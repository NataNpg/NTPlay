package com.natanael.ntplay.tv

import android.net.Uri
import android.util.Base64
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/** ⚽ Futebol: jogos do dia (placar da ESPN) + o canal da lista que está passando cada jogo (pela programação EPG). */
object Fut {
    class Game(
        val league: String, val leagueLogo: String,
        val home: String, val homeLogo: String, val homeScore: String,
        val away: String, val awayLogo: String, val awayScore: String,
        val state: String, val clock: String, val timeMs: Long, val venue: String
    )

    private val leagues = listOf(
        "bra.1" to "Brasileirão Série A", "bra.2" to "Brasileirão Série B", "bra.copa_do_brazil" to "Copa do Brasil",
        "conmebol.libertadores" to "Libertadores", "conmebol.sudamericana" to "Sul-Americana",
        "uefa.champions" to "Champions League", "uefa.europa" to "Europa League",
        "eng.1" to "Premier League", "esp.1" to "La Liga", "ita.1" to "Serie A (Itália)",
        "ger.1" to "Bundesliga", "fra.1" to "Ligue 1", "por.1" to "Liga Portugal",
        "fifa.world" to "Copa do Mundo", "fifa.worldq.conmebol" to "Eliminatórias", "usa.1" to "MLS"
    )
    val sportRe = Regex("esporte|sport|futebol|premiere|\\bespn|tnt sports|band ?sports|sportv|combate|caz[eé]|dazn|paramount|jogos|campeonato|brasileir|libertadores|champions|copa do|nosso futebol|conmebol|bein|fox sports|xsports|nsports", RegexOption.IGNORE_CASE)
    private val gameRe = Regex("\\s(x|vs\\.?|versus)\\s|futebol|campeonato|brasileir|libertadores|sul.?americana|champions|copa |liga |premier|la liga|serie a|bundesliga|ligue 1|eliminat|amistoso|\\bjogo\\b|rodada|\\bfinal\\b|semifinal|cl[aá]ssico", RegexOption.IGNORE_CASE)
    private val notRe = Regex("reprise|compacto|melhores momentos|gols da rodada|resenha|debate|programa|mesa redonda|jornal|not[ií]cias|tal(k|e) ?show|pr[eé].?jogo|p[oó]s.?jogo|replay", RegexOption.IGNORE_CASE)
    private val credRe = Regex("^(https?://[^/]+)/live/([^/]+)/([^/]+)/(\\d+)(?:\\.\\w+)?")

    private val isoFmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm'Z'", Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
    private val cache = HashMap<Int, Pair<Long, List<Game>>>()

    // programação atual por canal: baseKey(nome) -> título do programa que está no ar agora
    val epg = ConcurrentHashMap<String, String>()
    @Volatile var epgTs = 0L
    @Volatile var epgLoading = false

    fun day(off: Int): String = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date(System.currentTimeMillis() + off * 86400000L))
    fun baseKey(s: String): String = Util.norm(s).replace(Regex("\\b(fhd|hd|sd|uhd|4k|h265|hevc|fullhd|alt|backup|\\d{3,4}p)\\b"), " ").replace(Regex("\\s+"), " ").trim()
    private fun teamKey(s: String) = Util.norm(s).replace(Regex("\\b(fc|sc|ec|cf|ac|afc|de|da|do|futebol|esporte|sport)\\b"), " ").replace(Regex("\\s+"), " ").trim()
    private fun b64(s: String): String = try { String(Base64.decode(s, Base64.DEFAULT), Charsets.UTF_8) } catch (e: Exception) { s }

    suspend fun load(off: Int, force: Boolean): List<Game> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val c = cache[off]
        val live = c?.second?.any { it.state == "in" } == true
        if (!force && c != null && now - c.first < (if (live) 60000 else 600000)) return@withContext c.second
        val d = day(off)
        val out = ArrayList<Game>()
        val seen = HashSet<String>()
        for ((id, nm) in leagues) {
            try {
                val t = Net.getText("https://site.api.espn.com/apis/site/v2/sports/soccer/$id/scoreboard?dates=$d&lang=pt&region=br") ?: continue
                val j = JSONObject(t)
                val lgLogo = j.optJSONArray("leagues")?.optJSONObject(0)?.optJSONArray("logos")?.optJSONObject(0)?.optString("href") ?: ""
                val evs = j.optJSONArray("events") ?: continue
                for (i in 0 until evs.length()) {
                    val ev = evs.optJSONObject(i) ?: continue
                    val eid = ev.optString("id"); if (eid.isEmpty() || !seen.add(eid)) continue
                    val cp = ev.optJSONArray("competitions")?.optJSONObject(0) ?: continue
                    val stt = cp.optJSONObject("status") ?: ev.optJSONObject("status")
                    val ty = stt?.optJSONObject("type")
                    val state = ty?.optString("state") ?: "pre"
                    val clock = stt?.optString("displayClock") ?: ""
                    val comps = cp.optJSONArray("competitors") ?: continue
                    var hN = ""; var hL = ""; var hS = ""; var aN = ""; var aL = ""; var aS = ""
                    for (k in 0 until comps.length()) {
                        val cc = comps.optJSONObject(k) ?: continue
                        val team = cc.optJSONObject("team")
                        val nmT = team?.optString("shortDisplayName")?.ifEmpty { team.optString("displayName") } ?: ""
                        val lg = team?.optString("logo") ?: ""
                        val sc = cc.optString("score")
                        if (cc.optString("homeAway") == "home") { hN = nmT; hL = lg; hS = sc } else { aN = nmT; aL = lg; aS = sc }
                    }
                    if (hN.isEmpty() && aN.isEmpty()) continue
                    val tms = try { isoFmt.parse(ev.optString("date"))?.time ?: 0L } catch (e: Exception) { 0L }
                    val venue = cp.optJSONObject("venue")?.optString("fullName") ?: ""
                    out.add(Game(nm, lgLogo, hN, hL, hS, aN, aL, aS, state, clock, tms, venue))
                }
            } catch (e: Exception) { }
        }
        val sorted = out.sortedWith(compareBy({ if (it.state == "in") 0 else if (it.state == "pre") 1 else 2 }, { it.timeMs }))
        cache[off] = now to sorted
        sorted
    }

    /** lê o guia (get_short_epg) dos canais de esporte para saber o que cada um está passando agora */
    suspend fun loadEpg(sports: List<Item>, force: Boolean) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (!force && epgTs > 0 && now - epgTs < 5 * 60000) return@withContext
        if (epgLoading) return@withContext
        epgLoading = true
        try {
            val reps = LinkedHashMap<String, Item>()
            for (c in sports) { val k = baseKey(c.title); if (k.isNotEmpty() && !reps.containsKey(k)) reps[k] = c }
            val jobs = reps.entries.toList().take(140)
            val map = ConcurrentHashMap<String, String>()
            val sem = Semaphore(5)
            coroutineScope {
                for ((bk, ch) in jobs) launch {
                    sem.withPermit {
                        val m = credRe.find(ch.eps.firstOrNull()?.url ?: "") ?: return@withPermit
                        val base = m.groupValues[1]; val u = Net.enc(Uri.decode(m.groupValues[2])); val p = Net.enc(Uri.decode(m.groupValues[3])); val id = m.groupValues[4]
                        val t = Net.getQuick("$base/player_api.php?username=$u&password=$p&action=get_short_epg&stream_id=$id&limit=6") ?: return@withPermit
                        try {
                            val arr = JSONObject(t).optJSONArray("epg_listings") ?: return@withPermit
                            val ns = now / 1000
                            for (i in 0 until arr.length()) {
                                val o = arr.optJSONObject(i) ?: continue
                                val s0 = o.optString("start_timestamp").toLongOrNull() ?: 0
                                val s1 = o.optString("stop_timestamp").toLongOrNull() ?: 0
                                val nowP = o.optInt("now_playing", 0) == 1
                                if ((s0 <= ns && s1 > ns) || nowP) { map[bk] = (b64(o.optString("title")) + " " + b64(o.optString("description"))).trim(); break }
                            }
                        } catch (e: Exception) { }
                    }
                }
            }
            epg.clear(); epg.putAll(map); epgTs = now
        } finally { epgLoading = false }
    }

    fun status(g: Game): String = when {
        g.state == "in" -> "● AO VIVO" + (if (g.clock.isNotEmpty() && g.clock != "0'") " " + g.clock else "")
        g.state == "post" -> "Encerrado"
        g.timeMs > 0 -> SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(g.timeMs))
        else -> "A definir"
    }

    /** o canal está passando jogo agora? (programa atual com cara de jogo, sem ser reprise/debate) */
    fun onAir(ch: Item): String? { val p = epg[baseKey(ch.title)] ?: return null; return if (gameRe.containsMatchIn(p) && !notRe.containsMatchIn(p)) p else null }

    /** canais da lista passando ESTE jogo: pela programação (dois times no programa) ou canal de evento com os dois nomes */
    fun channelsFor(g: Game, sports: List<Item>): List<Item> {
        val h = teamKey(g.home); val a = teamKey(g.away)
        if (h.length < 3 && a.length < 3) return emptyList()
        fun has(s: String, k: String) = k.length >= 3 && (" $s ").contains(" $k ")
        val seen = HashSet<String>(); val out = ArrayList<Item>()
        // 1) programação com os dois times
        for (c in sports) { val p = epg[baseKey(c.title)] ?: continue; val pk = teamKey(p); if (has(pk, h) && has(pk, a)) { if (seen.add(baseKey(c.title))) out.add(c) } }
        // 2) canal de evento (nome com os dois times)
        for (c in sports) { val n = teamKey(c.title); if (has(n, h) && has(n, a)) { if (seen.add(baseKey(c.title))) out.add(c) } }
        return out.take(4)
    }
}

@Composable
fun FutebolScreen(st: AppState, lib: Library) {
    var off by remember { mutableIntStateOf(0) }
    var games by remember { mutableStateOf<List<Fut.Game>?>(null) }
    var epgVer by remember { mutableIntStateOf(0) }
    val sports = remember(lib) { lib.live.filter { Fut.sportRe.containsMatchIn(it.title + " " + it.group) } }
    LaunchedEffect(off) { games = try { Fut.load(off, false) } catch (e: Exception) { emptyList() } }
    LaunchedEffect(sports, off) { if (off == 0 && sports.isNotEmpty()) { Fut.loadEpg(sports, false); epgVer++ } }
    val airing = remember(epgVer, sports) { if (off == 0) sports.mapNotNull { c -> Fut.onAir(c)?.let { c to it } }.distinctBy { Fut.baseKey(it.first.title) } else emptyList() }
    val ls = rememberLazyListState()
    LazyColumn(Modifier.fillMaxSize().mouseScroll(ls), state = ls, contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 24.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(4.dp).height(20.dp).background(Pink, RoundedCornerShape(2.dp)))
                Spacer(Modifier.width(8.dp))
                Text("⚽ Jogos", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.width(14.dp))
                for ((o, lab) in listOf(-1 to "Ontem", 0 to "Hoje", 1 to "Amanhã")) {
                    val fi = rememberFocus()
                    Text(lab, fontSize = 12.sp, color = if (off == o || fi.on) Color.White else Color(0xCCFFFFFF), fontWeight = if (off == o) FontWeight.Bold else FontWeight.Medium,
                        modifier = Modifier.tvClick(fi, RoundedCornerShape(50)) { off = o }
                            .background(if (off == o) Pink else if (fi.on) FocusBg else Color.Transparent, RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 6.dp))
                    Spacer(Modifier.width(6.dp))
                }
            }
        }
        if (off == 0) {
            item {
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("● ", color = Color(0xFF34D399), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text("Passando jogo agora", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    Text(if (Fut.epgLoading && Fut.epgTs == 0L) "lendo a programação…" else "${airing.size} canais", color = Muted, fontSize = 11.sp)
                }
                Spacer(Modifier.height(6.dp))
                if (airing.isEmpty()) {
                    Text(if (Fut.epgLoading) "Verificando os canais…" else "Nenhum canal da sua lista está passando jogo neste momento.", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp))
                } else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 2.dp)) {
                        items(airing, key = { Fut.baseKey(it.first.title) }) { (ch, prog) ->
                            val fi = rememberFocus()
                            Column(Modifier.width(150.dp).tvClick(fi, RoundedCornerShape(10.dp)) { st.playItem(ch, null, sports) }
                                .background(if (fi.on) FocusBg else Color(0xFF17263F), RoundedCornerShape(10.dp)).padding(10.dp)) {
                                Text("📺 " + ch.title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("⚽ " + prog, color = Color(0xAA34D399), fontSize = 10.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
        val g = games
        if (g == null) {
            item { Text("Buscando os jogos na internet…", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(12.dp)) }
        } else if (g.isEmpty()) {
            item { Text("Nenhum jogo encontrado neste dia nos campeonatos acompanhados.", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(12.dp)) }
        } else {
            val byLeague = g.groupBy { it.league }
            for ((lg, list) in byLeague) {
                item(key = "lg_$lg") {
                    Row(Modifier.padding(top = 14.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (list.first().leagueLogo.isNotEmpty()) { AsyncImage(list.first().leagueLogo, null, Modifier.size(22.dp)); Spacer(Modifier.width(8.dp)) }
                        Text(lg, color = Cyan, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(8.dp)); Text("${list.size} jogo${if (list.size > 1) "s" else ""}", color = Muted, fontSize = 11.sp)
                    }
                }
                items(list, key = { "${lg}_${it.home}_${it.away}_${it.timeMs}" }) { game -> GameCard(st, game, sports, epgVer) }
            }
        }
    }
}

@Composable
private fun GameCard(st: AppState, g: Fut.Game, sports: List<Item>, epgVer: Int) {
    @Suppress("UNUSED_EXPRESSION") epgVer
    val chs = if (g.state == "post") emptyList() else Fut.channelsFor(g, sports)
    Column(Modifier.fillMaxWidth().padding(vertical = 5.dp).clip(RoundedCornerShape(12.dp))
        .background(if (g.state == "in") Color(0x332CC66B) else Color(0xFF141A28), RoundedCornerShape(12.dp)).padding(12.dp)) {
        Text(Fut.status(g), color = if (g.state == "in") Color(0xFF34D399) else Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        TeamRow(g.homeLogo, g.home, if (g.state == "pre") "" else g.homeScore)
        TeamRow(g.awayLogo, g.away, if (g.state == "pre") "" else g.awayScore)
        if (g.venue.isNotEmpty()) Text(g.venue, color = Color(0x77FFFFFF), fontSize = 10.sp, modifier = Modifier.padding(top = 3.dp))
        if (chs.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (ch in chs) {
                    val fi = rememberFocus()
                    Text("▶ " + ch.title, color = Color.White, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.tvClick(fi, RoundedCornerShape(8.dp)) { st.playItem(ch, null, sports) }
                            .background(if (fi.on) Pink else Color(0x55E93A7D), RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 6.dp))
                }
            }
        } else if (g.state == "in") {
            Spacer(Modifier.height(4.dp)); Text("Nenhum canal da sua lista está passando este jogo", color = Color(0x66FFFFFF), fontSize = 10.sp)
        }
    }
}

@Composable
private fun TeamRow(logo: String, name: String, score: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        if (logo.isNotEmpty()) { AsyncImage(logo, null, Modifier.size(22.dp)); Spacer(Modifier.width(8.dp)) } else Spacer(Modifier.width(30.dp))
        Text(name, color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (score.isNotEmpty()) Text(score, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
    }
}

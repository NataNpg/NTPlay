package com.natanael.ntplay.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
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
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** ⚽ Futebol: jogos do dia (placar da ESPN, sem chave) + canais de esporte da lista. */
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
    val sportRe = Regex("esporte|sport|futebol|premiere|\\bespn|tnt sports|band ?sports|sportv|combate|caz[eé]|dazn|paramount|\\bggoat|jogos|campeonato|brasileir|libertadores|champions|copa do|nosso futebol|conmebol|bein|fox sports|xsports|nsports", RegexOption.IGNORE_CASE)

    private val cache = HashMap<Int, Pair<Long, List<Game>>>()

    fun day(off: Int): String = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date(System.currentTimeMillis() + off * 86400000L))

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
                    val st = cp.optJSONObject("status") ?: ev.optJSONObject("status")
                    val ty = st?.optJSONObject("type")
                    val state = ty?.optString("state") ?: "pre"
                    val clock = st?.optString("displayClock") ?: ""
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

    private val isoFmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm'Z'", Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }

    fun status(g: Game): String = when {
        g.state == "in" -> "● AO VIVO" + (if (g.clock.isNotEmpty() && g.clock != "0'") " " + g.clock else "")
        g.state == "post" -> "Encerrado"
        g.timeMs > 0 -> SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(g.timeMs))
        else -> "A definir"
    }

    private fun key(s: String) = Util.norm(s).replace(Regex("\\b(fc|sc|ec|cf|ac|afc|de|da|do|futebol|esporte|sport)\\b"), " ").replace(Regex("\\s+"), " ").trim()

    /** canais de esporte da lista que citam os times do jogo (sem EPG, só pelo nome) */
    fun channelsFor(g: Game, sports: List<Item>): List<Item> {
        val h = key(g.home); val a = key(g.away)
        if (h.length < 3 && a.length < 3) return emptyList()
        return sports.filter {
            val n = " " + key(it.title) + " "
            (h.length >= 3 && n.contains(" $h ")) || (a.length >= 3 && n.contains(" $a "))
        }.take(4)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FutebolScreen(st: AppState, lib: Library) {
    var off by remember { mutableIntStateOf(0) }
    var games by remember { mutableStateOf<List<Fut.Game>?>(null) }
    var loading by remember { mutableStateOf(true) }
    val sports = remember(lib) { lib.live.filter { Fut.sportRe.containsMatchIn(it.title + " " + it.group) } }
    LaunchedEffect(off) {
        loading = true; games = null
        games = try { Fut.load(off, false) } catch (e: Exception) { emptyList() }
        loading = false
    }
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
            Spacer(Modifier.height(10.dp))
        }
        if (loading && games == null) {
            item { Text("Buscando os jogos na internet…", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(12.dp)) }
        } else if (games.isNullOrEmpty()) {
            item { Text("Nenhum jogo encontrado neste dia nos campeonatos acompanhados.", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(12.dp)) }
        } else {
            val byLeague = games!!.groupBy { it.league }
            for ((lg, list) in byLeague) {
                item(key = "lg_$lg") {
                    Row(Modifier.padding(top = 14.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (list.first().leagueLogo.isNotEmpty()) { AsyncImage(list.first().leagueLogo, null, Modifier.size(22.dp)); Spacer(Modifier.width(8.dp)) }
                        Text(lg, color = Cyan, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(8.dp)); Text("${list.size} jogo${if (list.size > 1) "s" else ""}", color = Muted, fontSize = 11.sp)
                    }
                }
                items(list, key = { "${lg}_${it.home}_${it.away}_${it.timeMs}" }) { g -> GameCard(st, g, sports) }
            }
        }
        if (sports.isNotEmpty()) {
            item {
                Spacer(Modifier.height(16.dp))
                Text("Canais de esporte da sua lista (${sports.size})", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (ch in sports.take(60)) {
                        val fi = rememberFocus()
                        Text("📺 " + ch.title, color = Color.White, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.tvClick(fi, RoundedCornerShape(8.dp)) { st.playItem(ch, null, sports) }
                                .background(if (fi.on) FocusBg else Color(0xFF17263F), RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 7.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun GameCard(st: AppState, g: Fut.Game, sports: List<Item>) {
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

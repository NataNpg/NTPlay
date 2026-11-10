package com.natanael.ntplay.tv

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PlayReq(val item: Item, val ep: Ep, val resume: Long)

val TABS = listOf("RECOMENDAÇÕES", "TV AO VIVO", "FUTEBOL", "FILMES", "SÉRIES", "ANIMES", "FAVORITOS")

class AppState(val ctx: Context) {
    val store = Store(ctx)
    init { Sync.dev = store.deviceId; Sync.devName = (android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL).trim() }
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    var lib by mutableStateOf<Library?>(null)
    var status by mutableStateOf("Nenhuma lista carregada")
    var busy by mutableStateOf(false)
    var tab by mutableStateOf(store.tab.let { if (it in TABS) it else TABS[0] })
    private var groupState by mutableStateOf("")
    /** categoria aberta; lembrada por aba (volta nela ao reabrir o app) */
    var group: String
        get() = groupState
        set(v) { groupState = v; store.setGroupFor(tab, v) }
    var detail by mutableStateOf<Item?>(null)
    var play by mutableStateOf<PlayReq?>(null)
    var lastErr by mutableStateOf("")
    var showLists by mutableStateOf(false)
    var showSearch by mutableStateOf(false)
    var groupDialog by mutableStateOf(false)
    var groupDialogItem: Item? = null
    fun openGroups(item: Item?) { groupDialogItem = item; groupDialog = true }
    var favs by mutableStateOf(store.favs)
    var adult by mutableStateOf(store.adult)
    var autoNext by mutableStateOf(store.autoNext)
    var subs by mutableStateOf(store.subs)
    var accVer by mutableIntStateOf(0)
    var season by mutableIntStateOf(1)
    var toastMsg by mutableStateOf("")
    var listsVer by mutableIntStateOf(0)
    var progVer by mutableIntStateOf(0)
    var epsVer by mutableIntStateOf(0)
    var epsLoading by mutableStateOf(false)
    var logoVer by mutableIntStateOf(0)
    var trend by mutableStateOf<List<Trending.Row>>(emptyList())
    var trendBusy by mutableStateOf(false)
    var epgVer by mutableIntStateOf(0)
    var yearSel by mutableStateOf("")
    /** ordem de Filmes/Séries: novos (lançamentos + recentes), recentes, lanc, az */
    var sortMode by mutableStateOf("novos")
    var yearDialog by mutableStateOf(false)
    var epsErr by mutableStateOf("")
    var queue: List<Item> = emptyList()
    val scroll = HashMap<String, Pair<Int, Int>>() // posição de rolagem por tela (volta onde parou)
    private val progMap: HashMap<String, Prog> = store.progress()

    fun toast(m: String) {
        toastMsg = m
        scope.launch { delay(4500); if (toastMsg == m) toastMsg = "" }
    }

    fun selectTab(t: String) {
        tab = t; groupState = store.groupFor(t); detail = null; store.tab = t; yearSel = ""
    }

    val canBack: Boolean get() = play != null || detail != null || group.isNotEmpty()

    fun back() {
        when {
            play != null -> closePlayer()
            detail != null -> detail = null
            group.isNotEmpty() -> group = ""
        }
    }

    fun start() {
        scope.launch { reload() }
        // sincronização na nuvem: ao abrir e a cada 90 s (só se a conta estiver ligada)
        scope.launch { delay(1500); while (true) { if (store.syncUser.isNotEmpty()) syncNow(false); delay(90_000L) } }
        // aviso de vencimento: uma vez por dia, quando faltam 7 dias ou menos
        scope.launch {
            delay(6000)
            val d = expiryDays(); val today = System.currentTimeMillis() / 86_400_000L
            if (d != null && !locked && store.warnDay != today) {
                store.warnDay = today
                toast(if (d == 0) "⚠ Seu acesso vence hoje. Fale com quem forneceu o acesso para renovar." else "⚠ Seu acesso vence em $d dia" + (if (d == 1) "" else "s") + ". Fale com quem forneceu o acesso para renovar.")
            }
        }
        autoUpdate()
        scope.launch { delay(90_000); while (true) { lib?.let { try { Epg.load(store, it); epgVer++ } catch (e: Throwable) { } }; delay(3 * 3600_000L) } }
        // atualização do próprio app: confere ao abrir e a cada 6 h
        scope.launch { delay(20_000); while (true) { checkUpdate(); delay(6 * 3600_000L) } }
        // presença para o painel do desenvolvedor: ao abrir e a cada 5 minutos
        scope.launch {
            delay(8000)
            while (true) {
                withContext(Dispatchers.IO) { Presence.send(ctx) }
                delay(5 * 60_000L)
            }
        }
    }

    suspend fun reload() {
        val metas = store.lists().filter { it.active }
        val files = metas.map { store.file(it.id) }.filter { it.exists() }
        if (files.isEmpty()) {
            lib = null
            status = "Nenhuma lista carregada"
            return
        }
        busy = true
        status = "Carregando lista…"
        try {
            val l = withContext(Dispatchers.Default) {
                val sig = LibCache.sig(files, adult, store.base64, store.adultByName, store.hiddenGroups)
                // lista igual à da última vez: lê o arquivo já processado (bem mais rápido que analisar o M3U)
                val cached = LibCache.load(ctx.filesDir, sig)
                val b = cached ?: Library.build(files, adult, store.base64, store.adultByName, store.hiddenGroups)
                if (cached == null) Thread { LibCache.save(ctx.filesDir, sig, b) }.also { it.priority = Thread.MIN_PRIORITY }.start()
                // pré-calcula agrupamentos fora da tela (evita travar ao trocar de aba)
                store.info("warm"); store.genres("warm") // lê os arquivos de capas/gêneros aqui, fora da tela
                b.every; b.byKey; b.liveGroups; b.movieGroups; b.serieGroups; b.kids; b.anime
                // pré-calcula ordenações e anos das abas (a troca de aba só escolhe o que já está pronto)
                b.sorted("m", b.movies, "novos"); b.sorted("s", b.series, "novos"); b.sorted("a", b.anime, "novos"); b.animeCR; b.animeFN
                b.years("FILMES|", b.movies) { yearOfItem(this@AppState, it) }; b.years("SÉRIES|", b.series) { yearOfItem(this@AppState, it) }; b.years("ANIMES|", b.anime) { yearOfItem(this@AppState, it) }
                b
            }
            lib = l
            // volta na categoria em que você estava, se ela ainda existe
            groupState = store.groupFor(tab).takeIf { g -> g.isNotEmpty() && (l.liveGroups + l.movieGroups + l.serieGroups).any { it.first == g } } ?: ""
            status = "${l.live.size} canais • ${l.movies.size} filmes • ${l.series.size} séries"
            logosLater()
            scope.launch { try { Epg.load(store, l, true); epgVer++ } catch (e: Throwable) { } }
            trendBusy = trend.isEmpty()
            scope.launch {
                try {
                    trend = withContext(Dispatchers.IO) { Trending.load(ctx, l) }
                    // pré-carrega capas dos primeiros itens para a tela inicial já abrir pronta (máx. ~6 s)
                    kotlinx.coroutines.withTimeoutOrNull(2500) {
                        val first = trend.flatMap { it.items.take(8) }.filter { it.kind != 'l' }.distinctBy { it.key }.take(24)
                        withContext(Dispatchers.IO) {
                            val sem = kotlinx.coroutines.sync.Semaphore(6)
                            first.map { it0 -> scope.launch(Dispatchers.IO) { sem.acquire(); try {
                                var img = it0.logo.ifEmpty { Meta.cached(store, it0)?.img ?: "" }
                                if (img.isEmpty()) img = try { Meta.get(store, it0)?.img ?: "" } catch (e: Throwable) { "" }
                                if (img.isNotEmpty()) coil.Coil.imageLoader(ctx).execute(coil.request.ImageRequest.Builder(ctx).data(img).build())
                            } catch (e: Throwable) { } finally { sem.release() } } }.forEach { it1 -> it1.join() }
                        }
                    }
                } catch (e: Throwable) { }
                trendBusy = false
            }
        } catch (e: Throwable) {
            status = "Erro ao ler a lista"
            toast("Erro ao ler a lista: " + Util.friendlyErr(e))
        }
        busy = false
        listsVer++
        syncSoon()
    }

    private fun countItems(f: java.io.File): Int {
        var n = 0
        f.bufferedReader().use { r -> while (true) { val l = r.readLine() ?: break; if (l.startsWith("#EXTINF")) n++ } }
        return n
    }

    fun addUrl(url: String, name: String?) {
        var u = url.trim().replace(" ", "").replace("\n", "")
        if (u.isEmpty()) { lastErr = "Informe o link da lista M3U."; toast(lastErr); return }
        if (!u.contains("://")) u = "http://$u"
        lastErr = ""
        scope.launch {
            busy = true
            status = "Baixando lista…"
            try {
                val id = java.lang.Long.toString(System.currentTimeMillis(), 36)
                val n = withContext(Dispatchers.IO) {
                    val f = store.file(id)
                    val cr = Xtream.creds(u)
                    var viaApi = false
                    var apiErr = ""
                    if (cr != null) {
                        try { Xtream.buildM3u(cr, f) { m -> status = m }; viaApi = true }
                        catch (x: Throwable) { if (x.message?.contains("recusados") == true || x.message?.startsWith("conta ") == true) throw x; apiErr = x.message ?: x.javaClass.simpleName }
                    }
                    if (!viaApi) try {
                        Net.downloadAny(u, f) { b -> if (b % (1 shl 20) < 70000) status = "Baixando… ${b / 1048576} MB" }
                    } catch (x: Throwable) {
                        if (apiErr.isNotEmpty()) throw RuntimeException("API: $apiErr | M3U: " + (x.message ?: x.javaClass.simpleName))
                        throw x
                    }
                    val c = countItems(f)
                    if (c == 0) {
                        val head = try { f.inputStream().use { i -> val b = ByteArray(120); val k = i.read(b); if (k > 0) String(b, 0, k, Charsets.UTF_8) else "(vazia)" } } catch (x: Throwable) { "" }
                        f.delete()
                        throw RuntimeException("o link respondeu, mas não é uma lista M3U (resposta: " + head.replace("\n", " ").trim() + ")")
                    }
                    c
                }
                val l = store.lists()
                l.add(ListMeta(id, name?.ifBlank { null } ?: (u.substringAfter("://").substringBefore('/').ifEmpty { "Lista" }), u, n, System.currentTimeMillis(), true))
                store.saveLists(l)
                toast("Lista adicionada: $n itens.")
                reload()
            } catch (e: Throwable) {
                busy = false
                status = "Falha ao baixar a lista"
                val why = when (e) {
                    is java.net.UnknownHostException -> "não achei o servidor (sem internet/DNS?): " + e.message
                    is java.net.SocketTimeoutException -> "o servidor demorou demais para responder"
                    is javax.net.ssl.SSLException -> "erro de conexão segura (https). Confira a data/hora da TV. " + e.message
                    is OutOfMemoryError -> "lista grande demais para a memória da TV"
                    else -> (e.message ?: e.javaClass.simpleName).let { m ->
                        if (Regex("HTTP 5(2[0-9]|0[234])").containsMatchIn(m)) "$m — o servidor da sua lista não respondeu (fora do ar ou sobrecarregado). Não é erro do app: tente de novo mais tarde ou peça ao fornecedor um link novo."
                        else m
                    }
                }
                lastErr = "Falha ao baixar a lista: $why"
                toast(lastErr)
            }
        }
    }

    fun addText(text: String, name: String?) {
        scope.launch {
            busy = true
            try {
                val id = java.lang.Long.toString(System.currentTimeMillis(), 36)
                val n = withContext(Dispatchers.IO) { val f = store.file(id); f.writeText(text); countItems(f) }
                val l = store.lists()
                l.add(ListMeta(id, name ?: "Lista do celular", "", n, System.currentTimeMillis(), true))
                store.saveLists(l)
                toast("Lista recebida: $n itens.")
                reload()
            } catch (e: Throwable) { busy = false; toast("Lista inválida.") }
        }
    }

    fun onRelay(content: String, name: String?) {
        scope.launch {
            toast("Lista recebida do celular!")
            if (content.startsWith("http", true)) addUrl(content, name) else addText(content, name)
        }
    }

    fun toggleList(id: String) {
        val l = store.lists(); l.firstOrNull { it.id == id }?.let { it.active = !it.active }
        store.saveLists(l); scope.launch { reload() }
    }

    fun deleteList(id: String) {
        val l = store.lists(); l.removeAll { it.id == id }
        store.saveLists(l); store.deleteList(id); scope.launch { reload() }
    }

    fun refreshList(m: ListMeta) {
        if (m.url.isEmpty()) { toast("Lista enviada como texto: adicione de novo para atualizar."); return }
        scope.launch {
            busy = true; status = "Atualizando lista…"
            try {
                val n = withContext(Dispatchers.IO) {
                    val f = store.file(m.id)
                    val cr = Xtream.creds(m.url)
                    var ok = false
                    if (cr != null) try { Xtream.buildM3u(cr, f) { s -> status = s }; ok = true } catch (x: Throwable) { }
                    if (!ok) Net.downloadAny(m.url, f)
                    countItems(f)
                }
                val l = store.lists(); l.firstOrNull { it.id == m.id }?.let { it.count = n; it.date = System.currentTimeMillis() }
                store.saveLists(l); reload()
            } catch (e: Throwable) { busy = false; toast("Falha ao atualizar: " + Util.friendlyErr(e)) }
        }
    }

    // ---- gêneros: confere a categoria com o gênero do TMDB (só quando o título já tem gênero conhecido)
    var genresVer by mutableIntStateOf(0)
    private var genreJob: Job? = null
    private fun genreIdsFor(group: String): Set<Int> {
        val tk = Util.norm(group).split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }
        val out = HashSet<Int>()
        for (w in tk) when (w) {
            "comedia", "comedy", "humor" -> out += setOf(35)
            "acao", "action" -> out += setOf(28, 10759)
            "aventura", "adventure" -> out += setOf(12, 10759)
            "drama" -> out += setOf(18)
            "terror", "horror" -> out += setOf(27)
            "suspense", "thriller" -> out += setOf(53, 9648, 80)
            "romance" -> out += setOf(10749)
            "ficcao", "scifi", "sci" -> out += setOf(878, 10765)
            "documentario", "documentarios", "documentary" -> out += setOf(99)
            "crime", "policial" -> out += setOf(80)
            "fantasia", "fantasy" -> out += setOf(14, 10765)
            "guerra", "war" -> out += setOf(10752, 10768)
            "faroeste", "western" -> out += setOf(37)
            "misterio", "mystery" -> out += setOf(9648)
        }
        return out
    }
    /** tira da categoria (ex.: "Comédia") o título que o TMDB classifica em outro gênero. Sem gênero conhecido, o título fica. */
    fun genreFilter(group: String, l: List<Item>): List<Item> {
        val ok = genreIdsFor(group); if (ok.isEmpty()) return l
        return l.filter { val g = store.genres(it.key); g == null || g.isEmpty() || g.any { x -> x in ok } }
    }
    fun enrichGenres(group: String, l: List<Item>) {
        genreJob?.cancel()
        if (group.isEmpty() || genreIdsFor(group).isEmpty()) return
        genreJob = scope.launch(Dispatchers.IO) {
            var n = 0; var done = 0
            for (chunk in l.filter { it.kind != 'l' && store.genres(it.key) == null && !store.genreTriedRecently(it.key) }.take(200).chunked(8)) {
                val r = chunk.map { x -> async { Meta.genreIds(store, x) } }.map { it.await() }
                n += r.count { it }; done += chunk.size
                if (n >= 8) { genresVer++; n = 0 }
            }
            if (n > 0) genresVer++
        }
    }
    var hiddenCount by mutableIntStateOf(store.hiddenGroups.size)
    fun hideGroup(name: String) {
        store.hiddenGroups = store.hiddenGroups + name; hiddenCount = store.hiddenGroups.size
        group = ""; toast("Categoria \"$name\" ocultada. Para mostrar de novo: Listas → Categorias ocultas.")
        scope.launch { reload() }
    }
    fun showAllGroups() { store.hiddenGroups = emptySet(); hiddenCount = 0; toast("Categorias ocultas voltaram."); scope.launch { reload() } }
    fun changeAdult(v: Boolean) { adult = v; store.adult = v; scope.launch { reload() } }
    fun changeAutoNext(v: Boolean) { autoNext = v; store.autoNext = v }
    fun changeSubs(v: Boolean) { subs = v; store.subs = v }
    var bufferMode by mutableIntStateOf(store.bufferMode)
    fun changeBuffer(v: Int) { bufferMode = v; store.bufferMode = v }
    var base64 by mutableStateOf(store.base64)
    fun changeBase64(v: Boolean) { base64 = v; store.base64 = v; scope.launch { reload() } }
    var adultByName by mutableStateOf(store.adultByName)
    fun changeAdultByName(v: Boolean) { adultByName = v; store.adultByName = v; scope.launch { reload() } }

    // ---------- grupos personalizados ----------
    var groupsVer by mutableIntStateOf(0)
    private fun readGroups(): LinkedHashMap<String, MutableList<String>> {
        val m = LinkedHashMap<String, MutableList<String>>()
        try { val o = JSONObject(store.groupsRaw); val it = o.keys(); while (it.hasNext()) { val k = it.next(); val a = o.getJSONArray(k); val l = ArrayList<String>(); for (i in 0 until a.length()) l.add(a.getString(i)); m[k] = l } } catch (e: Exception) { }
        return m
    }
    private fun writeGroups(m: Map<String, List<String>>) {
        val o = JSONObject(); for ((k, v) in m) o.put(k, JSONArray(v)); store.groupsRaw = o.toString(); groupsVer++; syncSoon()
    }
    fun groupNames(): List<String> { @Suppress("UNUSED_EXPRESSION") groupsVer; return readGroups().keys.toList() }
    fun groupCount(name: String): Int = readGroups()[name]?.size ?: 0
    fun groupItems(name: String): List<Item> { val lb = lib ?: return emptyList(); val keys = readGroups()[name] ?: return emptyList(); return keys.mapNotNull { lb.byKey[it] } }
    fun createGroup(name: String) { val n = name.trim().take(40); if (n.isEmpty()) return; val m = readGroups(); if (!m.containsKey(n)) { m[n] = ArrayList(); writeGroups(m); toast("Grupo \"$n\" criado") } }
    fun deleteGroup(name: String) { val m = readGroups(); if (m.remove(name) != null) { writeGroups(m); toast("Grupo removido") } }
    fun inGroup(name: String, key: String): Boolean = readGroups()[name]?.contains(key) == true
    fun toggleInGroup(name: String, key: String) { val m = readGroups(); val l = m.getOrPut(name) { ArrayList() }; val added = if (l.contains(key)) { l.remove(key); false } else { l.add(key); true }; writeGroups(m); toast(if (added) "Adicionado ao grupo \"$name\"" else "Removido do grupo") }

    // ---------- config de playlist ----------
    var updateHours by mutableIntStateOf(store.updateHours)
    fun changeUpdateHours(v: Int) { updateHours = v; store.updateHours = v }
    var liteMode by mutableStateOf(store.liteMode)
    fun changeLite(v: Int) { liteMode = v; store.liteMode = v; toast(if (lite) "Modo leve ligado" else "Modo leve desligado") }
    /** aparelho com pouca memória (usado no modo automático) */
    val lowRam: Boolean = try {
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        am.isLowRamDevice || am.memoryClass <= 192
    } catch (e: Throwable) { false }
    val lite: Boolean get() = liteMode == 1 || (liteMode == 0 && lowRam)
    var updateOnStart by mutableStateOf(store.updateOnStart)
    fun changeUpdateOnStart(v: Boolean) { updateOnStart = v; store.updateOnStart = v }

    /** baixa para um arquivo temporário; se for igual ao que já está salvo, não troca nada (e o cache da lista continua valendo). true = a lista mudou */
    private suspend fun syncList(m: ListMeta): Boolean = withContext(Dispatchers.IO) {
        val f = store.file(m.id); val cr = Xtream.creds(m.url)
        val tmp = java.io.File(f.parentFile, f.name + ".new")
        try {
            if (cr != null) Xtream.buildM3u(cr, tmp) { } else Net.downloadAny(m.url, tmp)
            val same = f.exists() && f.length() == tmp.length() && crcOf(f) == crcOf(tmp)
            val l = store.lists()
            if (same) { tmp.delete(); l.firstOrNull { it.id == m.id }?.let { it.date = System.currentTimeMillis() } }
            else { f.delete(); tmp.renameTo(f); l.firstOrNull { it.id == m.id }?.let { it.count = countItems(f); it.date = System.currentTimeMillis() } }
            store.saveLists(l)
            !same
        } catch (e: Throwable) { try { tmp.delete() } catch (x: Throwable) { }; false }
    }
    private fun crcOf(f: java.io.File): Long {
        val c = java.util.zip.CRC32(); val buf = ByteArray(1 shl 16)
        java.io.FileInputStream(f).use { i -> while (true) { val n = i.read(buf); if (n <= 0) break; c.update(buf, 0, n) } }
        return c.value
    }
    private fun autoUpdate() {
        if (!store.updateOnStart && store.updateHours <= 0) return
        scope.launch {
            delay(12000)
            val now = System.currentTimeMillis()
            // ao abrir: só atualiza se a lista tem mais de 3 horas (abrir o app várias vezes não baixa tudo de novo)
            val due = store.lists().filter { it.active && it.url.isNotEmpty() && (now - it.date > (if (store.updateOnStart) 3 * 3600_000L else store.updateHours * 3600_000L)) }
            if (due.isEmpty()) return@launch
            var changed = false
            for (m in due) try { if (syncList(m)) changed = true } catch (e: Throwable) { }
            if (changed) reload()
        }
    }

    /** atualiza vencimento das listas Xtream (em segundo plano) */
    fun refreshAccounts() {
        scope.launch {
            for (m in store.lists()) {
                if (m.url.isEmpty() || Xtream.creds(m.url) == null) continue
                val acc = withContext(Dispatchers.IO) { try { Xtream.account(m.url) } catch (e: Throwable) { null } } ?: continue
                store.setAccount(m.id, "${acc.exp}|${acc.status}|${acc.maxCon}|${acc.active}|${System.currentTimeMillis()}")
                accVer++
            }
        }
    }

    fun accountText(id: String): String {
        val p = store.account(id).split('|')
        if (p.size < 5) return ""
        val exp = p[0].toLongOrNull() ?: 0L
        val st = p[1]
        val venc = if (exp <= 0) "Sem data de vencimento" else {
            val d = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault()).format(java.util.Date(exp))
            val dias = ((exp - System.currentTimeMillis()) / 86_400_000L)
            if (dias < 0) "⚠ Venceu em $d" else "Vence em $d ($dias dia" + (if (dias == 1L) ")" else "s)")
        }
        val stTxt = when { st.equals("Active", true) -> "Ativa"; st.isEmpty() -> ""; else -> st }
        return listOf(venc, stTxt, if ((p[2].toIntOrNull() ?: 0) > 0) "${p[3]}/${p[2]} telas" else "").filter { it.isNotEmpty() }.joinToString(" · ")
    }

    fun toggleFav(key: String) { toggleFav0(key); syncSoon() }
    private fun toggleFav0(key: String) {
        val s = HashSet(favs); val added = !s.remove(key); if (added) s.add(key)
        favs = s; store.favs = s
        toast(if (added) "★ Adicionado à Minha lista" else "Removido da Minha lista")
    }

    // ---------- progresso ----------
    fun progOf(ep: Ep): Prog? = progMap[ep.url]

    fun saveProg(r: PlayReq, pos: Long, dur: Long) {
        if (r.item.kind == 'l' || pos < 1000) return
        val d = if (dur > 0) dur else 0
        progMap[r.ep.url] = Prog(pos, d, System.currentTimeMillis(), d > 0 && pos > d * 0.95, r.item.key, r.item.title)
        store.saveProgress(progMap)
    }

    fun markDone(r: PlayReq) {
        val old = progMap[r.ep.url]
        progMap[r.ep.url] = Prog(0, old?.d ?: 0, System.currentTimeMillis(), true, r.item.key, r.item.title)
        store.saveProgress(progMap)
    }

    fun removeProg(key: String) {
        progMap.entries.removeAll { it.value.k == key }
        store.saveProgress(progMap); progVer++
        toast("Removido de Continuar assistindo.")
    }

    fun continueList(): List<Pair<Item, Ep>> {
        val l = lib ?: return emptyList()
        val seen = HashSet<String>()
        val out = ArrayList<Pair<Item, Ep>>()
        // o mais recente de cada título; série cujo último episódio terminou volta com o PRÓXIMO episódio
        for (p in progMap.entries.sortedByDescending { it.value.ts }) {
            val it = l.byKey[p.value.k] ?: continue
            if (!seen.add(it.key)) continue
            val ep = it.eps.firstOrNull { e -> e.url == p.key } ?: (if (needsEps(it)) it.eps[0] else null) ?: continue
            if (!p.value.done) { out.add(it to ep) }
            else if (it.kind == 's' && !needsEps(it)) {
                val i = it.eps.indexOf(ep)
                if (i >= 0 && i < it.eps.size - 1) out.add(it to it.eps[i + 1])
            }
            if (out.size >= 24) break
        }
        return out
    }

    fun startEp(it: Item): Ep {
        if (it.kind != 's') return it.eps[0]
        var best: Ep? = null; var ts = 0L; var done = false
        for (e in it.eps) { val p = progMap[e.url] ?: continue; if (p.ts > ts) { ts = p.ts; best = e; done = p.done } }
        if (best == null) return it.eps[0]
        if (!done) return best
        val i = it.eps.indexOf(best)
        return if (i >= 0 && i < it.eps.size - 1) it.eps[i + 1] else best
    }

    fun resumeOf(ep: Ep): Long { val p = progMap[ep.url]; return if (p != null && !p.done && p.t > 10000) p.t else 0 }

    fun needsEps(it: Item) = it.kind == 's' && it.eps.size == 1 && it.eps[0].url.endsWith(Xtream.SERIES_EXT)

    fun openDetail(it: Item) {
        detail = it
        season = if (it.kind == 's') startEp(it).s else 1
        if (needsEps(it)) loadEps(it)
    }

    fun loadEps(it: Item) {
        if (epsLoading) return
        epsLoading = true; epsErr = ""
        scope.launch {
            try {
                val l = withContext(Dispatchers.IO) { Xtream.episodes(it.eps[0].url) }
                if (l.isNotEmpty()) {
                    it.eps.clear(); it.eps.addAll(l); it.ns = l.maxOf { e -> e.s }
                    if (detail === it) season = startEp(it).s
                    epsVer++
                } else epsErr = "Esta série não tem episódios no servidor."
            } catch (e: Throwable) { epsErr = "Não consegui carregar os episódios. " + Util.friendlyErr(e) }
            epsLoading = false
        }
    }

    fun playItem(it: Item, ep: Ep? = null, q: List<Item>? = null) {
        if (needsEps(it)) { openDetail(it); return }
        val e = ep ?: startEp(it)
        if (it.kind == 's' && !needsEps(it)) store.setEpSeen(it.key, it.eps.size)
        if (q != null) queue = q
        play = PlayReq(it, e, if (it.kind == 'l') 0 else resumeOf(e))
    }

    fun closePlayer() { play = null; progVer++ }

    fun onEnded(r: PlayReq) {
        if (r.item.kind == 'l') return
        markDone(r)
        if (autoNext) {
            if (r.item.kind == 's') {
                val i = r.item.eps.indexOf(r.ep)
                if (i >= 0 && i < r.item.eps.size - 1) {
                    val n = r.item.eps[i + 1]
                    toast("Próximo: " + r.item.title + " • T${n.s} E${n.e}")
                    play = PlayReq(r.item, n, 0); return
                }
            }
            val q = queue.filter { it.kind == r.item.kind }
            val i = q.indexOfFirst { it.key == r.item.key }
            if (i >= 0 && i < q.size - 1) {
                val n = q[i + 1]
                toast("Próximo: " + n.title)
                play = PlayReq(n, startEp(n), resumeOf(startEp(n))); return
            }
            toast("Fim da lista.")
        }
        closePlayer()
    }

    fun next(r: PlayReq): PlayReq? {
        if (r.item.kind == 's') { val i = r.item.eps.indexOf(r.ep); if (i >= 0 && i < r.item.eps.size - 1) return PlayReq(r.item, r.item.eps[i + 1], 0) }
        val q = queue.filter { it.kind == r.item.kind }; val i = q.indexOfFirst { it.key == r.item.key }
        return if (i >= 0 && i < q.size - 1) PlayReq(q[i + 1], startEp(q[i + 1]), 0) else null
    }

    fun prev(r: PlayReq): PlayReq? {
        if (r.item.kind == 's') { val i = r.item.eps.indexOf(r.ep); if (i > 0) return PlayReq(r.item, r.item.eps[i - 1], 0) }
        val q = queue.filter { it.kind == r.item.kind }; val i = q.indexOfFirst { it.key == r.item.key }
        return if (i > 0) PlayReq(q[i - 1], startEp(q[i - 1]), 0) else null
    }

    suspend fun search(q: String): List<Item> {
        val l = lib ?: return emptyList()
        val n = Util.norm(q)
        if (n.length < 2) return emptyList()
        return withContext(Dispatchers.Default) {
            val first = ArrayList<Item>(); val rest = ArrayList<Item>()
            // só filmes e séries (os animes estão dentro desses dois); canais ao vivo ficam de fora
            for (it in l.movies.asSequence() + l.series.asSequence()) {
                // as duas listas cheias: nada mais entra, então para de varrer
                // (evita normalizar o nome da biblioteca inteira à toa)
                if (first.size >= 150 && rest.size >= 150) break
                val t = it.norm
                if (t.startsWith(n)) { first.add(it); if (first.size >= 150) break }
                else if (rest.size < 150) {
                    val g = synchronized(l.gnorm) { l.gnorm.getOrPut(it.group) { Util.norm(it.group) } }
                    if (t.contains(n) || g.contains(n)) rest.add(it)
                }
            }
            // poucos resultados: tenta de novo tolerando 1 letra errada (ex.: "vingadors")
            val fuzzy = ArrayList<Item>()
            if (first.size + rest.size < 12) {
                val qw = n.split(' ').filter { it.length >= 3 }
                if (qw.isNotEmpty()) for (it in l.movies.asSequence() + l.series.asSequence()) {
                    val tw = it.norm.split(' ')
                    if (qw.all { w -> tw.any { x -> near(x, w) } }) { fuzzy.add(it); if (fuzzy.size >= 60) break }
                }
            }
            // canais ao vivo que combinam com o nome (depois de filmes e séries)
            val ch = ArrayList<Item>()
            for (it in l.live) { if (it.norm.contains(n)) { ch.add(it); if (ch.size >= 40) break } }
            // última barreira: chave repetida na grade do Compose fecha o app
            (first + rest + fuzzy + ch).distinctBy { it.key }.take(240)
        }
    }

    /** palavras iguais ou com 1 letra de diferença (só para palavras de 4+ letras) */
    private fun near(a: String, b: String): Boolean {
        if (a == b) return true
        if (a.length < 4 || b.length < 4 || Math.abs(a.length - b.length) > 1) return false
        var i = 0; var j = 0; var d = 0
        while (i < a.length && j < b.length) {
            if (a[i] == b[j]) { i++; j++; continue }
            if (++d > 1) return false
            if (a.length > b.length) i++ else if (b.length > a.length) j++ else { i++; j++ }
        }
        return d + (a.length - i) + (b.length - j) <= 1
    }

    fun logosLater() {
        val l = lib ?: return
        if (l.live.none { it.logo.isEmpty() }) return
        scope.launch {
            val ok = withContext(Dispatchers.IO) { Logos.ensure(ctx) }
            if (!ok) return@launch
            var n = 0
            withContext(Dispatchers.Default) { for (o in l.live) if (o.logo.isEmpty()) Logos.find(o.title)?.let { o.logo = it; n++ } }
            if (n > 0) logoVer++
        }
    }

    // ------------------------------------------------------------ conta + sincronização na nuvem
    var syncUser by mutableStateOf(store.syncUser)
    var locked by mutableStateOf(store.syncUser.isEmpty() || (store.accessExp > 0 && System.currentTimeMillis() > store.accessExp))
    var accessMsg by mutableStateOf(if (store.syncUser.isNotEmpty() && store.accessExp > 0 && System.currentTimeMillis() > store.accessExp) "Seu acesso expirou. Conecte-se à internet e entre de novo ou peça a renovação." else "")
    /** dias que faltam para o acesso vencer (só quando faltam 7 dias ou menos) */
    fun expiryDays(): Int? {
        val e = store.accessExp
        if (e <= 0) return null
        val d = Math.ceil((e - System.currentTimeMillis()) / 86_400_000.0).toInt()
        return if (d in 0..7) d else null
    }
    var searchHist by mutableStateOf(store.searchHist)
    fun rememberSearch(q: String) {
        val t = q.trim(); if (t.length < 2) return
        val l = listOf(t) + searchHist.filter { !it.equals(t, true) }
        searchHist = l.take(8); store.searchHist = searchHist
    }
    fun accessText(): String = if (store.accessExp > 0) "Válido até " + java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale("pt", "BR")).format(java.util.Date(store.accessExp)) else ""

    private fun lockOut(msg: String) {
        store.syncUser = ""; store.syncAuth = ""; store.syncEnc = ""; store.syncTs = 0; store.syncHash = ""
        syncUser = ""; accessMsg = msg; locked = true; play = null; accountDialog = false
    }
    /** true = a falha foi de acesso (conta removida/senha trocada/expirada) e o app foi bloqueado */
    private fun accessFailed(r: Sync.Res): Boolean {
        if (r.ok) { if (r.exp > 0) store.accessExp = r.exp; return false }
        if (r.code == 401 || r.code == 403) { lockOut(r.msg); return true }
        if (r.code == 0 && store.accessExp > 0 && System.currentTimeMillis() > store.accessExp) { lockOut("Seu acesso expirou. Conecte-se à internet e entre de novo ou peça a renovação."); return true }
        return false
    }
    private suspend fun wipeLocal() {
        for (m in store.lists()) store.deleteList(m.id)
        store.saveLists(emptyList())
        store.favs = emptySet(); favs = emptySet()
        store.groupsRaw = "{}"; groupsVer++
        progMap.clear(); store.saveProgress(progMap); progVer++
        reload()
    }
    var syncMsg by mutableStateOf("")
    var syncBusy by mutableStateOf(false)
    var accountDialog by mutableStateOf(false)
    private var syncJob: kotlinx.coroutines.Job? = null
    @Volatile private var blockPush = false

    fun syncSoon() {
        if (store.syncUser.isEmpty()) return
        syncJob?.cancel()
        syncJob = scope.launch { delay(10_000); syncNow(false) }
    }

    /** conteúdo sincronizado: listas com link, favoritos, grupos e progresso (progresso fora da detecção de mudança) */
    private fun payload(withTs: Long): JSONObject {
        val o = JSONObject()
        val la = JSONArray()
        for (m in store.lists().filter { it.url.isNotEmpty() }.sortedBy { it.url }) la.put(JSONObject().put("name", m.name).put("url", m.url).put("active", m.active))
        o.put("lists", la)
        o.put("favs", JSONArray(store.favs.sorted()))
        o.put("groups", JSONObject(store.groupsRaw.ifEmpty { "{}" }))
        val pr = JSONObject()
        for ((u, p) in progMap.entries.sortedByDescending { it.value.ts }.take(150)) pr.put(u, JSONObject().put("t", p.t).put("d", p.d).put("ts", p.ts).put("done", p.done).put("k", p.k).put("n", p.title))
        o.put("prog", pr)
        o.put("ts", withTs)
        return o
    }
    private fun hashOf(o: JSONObject): String {
        val g = o.getJSONObject("groups"); val keys = g.keys().asSequence().sorted().toList()
        val sb = StringBuilder()
        sb.append(o.getJSONArray("lists").toString()).append('|').append(o.getJSONArray("favs").toString()).append('|')
        for (k in keys) sb.append(k).append('=').append(g.getJSONArray(k).toString()).append(';')
        return Sync.sha(sb.toString())
    }

    private suspend fun applyRemote(r: JSONObject, merge: Boolean) {
        // favoritos
        val rf = HashSet<String>(); r.optJSONArray("favs")?.let { for (i in 0 until it.length()) rf.add(it.getString(i)) }
        val nf = if (merge) HashSet(store.favs).also { it.addAll(rf) } else rf
        store.favs = nf; favs = nf
        // grupos
        val rg = r.optJSONObject("groups") ?: JSONObject()
        if (merge) {
            val m = readGroups()
            for (k in rg.keys()) { val l = m.getOrPut(k) { ArrayList() }; val a = rg.getJSONArray(k); for (i in 0 until a.length()) if (!l.contains(a.getString(i))) l.add(a.getString(i)) }
            val o = JSONObject(); for ((k, v) in m) o.put(k, JSONArray(v)); store.groupsRaw = o.toString()
        } else store.groupsRaw = rg.toString()
        groupsVer++
        // progresso (mantém o mais recente)
        r.optJSONObject("prog")?.let { pr ->
            for (u in pr.keys()) { val p = pr.getJSONObject(u); val ts = p.optLong("ts"); val old = progMap[u]
                if (old == null || ts > old.ts) progMap[u] = Prog(p.optLong("t"), p.optLong("d"), ts, p.optBoolean("done"), p.optString("k"), p.optString("n")) }
            store.saveProgress(progMap); progVer++
        }
        // listas
        val rl = r.optJSONArray("lists")
        val remoteUrls = HashSet<String>()
        if (rl != null) {
            for (i in 0 until rl.length()) {
                val x = rl.getJSONObject(i); val url = x.getString("url"); remoteUrls.add(url)
                val have = store.lists().firstOrNull { it.url == url }
                if (have == null) {
                    addUrl(url, x.optString("name"))
                    var w = 0
                    while (w < 450 && store.lists().none { it.url == url }) { delay(200); w++ ; if (!busy && w > 15) break }
                    if (store.lists().none { it.url == url }) blockPush = true
                } else if (have.active != x.optBoolean("active", true)) {
                    val l = store.lists(); l.firstOrNull { it.id == have.id }?.active = x.optBoolean("active", true); store.saveLists(l)
                    reload()
                }
            }
            if (!merge) {
                val l = store.lists(); val rm = l.filter { it.url.isNotEmpty() && it.url !in remoteUrls }
                if (rm.isNotEmpty()) { l.removeAll(rm); store.saveLists(l); for (m in rm) store.deleteList(m.id); reload() }
            }
        }
        listsVer++
    }

    suspend fun syncNow(manual: Boolean) {
        val u = store.syncUser; if (u.isEmpty() || syncBusy) return
        syncBusy = true; blockPush = false
        try {
            val ak = store.syncAuth; val ek = store.syncEnc
            val g = withContext(Dispatchers.IO) { Sync.call("get", u, ak) }
            if (accessFailed(g)) return
            if (!g.ok) { syncMsg = g.msg; return }
            val remote = if (g.data.isEmpty()) null else withContext(Dispatchers.Default) { Sync.decrypt(g.data, ek) }?.let { try { JSONObject(it) } catch (e: Exception) { null } }
            if (g.data.isNotEmpty() && remote == null) { syncMsg = "Não consegui abrir os dados da nuvem (senha diferente da usada ao salvar?)."; return }
            val local = payload(0)
            val changed = hashOf(local) != store.syncHash
            if (remote != null && g.ts > store.syncTs) {
                applyRemote(remote, merge = changed)
                if (blockPush) { syncMsg = "Algumas listas da nuvem não baixaram; vou tentar de novo."; return }
                if (!changed) { store.syncTs = g.ts; store.syncHash = hashOf(payload(0)); syncMsg = "☁ Atualizado da nuvem."; return }
            } else if (!changed && remote != null) { if (manual) syncMsg = "☁ Tudo em dia."; return }
            val now = System.currentTimeMillis()
            val p = payload(now)
            val enc = withContext(Dispatchers.Default) { Sync.encrypt(p.toString(), ek) }
            val pr = withContext(Dispatchers.IO) { Sync.call("put", u, ak, enc, now) }
            if (accessFailed(pr)) return
            if (pr.ok) { store.syncTs = now; store.syncHash = hashOf(p); syncMsg = "☁ Salvo na nuvem." } else syncMsg = pr.msg
        } catch (e: Throwable) { syncMsg = "Falha ao sincronizar: " + Util.friendlyErr(e) }
        finally { syncBusy = false }
    }

    fun accountLogin(user: String, pass: String) {
        val u = user.trim().lowercase()
        if (u.length < 3) { accessMsg = "Digite o usuário."; return }
        if (pass.length < 6) { accessMsg = "Digite a senha (mínimo 6 caracteres)."; return }
        if (syncBusy) return
        syncBusy = true; accessMsg = "Entrando…"
        scope.launch {
            val (ak, ek) = withContext(Dispatchers.Default) { Sync.derive(u, pass) }
            val r = withContext(Dispatchers.IO) { Sync.call("get", u, ak) }
            if (!r.ok) { accessMsg = r.msg; syncBusy = false; return@launch }
            if (store.lastUser.isNotEmpty() && store.lastUser != u) wipeLocal() // outro usuário na mesma TV: não mistura os dados
            store.lastUser = u
            store.syncUser = u; store.syncAuth = ak; store.syncEnc = ek; store.syncTs = 0; store.syncHash = ""
            store.accessExp = r.exp
            syncUser = u; locked = false; accessMsg = ""; syncMsg = "Entrou! Baixando seus dados…"
            syncBusy = false
            syncNow(true)
        }
    }

    fun accountLogout() { lockOut("Você saiu. Entre de novo para usar o app.") }

    // ------------------------------------------------------------ atualização automática do app
    var upd by mutableStateOf<Updater.Info?>(null)
    var updStage by mutableIntStateOf(0)   // 0 nada, 1 baixando, 2 pronta para instalar, 3 falhou
    var updPct by mutableIntStateOf(0)
    var updSnooze by mutableStateOf(false)
    val updForced: Boolean get() = upd?.let { it.min > Updater.current(ctx) } ?: false
    private var updFile: java.io.File? = null

    fun checkUpdate() {
        if (updStage == 1) return
        scope.launch {
            val cur = Updater.current(ctx)
            val i = withContext(Dispatchers.IO) { Updater.fetch() } ?: return@launch
            if (i.code <= cur) { upd = null; updStage = 0; return@launch }
            if (upd?.code == i.code && updStage == 2) return@launch
            upd = i; updStage = 1; updPct = 0; updSnooze = false
            val f = withContext(Dispatchers.IO) { Updater.download(ctx) { p -> updPct = p } }
            if (f != null) { updFile = f; updStage = 2 } else updStage = 3
        }
    }
    fun installUpdate() {
        val f = updFile ?: Updater.apk(ctx)
        if (!f.exists()) { checkUpdate(); return }
        val ok = try { Updater.install(ctx, f) } catch (e: Throwable) { toast("Não consegui abrir o instalador: " + (e.message ?: "erro")); true }
        if (!ok) toast("Permita \"instalar apps desconhecidos\" para o NTPlay e toque em Instalar de novo.")
    }
}

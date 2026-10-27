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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PlayReq(val item: Item, val ep: Ep, val resume: Long)

val TABS = listOf("RECOMENDAÇÕES", "TV AO VIVO", "FUTEBOL", "FILMES", "SÉRIES", "INFANTIL", "ANIMES", "FAVORITOS")

class AppState(val ctx: Context) {
    val store = Store(ctx)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    var lib by mutableStateOf<Library?>(null)
    var status by mutableStateOf("Nenhuma lista carregada")
    var busy by mutableStateOf(false)
    var tab by mutableStateOf(store.tab.let { if (it in TABS) it else TABS[0] })
    var group by mutableStateOf("")
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
    var yearSel by mutableStateOf("")
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
        tab = t; group = ""; detail = null; store.tab = t; yearSel = ""
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
        autoUpdate()
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
                val b = Library.build(files, adult, store.base64, store.adultByName)
                // pré-calcula agrupamentos fora da tela (evita travar ao trocar de aba)
                b.every; b.byKey; b.liveGroups; b.movieGroups; b.serieGroups; b.kids; b.anime
                b
            }
            lib = l
            group = ""
            status = "${l.live.size} canais • ${l.movies.size} filmes • ${l.series.size} séries"
            logosLater()
            trend = emptyList()
            trendBusy = true
            scope.launch {
                try {
                    trend = withContext(Dispatchers.IO) { Trending.load(ctx, l) }
                    // pré-carrega capas dos primeiros itens para a tela inicial já abrir pronta (máx. ~6 s)
                    kotlinx.coroutines.withTimeoutOrNull(6000) {
                        val first = trend.flatMap { it.items.take(8) }.filter { it.kind != 'l' }.distinctBy { it.key }.take(40)
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
            toast("Erro ao ler a lista: " + (e.message ?: e.javaClass.simpleName))
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
            } catch (e: Throwable) { busy = false; toast("Falha ao atualizar: " + (e.message ?: "erro")) }
        }
    }

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
    var updateOnStart by mutableStateOf(store.updateOnStart)
    fun changeUpdateOnStart(v: Boolean) { updateOnStart = v; store.updateOnStart = v }

    private suspend fun syncList(m: ListMeta) {
        withContext(Dispatchers.IO) {
            val f = store.file(m.id); val cr = Xtream.creds(m.url)
            if (cr != null) Xtream.buildM3u(cr, f) { } else Net.downloadAny(m.url, f)
            val l = store.lists(); l.firstOrNull { it.id == m.id }?.let { it.count = countItems(f); it.date = System.currentTimeMillis() }; store.saveLists(l)
        }
    }
    private fun autoUpdate() {
        if (!store.updateOnStart && store.updateHours <= 0) return
        scope.launch {
            delay(12000)
            val now = System.currentTimeMillis()
            val due = store.lists().filter { it.active && it.url.isNotEmpty() && (store.updateOnStart || (store.updateHours > 0 && now - it.date > store.updateHours * 3600_000L)) }
            if (due.isEmpty()) return@launch
            for (m in due) try { syncList(m) } catch (e: Throwable) { }
            reload()
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
        for (p in progMap.entries.filter { !it.value.done }.sortedByDescending { it.value.ts }) {
            val it = l.byKey[p.value.k] ?: continue
            if (!seen.add(it.key)) continue
            val ep = it.eps.firstOrNull { e -> e.url == p.key } ?: (if (needsEps(it)) it.eps[0] else null) ?: continue
            out.add(it to ep)
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
            } catch (e: Throwable) { epsErr = "Não consegui carregar os episódios (" + (e.message ?: "erro") + ")." }
            epsLoading = false
        }
    }

    fun playItem(it: Item, ep: Ep? = null, q: List<Item>? = null) {
        if (needsEps(it)) { openDetail(it); return }
        val e = ep ?: startEp(it)
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
            for (it in l.every) {
                val t = it.norm
                if (t.startsWith(n)) { first.add(it); if (first.size >= 150) break }
                else if (rest.size < 150) {
                    val g = synchronized(l.gnorm) { l.gnorm.getOrPut(it.group) { Util.norm(it.group) } }
                    if (t.contains(n) || g.contains(n)) rest.add(it)
                }
            }
            (first + rest).take(200)
        }
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
        } catch (e: Throwable) { syncMsg = "Falha ao sincronizar: " + (e.message ?: e.javaClass.simpleName) }
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

package com.natanael.ntplay.tv

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PlayReq(val item: Item, val ep: Ep, val resume: Long)

val TABS = listOf("RECOMENDAÇÕES", "TV AO VIVO", "FILMES", "SÉRIES", "INFANTIL", "ANIMES", "FAVORITOS")

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
    var epsErr by mutableStateOf("")
    var queue: List<Item> = emptyList()
    private val progMap: HashMap<String, Prog> = store.progress()

    fun toast(m: String) {
        toastMsg = m
        scope.launch { delay(4500); if (toastMsg == m) toastMsg = "" }
    }

    fun selectTab(t: String) {
        tab = t; group = ""; detail = null; store.tab = t
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
                val b = Library.build(files, adult)
                // pré-calcula agrupamentos fora da tela (evita travar ao trocar de aba)
                b.every; b.byKey; b.liveGroups; b.movieGroups; b.serieGroups; b.kids; b.anime
                b
            }
            lib = l
            group = ""
            status = "${l.live.size} canais • ${l.movies.size} filmes • ${l.series.size} séries"
            logosLater()
            trend = emptyList()
            scope.launch { try { trend = withContext(Dispatchers.IO) { Trending.load(ctx, l) } } catch (e: Throwable) { } }
        } catch (e: Throwable) {
            status = "Erro ao ler a lista"
            toast("Erro ao ler a lista: " + (e.message ?: e.javaClass.simpleName))
        }
        busy = false
        listsVer++
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

    fun toggleFav(key: String) {
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
}

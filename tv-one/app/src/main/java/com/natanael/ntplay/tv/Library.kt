package com.natanael.ntplay.tv

import java.io.File
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.FileInputStream
import java.io.FileOutputStream

class Ep(val name: String, val url: String, var s: Int, var e: Int)

class Item(
    val kind: Char,
    val key: String,
    val title: String,
    val group: String,
    var logo: String,
    val eps: ArrayList<Ep>,
    val adult: Boolean
) {
    var ns = 1
    var tvgId = ""
    /** posição na lista (maior = entrou depois = mais recente); séries sobem quando ganham episódio novo */
    var pos = 0
    /** ano escrito no nome (0 se não tiver) */
    /** DUB/LEG, título limpo e linha "ano · qualidade" calculados uma vez (antes rodavam regex a cada rolagem) */
    val aud: String by lazy { Util.audioTag(this) }
    val shown: String by lazy { if (kind == 'l') title else Util.cleanT(title).ifEmpty { title } }
    val subT: String by lazy { listOfNotNull(Util.yearOf(title).ifEmpty { null }, if (kind == 's') "Série" else null, Util.quality(title).ifEmpty { null }).joinToString(" · ") }
    val yr: Int by lazy { Util.yearOf(title).toIntOrNull() ?: 0 }
    // PUBLICATION, nunca NONE: três threads leem isto ao mesmo tempo — os cards de canal
    // na thread principal (Epg.nowTitle), a busca em Dispatchers.Default e o Epg.load em
    // Dispatchers.IO. Com NONE o lazy do Kotlin não tem @Volatile: uma thread enxerga
    // initializer já nulo e _value ainda não publicado, e o `initializer!!()` lança NPE.
    // Util.norm é puro, então rodar duas vezes não faz mal; PUBLICATION publica com CAS.
    val norm: String by lazy(LazyThreadSafetyMode.PUBLICATION) { Util.norm(title) }
}

class Library(val live: List<Item>, val movies: List<Item>, val series: List<Item>) {
    val every: List<Item> by lazy { live + movies + series }
    val gnorm = HashMap<String, String>()
    val byKey: HashMap<String, Item> by lazy {
        val m = HashMap<String, Item>(every.size * 2)
        for (i in every) m[i.key] = i
        m
    }
    val kids: List<Item> by lazy { (movies + series + live).filter { Util.theme(it) == "INFANTIL" } }
    val anime: List<Item> by lazy { (series + movies + live).filter { Util.theme(it) == "ANIMES" } }

    /** categorias de lançamento/estreia/novidade ficam no topo da lista lateral */
    private fun isNewGroup(g: String): Boolean { val k = java.text.Normalizer.normalize(g.lowercase(), java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}+"), ""); return k.contains("lancamento") || k.contains("estreia") || k.contains("novidade") || k.contains("recem") }
    private fun grp(l: List<Item>): List<Pair<String, List<Item>>> =
        l.groupBy { it.group }.entries
            .sortedWith(compareBy<Map.Entry<String, List<Item>>>({ it.value.firstOrNull()?.adult == true }, { !isNewGroup(it.key) }).thenBy { it.key.lowercase() })
            .map { it.key to it.value }

    // ---- cache de ordenações/anos/animes: a troca de aba só escolhe o que já está pronto
    private val sortCache = java.util.concurrent.ConcurrentHashMap<String, List<Item>>()
    fun sorted(name: String, l: List<Item>, mode: String, yf: (Item) -> Int = { it.yr }): List<Item> {
        if (sortCache.size > 40) sortCache.clear() // não deixa o cache crescer sem fim (memória da TV é pouca)
        return sortCache.getOrPut("$name|$mode") { sortItems(l, mode, yf) }
    }
    private val yearCache = java.util.concurrent.ConcurrentHashMap<String, List<Pair<String, Int>>>()
    fun years(name: String, l: List<Item>, of: (Item) -> String): List<Pair<String, Int>> =
        yearCache.getOrPut(name) { l.groupingBy(of).eachCount().filterKeys { it.isNotEmpty() }.toList().sortedByDescending { it.first } }
    val animeCR: List<Item> by lazy { anime.filter { Util.animeSrc(it) == "CR" } }
    val animeFN: List<Item> by lazy { anime.filter { Util.animeSrc(it) == "FN" } }

    val liveGroups by lazy { grp(live) }
    val movieGroups by lazy { grp(movies) }
    val serieGroups by lazy { grp(series) }

    companion object {
        private val TV_PACK = Regex("^\\s*[A-Za-z0-9]{1,8}\\s*\\|\\s*filmes\\s*&\\s*s[eé]ries", RegexOption.IGNORE_CASE)
        fun build(files: List<File>, showAdult: Boolean, b64: Boolean = false, adultByName: Boolean = true, hidden: Set<String> = emptySet()): Library {
            val live = ArrayList<Item>()
            val mv = ArrayList<Item>()
            val sm = LinkedHashMap<String, Item>()
            val seenLive = HashSet<String>()
            val seenMovie = HashSet<String>()
            var seq = 0
            for (f in files) {
                M3u.parse(f) { rawName, url, rawGroup, logo, tvgId ->
                    val name = if (b64) Util.maybeB64(rawName) else rawName
                    val group = if (b64) Util.maybeB64(rawGroup) else rawGroup
                    val adult = adultByName && Util.isAdult("$name $group")
                    if (adult && !showAdult) return@parse
                    if (hidden.isNotEmpty() && Util.cleanG(group) in hidden) return@parse
                    val kd = Util.guess(url, group, name)
                    // categorias "1 | FILMES & SERIES", "5 | FILMES & SERIES"… são canais de TV (Warner, USA, Universal…) empacotados como série: ficam fora
                    if (kd != 'l' && TV_PACK.containsMatchIn(group)) return@parse
                    when (kd) {
                        // o mesmo filme costuma vir em mais de uma categoria (ou em mais de uma lista).
                        // duas vezes a mesma "key" derruba o app nas grades do Compose, então entra só uma vez.
                        'm' -> if (seenMovie.add("m:$url")) mv.add(Item('m', "m:$url", name, Util.cleanG(group), logo, arrayListOf(Ep(name, url, 0, 0)), adult).also { it.pos = seq++ })
                        's' -> {
                            val i = Util.epInfo(name)
                            val t = i?.first ?: name
                            val k = "s:" + group + "|" + t.lowercase()
                            val o = sm.getOrPut(k) { Item('s', k, t, Util.cleanG(group), logo, ArrayList(), adult) }
                            val s = i?.second ?: 1
                            val e = i?.third ?: (o.eps.size + 1)
                            o.eps.add(Ep(name, url, s, e))
                            o.pos = seq++
                            if (o.logo.isEmpty()) o.logo = logo
                        }
                        else -> if (seenLive.add(url)) live.add(Item('l', "l:$url", name, Util.cleanG(group), logo, arrayListOf(Ep(name, url, 0, 0)), adult).also { it.tvgId = tvgId })
                    }
                }
            }
            // canais sem logo herdam o logo de outra versão do mesmo canal (FHD/HD/SD...)
            val lk = HashMap<String, String>()
            for (o in live) if (o.logo.isNotEmpty()) { val k = Logos.key(o.title); if (!lk.containsKey(k)) lk[k] = o.logo }
            for (o in live) if (o.logo.isEmpty()) lk[Logos.key(o.title)]?.let { o.logo = it }
            val ser = ArrayList(sm.values)
            for (o in ser) {
                o.eps.sortWith(compareBy<Ep>({ it.s }, { it.e }))
                o.ns = o.eps.maxOf { it.s }
            }
            // capas repetidas em muitos títulos são removidas (evita a mesma imagem em todos)
            val cnt = HashMap<String, Int>()
            for (o in mv) if (o.logo.isNotEmpty()) cnt[o.logo] = (cnt[o.logo] ?: 0) + 1
            for (o in ser) if (o.logo.isNotEmpty()) cnt[o.logo] = (cnt[o.logo] ?: 0) + 1
            for (o in mv) if ((cnt[o.logo] ?: 0) > 2) o.logo = ""
            for (o in ser) if ((cnt[o.logo] ?: 0) > 2) o.logo = ""
            val byAdult = compareBy<Item> { it.adult }
            return Library(live.sortedWith(byAdult), mv.sortedWith(byAdult), ser.sortedWith(byAdult))
        }
    }
}

object M3u {
    private fun attr(l: String, key: String): String {
        val k = "$key=\""
        val i = l.indexOf(k, ignoreCase = true)
        if (i < 0) return ""
        val s = i + k.length
        val e = l.indexOf('"', s)
        return if (e < 0) "" else l.substring(s, e)
    }

    fun parse(file: File, cb: (String, String, String, String, String) -> Unit) {
        var tvg = ""
        var name = ""
        var group = ""
        var logo = ""
        var has = false
        file.bufferedReader(Charsets.UTF_8, 1 shl 16).use { r ->
            while (true) {
                val raw = r.readLine() ?: break
                val l = raw.trim()
                if (l.isEmpty()) continue
                if (l.startsWith("#EXTINF")) {
                    has = true
                    logo = attr(l, "tvg-logo")
                    tvg = attr(l, "tvg-id")
                    group = attr(l, "group-title")
                    val q = l.lastIndexOf('"')
                    val c = l.indexOf(',', if (q > 0) q else 0)
                    name = if (c < 0) "" else l.substring(c + 1).trim()
                    if (name.isEmpty()) name = attr(l, "tvg-name")
                    continue
                }
                if (l.startsWith("#EXTGRP:")) {
                    if (group.isEmpty()) group = l.substring(8).trim()
                    continue
                }
                if (l[0] == '#') continue
                if (!(l.startsWith("http://") || l.startsWith("https://"))) continue
                val n = if (has && name.isNotEmpty()) name else l.substringAfterLast('/').substringBefore('?').ifEmpty { "Canal" }
                cb(n, l, group.ifEmpty { "Sem grupo" }, logo, tvg)
                has = false; name = ""; group = ""; logo = ""; tvg = ""
            }
        }
    }
}

// ---- ordenação e ano (ficam aqui, no motor, porque o celular copia este arquivo e não tem o UI.kt da TV)
/** Novidades = lançamentos (ano atual e anterior, mais novo primeiro) e depois o que entrou há pouco na lista */
fun sortItems(l: List<Item>, mode: String, yf: (Item) -> Int = { it.yr }): List<Item> = when (mode) {
    "recentes" -> l.sortedByDescending { it.pos }
    "lanc" -> { val y = HashMap<String, Int>(l.size * 2); for (x in l) y[x.key] = yf(x); l.sortedWith(compareByDescending<Item> { y[it.key] ?: 0 }.thenByDescending { it.pos }) }
    "az" -> l.sortedBy { it.norm }
    else -> {
        val cut = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR) - 1
        val y = HashMap<String, Int>(l.size * 2); for (x in l) y[x.key] = yf(x)
        val (a, b) = l.partition { (y[it.key] ?: 0) >= cut }
        a.sortedWith(compareByDescending<Item> { y[it.key] ?: 0 }.thenByDescending { it.pos }) + b.sortedByDescending { it.pos }
    }
}

fun yearOfItem(st: AppState, it: Item): String {
    if (it.yr > 0) return it.yr.toString()
    val c = Meta.cached(st.store, it)?.y ?: ""
    return if (c.length == 4 && c.all { ch -> ch.isDigit() }) c else ""
}



/**
 * Guarda a biblioteca já processada (canais/filmes/séries) em um arquivo binário.
 * Ao abrir o app, se as listas não mudaram (mesmo tamanho e data) e a versão do app é a mesma,
 * lê este arquivo em vez de analisar o M3U inteiro de novo. Qualquer problema → cai no caminho normal.
 */
object LibCache {
    private const val MAGIC = 0x4E544C42 // "NTLB"
    private const val FORMAT = 1
    /** subir este número só quando mudar a forma de ler/classificar a lista (assim o cache não é jogado fora a cada atualização do app) */
    private const val ENGINE = 2

    fun sig(files: List<File>, showAdult: Boolean, b64: Boolean, adultByName: Boolean, hidden: Set<String> = emptySet()): String =
        files.joinToString("|") { it.name + ":" + it.length() + ":" + it.lastModified() } + "#" + showAdult + b64 + adultByName + hidden.sorted().hashCode() + "#e" + ENGINE + "#" + FORMAT

    private fun file(dir: File) = File(dir, "lib.bin")

    private fun DataOutputStream.str(s: String) {
        val b = s.toByteArray(Charsets.UTF_8); writeInt(b.size); write(b)
    }
    private fun DataInputStream.str(): String {
        val n = readInt(); if (n < 0 || n > 1_000_000) throw java.io.IOException("cache inválido")
        val b = ByteArray(n); readFully(b); return String(b, Charsets.UTF_8)
    }

    fun save(dir: File, sig: String, lib: Library) {
        val tmp = File(dir, "lib.bin.tmp")
        try {
            DataOutputStream(BufferedOutputStream(FileOutputStream(tmp), 1 shl 16)).use { o ->
                o.writeInt(MAGIC); o.str(sig)
                o.writeInt(lib.live.size)
                for (i in lib.live) { o.str(i.key); o.str(i.title); o.str(i.group); o.str(i.logo); o.writeBoolean(i.adult); o.str(i.tvgId); o.str(i.eps[0].url) }
                o.writeInt(lib.movies.size)
                for (i in lib.movies) { o.str(i.key); o.str(i.title); o.str(i.group); o.str(i.logo); o.writeBoolean(i.adult); o.writeInt(i.pos); o.str(i.eps[0].name); o.str(i.eps[0].url) }
                o.writeInt(lib.series.size)
                for (i in lib.series) {
                    o.str(i.key); o.str(i.title); o.str(i.group); o.str(i.logo); o.writeBoolean(i.adult); o.writeInt(i.pos); o.writeInt(i.ns)
                    o.writeInt(i.eps.size)
                    for (e in i.eps) { o.str(e.name); o.str(e.url); o.writeInt(e.s); o.writeInt(e.e) }
                }
            }
            val f = file(dir); f.delete(); tmp.renameTo(f)
        } catch (e: Throwable) { try { tmp.delete() } catch (x: Throwable) { } }
    }

    fun load(dir: File, sig: String): Library? {
        val f = file(dir)
        if (!f.exists()) return null
        return try {
            DataInputStream(BufferedInputStream(FileInputStream(f), 1 shl 16)).use { r ->
                if (r.readInt() != MAGIC || r.str() != sig) return null
                val nl = r.readInt(); val live = ArrayList<Item>(nl)
                repeat(nl) {
                    val key = r.str(); val title = r.str(); val group = r.str(); val logo = r.str(); val adult = r.readBoolean(); val tvg = r.str(); val url = r.str()
                    live.add(Item('l', key, title, group, logo, arrayListOf(Ep(title, url, 0, 0)), adult).also { it.tvgId = tvg })
                }
                val nm = r.readInt(); val mv = ArrayList<Item>(nm)
                repeat(nm) {
                    val key = r.str(); val title = r.str(); val group = r.str(); val logo = r.str(); val adult = r.readBoolean(); val pos = r.readInt(); val en = r.str(); val url = r.str()
                    mv.add(Item('m', key, title, group, logo, arrayListOf(Ep(en, url, 0, 0)), adult).also { it.pos = pos })
                }
                val ns = r.readInt(); val sr = ArrayList<Item>(ns)
                repeat(ns) {
                    val key = r.str(); val title = r.str(); val group = r.str(); val logo = r.str(); val adult = r.readBoolean(); val pos = r.readInt(); val nseas = r.readInt()
                    val ne = r.readInt(); val eps = ArrayList<Ep>(ne)
                    repeat(ne) { eps.add(Ep(r.str(), r.str(), r.readInt(), r.readInt())) }
                    sr.add(Item('s', key, title, group, logo, eps, adult).also { it.pos = pos; it.ns = nseas })
                }
                Library(live, mv, sr)
            }
        } catch (e: Throwable) { try { f.delete() } catch (x: Throwable) { }; null }
    }
}

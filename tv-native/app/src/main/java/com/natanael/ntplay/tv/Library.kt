package com.natanael.ntplay.tv

import java.io.File

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
    val norm: String by lazy(LazyThreadSafetyMode.NONE) { Util.norm(title) }
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

    private fun grp(l: List<Item>): List<Pair<String, List<Item>>> =
        l.groupBy { it.group }.entries
            .sortedWith(compareBy<Map.Entry<String, List<Item>>>({ it.value.firstOrNull()?.adult == true }).thenBy { it.key.lowercase() })
            .map { it.key to it.value }

    val liveGroups by lazy { grp(live) }
    val movieGroups by lazy { grp(movies) }
    val serieGroups by lazy { grp(series) }

    companion object {
        fun build(files: List<File>, showAdult: Boolean, b64: Boolean = false, adultByName: Boolean = true): Library {
            val live = ArrayList<Item>()
            val mv = ArrayList<Item>()
            val sm = LinkedHashMap<String, Item>()
            val seenLive = HashSet<String>()
            for (f in files) {
                M3u.parse(f) { rawName, url, rawGroup, logo ->
                    val name = if (b64) Util.maybeB64(rawName) else rawName
                    val group = if (b64) Util.maybeB64(rawGroup) else rawGroup
                    val adult = adultByName && Util.isAdult("$name $group")
                    if (adult && !showAdult) return@parse
                    when (Util.guess(url, group, name)) {
                        'm' -> mv.add(Item('m', "m:$url", name, Util.cleanG(group), logo, arrayListOf(Ep(name, url, 0, 0)), adult))
                        's' -> {
                            val i = Util.epInfo(name)
                            val t = i?.first ?: name
                            val k = "s:" + group + "|" + t.lowercase()
                            val o = sm.getOrPut(k) { Item('s', k, t, Util.cleanG(group), logo, ArrayList(), adult) }
                            val s = i?.second ?: 1
                            val e = i?.third ?: (o.eps.size + 1)
                            o.eps.add(Ep(name, url, s, e))
                            if (o.logo.isEmpty()) o.logo = logo
                        }
                        else -> if (seenLive.add(url)) live.add(Item('l', "l:$url", name, Util.cleanG(group), logo, arrayListOf(Ep(name, url, 0, 0)), adult))
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

    fun parse(file: File, cb: (String, String, String, String) -> Unit) {
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
                cb(n, l, group.ifEmpty { "Sem grupo" }, logo)
                has = false; name = ""; group = ""; logo = ""
            }
        }
    }
}

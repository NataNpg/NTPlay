package com.natanael.ntplay.tv

import java.text.Normalizer

object Util {
    const val UA = "VLC/3.0.20 LibVLC/3.0.20"
    const val SEND_BASE = "https://natanpg.github.io/NTPlay/enviar.html"

    private val diac = Regex("\\p{InCombiningDiacriticalMarks}+")
    private val paren = Regex("\\(.*?\\)|\\[.*?\\]")
    private val words = Regex("\\b(dublado|legendado|dual|nacional|4k|fhd|uhd|hd|1080p|720p|web-?dl)\\b", RegexOption.IGNORE_CASE)
    private val non = Regex("[^a-z0-9]+")
    private val ws = Regex("\\s+")
    private val adr = Regex("\\bxxx\\b|adult|\\+18|18\\+|er[óo]tic|porn|sexy|sexo", RegexOption.IGNORE_CASE)
    private val ep1 = Regex("^(.*?)[\\s._\\-–|:]*[\\[(]?\\b[sStT]\\s?(\\d{1,2})\\s?[eExX]\\s?(\\d{1,3})\\b")
    private val ep2 = Regex("^(.*?)[\\s._\\-–|:]*\\b(\\d{1,2})x(\\d{1,3})\\b")
    private val serieG = Regex("s[eé]rie|novela|anime|dorama|temporada", RegexOption.IGNORE_CASE)
    private val movieG = Regex("filme|movie|vod|cinema|lan[çc]amento", RegexOption.IGNORE_CASE)
    private val kids = Regex("kids|infantil|desenho|cartoon|baby|junior|gloob|nick|disney jr|gloobinho", RegexOption.IGNORE_CASE)
    private val animeR = Regex("anime", RegexOption.IGNORE_CASE)
    private val cleanGr = Regex("^\\s*(filmes?|movies?|s[eé]ries?|series|vod|canais?)\\s*[|:\\->»]+\\s*", RegexOption.IGNORE_CASE)

    fun norm(s: String): String {
        var t = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD)
        t = diac.replace(t, "")
        t = paren.replace(t, " ")
        t = words.replace(t, " ")
        return non.replace(t, " ").trim()
    }

    fun cleanT(s: String): String = ws.replace(words.replace(paren.replace(s, " "), " "), " ").trim()

    fun yearOf(s: String): String = Regex("\\((\\d{4})\\)").find(s)?.groupValues?.get(1) ?: ""

    fun isAdult(s: String) = adr.containsMatchIn(s)

    fun cleanG(g: String): String {
        val r = cleanGr.replace(g, "").trim()
        return if (r.isEmpty()) (g.trim().ifEmpty { "Sem grupo" }) else r
    }

    fun epInfo(n: String): Triple<String, Int, Int>? {
        val m = ep1.find(n) ?: ep2.find(n) ?: return null
        val t = m.groupValues[1].trim().ifEmpty { n }
        return Triple(t, m.groupValues[2].toInt(), m.groupValues[3].toInt())
    }

    /** 'l' = ao vivo, 'm' = filme, 's' = série */
    fun guess(u: String, g: String, n: String): Char {
        val x = u.lowercase().substringBefore('?')
        if (x.contains("/live/")) return 'l'
        if (epInfo(n) != null) return 's'
        if (x.contains("/series/")) return 's'
        if (x.contains("/movie/")) return 'm'
        if (serieG.containsMatchIn(g)) return 's'
        if (movieG.containsMatchIn(g) || x.endsWith(".mp4") || x.endsWith(".mkv") || x.endsWith(".avi")) return 'm'
        return 'l'
    }

    fun theme(it: Item): String {
        val t = if (it.kind == 'l') it.group + " " + it.title else it.group
        if (kids.containsMatchIn(t)) return "INFANTIL"
        if (animeR.containsMatchIn(t)) return "ANIMES"
        return ""
    }

    fun looksPT(s: String): Boolean {
        val w = " " + s.lowercase().replace(Regex("[^a-zà-ú ]"), " ") + " "
        val pt = Regex(" (que|não|uma|para|com|dos|das|ele|ela|seu|sua|mais|como|por|são|em) ").findAll(w).count()
        val en = Regex(" (the|and|of|his|her|with|that|for|who|their|from|are|was|is) ").findAll(w).count()
        return pt > en
    }

    fun fmt(ms: Long): String {
        val t = ms / 1000
        val h = t / 3600
        val m = (t % 3600) / 60
        val s = t % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }
}

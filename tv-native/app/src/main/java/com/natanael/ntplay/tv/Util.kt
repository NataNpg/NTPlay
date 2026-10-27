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

    private val yrR = Regex("\\((\\d{4})\\)")
    fun yearOf(s: String): String = yrR.find(s)?.groupValues?.get(1) ?: ""

    private val qR = Regex("\\b(4k|uhd|2160p|fhd|1080p|hd|720p|sd|h\\.?265|hevc)\\b", RegexOption.IGNORE_CASE)

    /** qualidade escrita no nome (4K, FHD, HD, H265...) */
    fun quality(s: String): String {
        val f = qR.findAll(s).map { it.value.lowercase().replace(".", "") }.toSet()
        val res = when { f.any { it == "4k" || it == "uhd" || it == "2160p" } -> "4K"; f.any { it == "fhd" || it == "1080p" } -> "Full HD"; f.any { it == "hd" || it == "720p" } -> "HD"; f.contains("sd") -> "SD"; else -> "" }
        val c = if (f.any { it == "h265" || it == "hevc" }) "H.265" else ""
        return listOf(res, c).filter { it.isNotEmpty() }.joinToString(" · ")
    }

    fun isAdult(s: String) = adr.containsMatchIn(s)

    private val b64re = Regex("^[A-Za-z0-9+/]{8,}={0,2}$")
    /** decodifica Base64 só quando o texto realmente parece Base64 e vira texto legível (senão devolve igual) */
    fun maybeB64(s: String): String {
        val t = s.trim()
        if (t.length % 4 != 0 || !b64re.matches(t)) return s
        return try {
            val d = String(android.util.Base64.decode(t, android.util.Base64.DEFAULT), Charsets.UTF_8)
            if (d.isNotBlank() && d.length >= 2 && d.none { it.code in 0..8 || it.code in 14..31 }) d else s
        } catch (e: Exception) { s }
    }

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

    private val crR = Regex("crunchy\\s*roll", RegexOption.IGNORE_CASE)
    private val fnR = Regex("funimation", RegexOption.IGNORE_CASE)
    private val legR = Regex("\\b(leg|legendad[oa]s?)\\b|\\[l\\]|\\(l\\)", RegexOption.IGNORE_CASE)
    private val dubR = Regex("\\b(dub|dublad[oa]s?|dual)\\b|\\[d\\]|\\(d\\)", RegexOption.IGNORE_CASE)

    /** "CR" = Crunchyroll, "FN" = Funimation Now, "" = outro */
    fun animeSrc(it: Item): String {
        val s = it.group + " " + it.title
        return if (crR.containsMatchIn(s)) "CR" else if (fnR.containsMatchIn(s)) "FN" else ""
    }

    /** "DUB" (dublado), "LEG" (legendado) ou "" */
    fun audioTag(it: Item): String {
        val s = it.title + " " + it.group + " " + (it.eps.firstOrNull()?.name ?: "")
        if (dubR.containsMatchIn(s)) return "DUB"
        if (legR.containsMatchIn(s)) return "LEG"
        return ""
    }

    fun theme(it: Item): String {
        val t = if (it.kind == 'l') it.group + " " + it.title else it.group
        if (kids.containsMatchIn(t)) return "INFANTIL"
        if (animeR.containsMatchIn(t) || (it.kind != 'l' && animeSrc(it).isNotEmpty())) return "ANIMES"
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

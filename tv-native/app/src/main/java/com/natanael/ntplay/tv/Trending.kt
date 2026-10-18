package com.natanael.ntplay.tv

import android.content.Context
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * "Em alta": cruza rankings da internet com o que existe na sua lista
 * (só mostra o que dá para assistir).
 *  - iTunes Brasil: filmes mais populares (títulos em português)
 *  - Netflix Top 10 da semana (filmes e séries)
 *  - TVMaze: séries com episódios novos hoje nos streamings
 *  - Lista: categorias de lançamentos
 */
object Trending {
    class Row(val title: String, val items: List<Item>)

    private val art = Regex("^(the|o|a|os|as|el|la) ")
    fun key(t: String): String = art.replace(Util.norm(Util.cleanT(t)), "").trim()

    private fun get(url: String): String? = try {
        Net.quick.newCall(Request.Builder().url(url).header("User-Agent", "Mozilla/5.0 (Linux; Android 10; TV) NTPlay").build()).execute()
            .use { r -> if (r.isSuccessful) r.body?.string() else null }
    } catch (e: Exception) { null }

    private fun itunesMovies(): List<String> {
        val t = get("https://itunes.apple.com/br/rss/topmovies/limit=100/json") ?: return emptyList()
        val out = ArrayList<String>()
        try {
            val a = JSONObject(t).getJSONObject("feed").optJSONArray("entry") ?: return out
            for (i in 0 until a.length()) a.getJSONObject(i).optJSONObject("im:name")?.optString("label")?.let { if (it.isNotEmpty()) out.add(it) }
        } catch (e: Exception) { }
        return out
    }

    /** Netflix Top 10 global da última semana: (filmes, séries) */
    private fun netflix(): Pair<List<String>, List<String>> {
        val films = ArrayList<String>(); val tv = ArrayList<String>()
        try {
            Net.quick.newCall(Request.Builder().url("https://www.netflix.com/tudum/top10/data/all-weeks-global.tsv").header("User-Agent", "Mozilla/5.0").build()).execute().use { r ->
                if (!r.isSuccessful) return films to tv
                val rd = r.body!!.charStream().buffered()
                rd.readLine() // cabeçalho
                var week = ""
                while (true) {
                    val l = rd.readLine() ?: break
                    val c = l.split('\t')
                    if (c.size < 4) continue
                    if (week.isEmpty()) week = c[0]
                    if (c[0] != week) break
                    if (c[1].startsWith("Films")) films.add(c[3]) else tv.add(c[3])
                }
            }
        } catch (e: Exception) { }
        return films to tv
    }

    /** séries com episódio novo hoje em streamings */
    private fun tvmazeWeb(): List<String> {
        val t = get("https://api.tvmaze.com/schedule/web") ?: return emptyList()
        val out = LinkedHashSet<String>()
        try {
            val a = JSONArray(t)
            for (i in 0 until a.length()) {
                val sh = a.getJSONObject(i).optJSONObject("_embedded")?.optJSONObject("show") ?: continue
                val lang = sh.optString("language")
                if (lang == "English" || lang == "Portuguese" || lang == "Spanish" || lang == "Korean" || lang == "Japanese") out.add(sh.optString("name"))
            }
        } catch (e: Exception) { }
        return out.toList()
    }

    private fun names(ctx: Context): JSONObject {
        val f = File(ctx.filesDir, "trend.json")
        try {
            if (f.exists() && System.currentTimeMillis() - f.lastModified() < 8 * 3600_000L) return JSONObject(f.readText())
        } catch (e: Exception) { }
        val o = JSONObject()
        val (nf, nt) = netflix()
        o.put("itunes", JSONArray(itunesMovies()))
        o.put("nfFilms", JSONArray(nf))
        o.put("nfTv", JSONArray(nt))
        o.put("web", JSONArray(tvmazeWeb()))
        try { f.writeText(o.toString()) } catch (e: Exception) { }
        return o
    }

    private fun arr(o: JSONObject, k: String): List<String> {
        val a = o.optJSONArray(k) ?: return emptyList()
        return (0 until a.length()).map { a.optString(it) }
    }

    fun load(ctx: Context, lib: Library): List<Row> {
        val mv = HashMap<String, Item>(); val sr = HashMap<String, Item>()
        for (it in lib.movies) if (!it.adult) { val k = key(it.title); if (k.length > 1 && !mv.containsKey(k)) mv[k] = it }
        for (it in lib.series) if (!it.adult) { val k = key(it.title); if (k.length > 1 && !sr.containsKey(k)) sr[k] = it }
        fun match(l: List<String>, m: Map<String, Item>): List<Item> {
            val seen = HashSet<String>(); val out = ArrayList<Item>()
            for (n in l) { val it = m[key(n)] ?: continue; if (seen.add(it.key)) out.add(it) }
            return out
        }
        val rows = ArrayList<Row>()
        val o = try { names(ctx) } catch (e: Exception) { JSONObject() }
        val nfAll = match(arr(o, "nfFilms"), mv) + match(arr(o, "nfTv"), sr)
        if (nfAll.isNotEmpty()) rows.add(Row("🔥 Top 10 da Netflix nesta semana", nfAll))
        val itn = match(arr(o, "itunes"), mv)
        if (itn.isNotEmpty()) rows.add(Row("🔥 Filmes em alta no Brasil", itn))
        val web = match(arr(o, "web"), sr)
        if (web.isNotEmpty()) rows.add(Row("📺 Episódios novos hoje nos streamings", web))
        // lançamentos da própria lista
        val lanc = Regex("lan[çc]amento|estreia|novidade|recente|cinema|2026", RegexOption.IGNORE_CASE)
        val lm = lib.movies.filter { !it.adult && lanc.containsMatchIn(it.group) }.take(40)
        if (lm.isNotEmpty()) rows.add(Row("🆕 Lançamentos de filmes", lm))
        val ls = lib.series.filter { !it.adult && lanc.containsMatchIn(it.group) }.take(40)
        if (ls.isNotEmpty()) rows.add(Row("🆕 Lançamentos de séries", ls))
        return rows
    }
}

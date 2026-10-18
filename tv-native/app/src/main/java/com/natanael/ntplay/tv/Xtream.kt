package com.natanael.ntplay.tv

import android.net.Uri
import android.util.JsonReader
import android.util.JsonToken
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.InputStreamReader
import java.io.Writer

/**
 * Listas no formato Xtream Codes (get.php?username=..&password=..).
 * Em vez de baixar a M3U gigante (que muitos servidores não conseguem gerar a tempo
 * e devolvem HTTP 522/524 pelo Cloudflare), usamos a API player_api.php,
 * igual aos apps IPTV Smarters / TiviMate, e montamos a M3U localmente.
 */
object Xtream {
    class Creds(val base: String, val user: String, val pass: String) {
        fun api(action: String, extra: String = "") =
            "$base/player_api.php?username=${Net.enc(user)}&password=${Net.enc(pass)}&action=$action$extra"
    }

    const val SERIES_EXT = ".xs"

    fun creds(url: String): Creds? = try {
        val u = Uri.parse(url.trim())
        val user = u.getQueryParameter("username")
        val pass = u.getQueryParameter("password")
        val path = u.path ?: ""
        if (user.isNullOrEmpty() || pass.isNullOrEmpty() || !(path.endsWith("get.php") || path.endsWith("player_api.php"))) null
        else {
            val port = if (u.port > 0) ":" + u.port else ""
            Creds(u.scheme + "://" + u.host + port, user, pass)
        }
    } catch (e: Exception) { null }

    /** credenciais a partir de uma URL de stream do próprio servidor (…/series/user/pass/123.xs) */
    fun credsFromStream(url: String): Pair<Creds, String>? {
        val m = Regex("^(https?://[^/]+)/series/([^/]+)/([^/]+)/(\\d+)\\.xs$").find(url) ?: return null
        val g = m.groupValues
        return Creds(g[1], Uri.decode(g[2]), Uri.decode(g[3])) to g[4]
    }

    private fun open(url: String): JsonReader {
        val r = Net.client.newCall(Request.Builder().url(url).header("User-Agent", Util.UA).build()).execute()
        if (!r.isSuccessful) { val c = r.code; r.close(); throw RuntimeException("HTTP $c na API do servidor") }
        val body = r.body ?: run { r.close(); throw RuntimeException("API sem dados") }
        return JsonReader(InputStreamReader(body.byteStream(), Charsets.UTF_8))
    }

    private fun clean(s: String) = s.replace('"', '\'').replace('\n', ' ').replace('\r', ' ').trim()

    /** lê categorias: id -> nome */
    private fun cats(c: Creds, action: String): HashMap<String, String> {
        val m = HashMap<String, String>()
        try {
            open(c.api(action)).use { r ->
                if (r.peek() != JsonToken.BEGIN_ARRAY) { r.skipValue(); return m }
                r.beginArray()
                while (r.hasNext()) {
                    var id = ""; var name = ""
                    r.beginObject()
                    while (r.hasNext()) {
                        when (r.nextName()) {
                            "category_id" -> id = str(r)
                            "category_name" -> name = str(r)
                            else -> r.skipValue()
                        }
                    }
                    r.endObject()
                    if (id.isNotEmpty()) m[id] = name
                }
                r.endArray()
            }
        } catch (e: Exception) { }
        return m
    }

    private fun str(r: JsonReader): String = when (r.peek()) {
        JsonToken.NULL -> { r.nextNull(); "" }
        JsonToken.STRING, JsonToken.NUMBER -> r.nextString()
        JsonToken.BOOLEAN -> r.nextBoolean().toString()
        else -> { r.skipValue(); "" }
    }

    /** percorre um array de streams escrevendo linhas M3U; devolve quantos itens */
    private fun streams(c: Creds, action: String, cats: Map<String, String>, w: Writer, kind: Char): Int {
        var n = 0
        open(c.api(action)).use { r ->
            if (r.peek() != JsonToken.BEGIN_ARRAY) { r.skipValue(); return 0 }
            r.beginArray()
            while (r.hasNext()) {
                var name = ""; var id = ""; var icon = ""; var cat = ""; var ext = ""
                r.beginObject()
                while (r.hasNext()) {
                    when (r.nextName()) {
                        "name" -> name = str(r)
                        "stream_id", "series_id" -> id = str(r)
                        "stream_icon", "cover" -> icon = str(r)
                        "category_id" -> cat = str(r)
                        "container_extension" -> ext = str(r)
                        else -> r.skipValue()
                    }
                }
                r.endObject()
                if (id.isEmpty() || name.isEmpty()) continue
                val u = Net.enc(c.user); val p = Net.enc(c.pass)
                val link = when (kind) {
                    'l' -> "${c.base}/live/$u/$p/$id.ts"
                    'm' -> "${c.base}/movie/$u/$p/$id.${ext.ifEmpty { "mp4" }}"
                    else -> "${c.base}/series/$u/$p/$id$SERIES_EXT"
                }
                val g = cats[cat] ?: "Sem grupo"
                w.write("#EXTINF:-1 tvg-logo=\"${clean(icon)}\" group-title=\"${clean(g)}\",${clean(name)}\n$link\n")
                n++
                if (n % 2000 == 0) onCount?.invoke(n)
            }
            r.endArray()
        }
        return n
    }

    var onCount: ((Int) -> Unit)? = null

    class Account(val exp: Long, val status: String, val maxCon: Int, val active: Int)

    /** dados da conta (vencimento, status, conexões) */
    fun account(url: String): Account? {
        val c = creds(url) ?: return null
        val t = Net.getQuick(c.api("").removeSuffix("&action=")) ?: return null
        val ui = try { JSONObject(t).optJSONObject("user_info") } catch (e: Exception) { null } ?: return null
        val e = ui.optString("exp_date").toLongOrNull() ?: 0L
        return Account(e * 1000L, ui.optString("status"), ui.optString("max_connections").toIntOrNull() ?: 0, ui.optString("active_cons").toIntOrNull() ?: 0)
    }

    /** monta a M3U completa a partir da API. Lança exceção se a API não responder. */
    fun buildM3u(c: Creds, out: File, progress: (String) -> Unit): Int {
        // valida login
        val auth = Net.getText(c.api("").removeSuffix("&action=")) ?: throw RuntimeException("o servidor não respondeu à API (player_api.php)")
        val ui = try { JSONObject(auth).optJSONObject("user_info") } catch (e: Exception) { null }
        if (ui != null && ui.optInt("auth", 1) == 0) throw RuntimeException("usuário ou senha recusados pelo servidor")
        if (ui != null && ui.optString("status").isNotEmpty() && !ui.optString("status").equals("Active", true))
            throw RuntimeException("conta " + ui.optString("status") + " no servidor")
        val tmp = File(out.parentFile, out.name + ".part")
        var total = 0
        tmp.bufferedWriter(Charsets.UTF_8).use { w ->
            w.write("#EXTM3U\n")
            progress("Baixando canais…")
            onCount = { k -> progress("Canais: $k") }
            total += try { streams(c, "get_live_streams", cats(c, "get_live_categories"), w, 'l') } catch (e: Exception) { 0 }
            progress("Baixando filmes…")
            onCount = { k -> progress("Filmes: $k") }
            total += try { streams(c, "get_vod_streams", cats(c, "get_vod_categories"), w, 'm') } catch (e: Exception) { 0 }
            progress("Baixando séries…")
            onCount = { k -> progress("Séries: $k") }
            total += try { streams(c, "get_series", cats(c, "get_series_categories"), w, 's') } catch (e: Exception) { 0 }
            onCount = null
        }
        if (total == 0) { tmp.delete(); throw RuntimeException("a API do servidor não devolveu nenhum item") }
        out.delete()
        if (!tmp.renameTo(out)) throw RuntimeException("falha ao salvar a lista")
        return total
    }

    private val siCache = object : LinkedHashMap<String, String>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?) = size > 30
    }

    /** get_series_info com cache em memória (usado por episódios e sinopse) */
    fun seriesInfo(c: Creds, id: String): String? {
        val k = c.base + "|" + id
        synchronized(siCache) { siCache[k]?.let { return it } }
        var t: String? = null
        for (i in 0 until 2) { t = Net.getText(c.api("get_series_info", "&series_id=$id")); if (t != null) break }
        if (t != null && t.length > 20) synchronized(siCache) { siCache[k] = t }
        return t
    }

    /** episódios de uma série (carregados só quando a série é aberta) */
    fun episodes(url: String): List<Ep> {
        val (c, id) = credsFromStream(url) ?: return emptyList()
        val txt = seriesInfo(c, id) ?: throw RuntimeException("o servidor não respondeu")
        val o = JSONObject(txt)
        val eps = o.optJSONObject("episodes") ?: return emptyList()
        val out = ArrayList<Ep>()
        val u = Net.enc(c.user); val p = Net.enc(c.pass)
        for (k in eps.keys()) {
            val arr = eps.optJSONArray(k) ?: continue
            for (i in 0 until arr.length()) {
                val e = arr.optJSONObject(i) ?: continue
                val eid = e.optString("id"); if (eid.isEmpty()) continue
                val s = e.optInt("season", k.toIntOrNull() ?: 1)
                val n = e.optInt("episode_num", i + 1)
                val ext = e.optString("container_extension").ifEmpty { "mp4" }
                val t = e.optString("title").ifEmpty { "Episódio $n" }
                out.add(Ep(t, "${c.base}/series/$u/$p/$eid.$ext", s, n))
            }
        }
        out.sortWith(compareBy<Ep>({ it.s }, { it.e }))
        return out
    }

    private val streamRe = Regex("^(https?://[^/]+)/(movie|series)/([^/]+)/([^/]+)/(\\d+)\\.([A-Za-z0-9]+)$")

    private fun s(o: JSONObject, k: String): String { val v = o.optString(k); return if (v == "null") "" else v.trim() }

    /** capa, sinopse, nota e ano direto do servidor da lista (já em português na maioria) */
    fun info(item: Item): Info? {
        val url = item.eps.firstOrNull()?.url ?: return null
        val m = streamRe.find(url) ?: return null
        val g = m.groupValues
        val c = Creds(g[1], Uri.decode(g[3]), Uri.decode(g[4]))
        val txt = when {
            g[2] == "movie" -> Net.getQuick(c.api("get_vod_info", "&vod_id=" + g[5]))
            g[2] == "series" && g[6] == "xs" -> seriesInfo(c, g[5])
            else -> null
        } ?: return null
        val o = try { JSONObject(txt).optJSONObject("info") } catch (e: Exception) { null } ?: return null
        val img = s(o, "movie_image").ifEmpty { s(o, "cover_big") }.ifEmpty { s(o, "cover") }
        val ov = s(o, "plot").ifEmpty { s(o, "description") }
        val r = s(o, "rating").replace(',', '.').toDoubleOrNull() ?: 0.0
        val y = s(o, "releasedate").ifEmpty { s(o, "releaseDate") }.take(4).filter { it.isDigit() }
        if (img.isEmpty() && ov.isEmpty()) return null
        return Info(img, ov, if (r > 10) r / 10 else r, y, false, System.currentTimeMillis())
    }
}

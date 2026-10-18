package com.natanael.ntplay.tv

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Base64
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object Net {
    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .addInterceptor { c -> val r = c.request(); c.proceed(if (r.header("User-Agent") == null) r.newBuilder().header("User-Agent", Util.UA).build() else r) }
        .build()

    val img: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .addInterceptor { c ->
            val resp = c.proceed(c.request().newBuilder().header("User-Agent", "Mozilla/5.0 (Linux; Android 10; TV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36").build())
            val u = resp.request.url.toString()
            if (u.contains("removed.png") || (resp.isSuccessful && resp.body?.contentType()?.type == "text")) {
                resp.close()
                okhttp3.Response.Builder().request(resp.request).protocol(okhttp3.Protocol.HTTP_1_1).code(404).message("sem imagem")
                    .body("".toResponseBody(null)).build()
            } else resp
        }
        .build()

    val sse: OkHttpClient = client.newBuilder().readTimeout(0, TimeUnit.SECONDS).build()

    fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

    val quick: OkHttpClient by lazy { client.newBuilder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).build() }

    fun getQuick(url: String): String? = try {
        quick.newCall(Request.Builder().url(url).header("User-Agent", "Mozilla/5.0 (Linux; Android 10; TV) NTPlay").build()).execute().use { r -> if (r.isSuccessful) r.body?.string() else null }
    } catch (e: Exception) { null }

    fun getText(url: String): String? = try {
        client.newCall(Request.Builder().url(url).build()).execute().use { r -> if (r.isSuccessful) r.body?.string() else null }
    } catch (e: Exception) { null }

    /** baixa direto para arquivo (listas grandes não passam pela memória) */
    val UAS = listOf(Util.UA, "Mozilla/5.0 (Linux; Android 9; TV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/110.0 Safari/537.36", "okhttp/4.12.0", "")

    /** tenta varios User-Agents; devolve o motivo exato se todos falharem */
    fun downloadAny(url: String, out: File, onProgress: (Long) -> Unit = {}) {
        var last: Throwable? = null
        for (ua in UAS) {
            try { download(url, out, ua, onProgress); return } catch (e: Throwable) { last = e }
        }
        throw last ?: RuntimeException("falha desconhecida")
    }

    fun download(url: String, out: File, ua: String = Util.UA, onProgress: (Long) -> Unit = {}) {
        val tmp = File(out.parentFile, out.name + ".part")
        val rb = Request.Builder().url(url)
        rb.header("User-Agent", ua.ifEmpty { "NTPlay" })
        client.newCall(rb.build()).execute().use { r ->
            if (!r.isSuccessful) throw RuntimeException("HTTP " + r.code)
            val body = r.body ?: throw RuntimeException("sem dados")
            tmp.outputStream().use { o ->
                val buf = ByteArray(1 shl 16)
                var n: Long = 0
                body.byteStream().use { i ->
                    while (true) {
                        val k = i.read(buf)
                        if (k < 0) break
                        o.write(buf, 0, k)
                        n += k
                        onProgress(n)
                    }
                }
            }
        }
        out.delete()
        if (!tmp.renameTo(out)) throw RuntimeException("falha ao salvar a lista")
    }
}

object Meta {
    private val semBg = Semaphore(3)

    /** semelhança entre títulos (palavras em comum) */
    fun sim(a: String, b: String): Double {
        val x = Util.norm(a).split(' ').filter { it.isNotEmpty() }.toSet()
        val y = Util.norm(b).split(' ').filter { it.isNotEmpty() }.toSet()
        if (x.isEmpty() || y.isEmpty()) return 0.0
        val i = x.intersect(y).size.toDouble()
        val j = i / (x.size + y.size - i)
        val cover = i / minOf(x.size, y.size)
        return if (cover == 1.0 && minOf(x.size, y.size) >= 2) maxOf(j, 0.8) else j
    }

    private fun json(url: String): JSONObject? {
        val t = Net.getQuick(url) ?: return null
        return try { JSONObject(t) } catch (e: Exception) { null }
    }

    private fun wiki(lang: String, it: Item, t: String, y: String): Pair<String, String>? {
        val kind = if (lang == "pt") (if (it.kind == 's') " série" else " filme") else (if (it.kind == 's') " TV series" else " film")
        val q = t + kind + (if (y.isNotEmpty() && it.kind != 's') " $y" else "")
        val r = json("https://$lang.wikipedia.org/w/api.php?action=query&format=json&generator=search&gsrnamespace=0&gsrlimit=6&prop=pageimages|extracts&piprop=thumbnail&pithumbsize=500&exintro=1&explaintext=1&exlimit=max&exsentences=4&gsrsearch=" + Net.enc(q))
        val pages = r?.optJSONObject("query")?.optJSONObject("pages") ?: return null
        val list = ArrayList<JSONObject>()
        for (k in pages.keys()) list.add(pages.getJSONObject(k))
        list.sortBy { it.optInt("index") }
        for (p in list) {
            val title = p.optString("title").replace(Regex("\\s*\\(.*?\\)\\s*"), " ")
            val img = p.optJSONObject("thumbnail")?.optString("source") ?: ""
            val ex = p.optString("extract")
            if (sim(title, t) >= 0.6 && (img.isNotEmpty() || ex.length > 40)) return img to ex
        }
        return null
    }

    private fun stripH(s: String) = s.replace(Regex("<[^>]+>"), "").trim()

    private suspend fun gtx(c: String): String {
        val r = withContext(Dispatchers.IO) { Net.getQuick("https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=pt&dt=t&q=" + Net.enc(c)) }
        if (r != null) {
            try {
                val a = JSONArray(r).getJSONArray(0)
                val sb = StringBuilder()
                for (i in 0 until a.length()) sb.append(a.getJSONArray(i).optString(0))
                if (sb.isNotEmpty()) return sb.toString()
            } catch (e: Exception) { }
        }
        val m = withContext(Dispatchers.IO) { json("https://api.mymemory.translated.net/get?langpair=en|pt-BR&q=" + Net.enc(c.take(480))) }
        val o = m?.optJSONObject("responseData")?.optString("translatedText")
        if (!o.isNullOrEmpty() && !o.contains("MYMEMORY", true)) return o
        return c
    }

    suspend fun toPT(t: String): String {
        if (t.isBlank() || Util.looksPT(t)) return t
        val parts = Regex("[^.!?]+[.!?]*\\s*").findAll(t).map { it.value }.toList().ifEmpty { listOf(t) }
        val ch = ArrayList<String>()
        var cur = ""
        for (p in parts) { if ((cur + p).length > 450) { if (cur.isNotEmpty()) ch.add(cur); cur = p } else cur += p }
        if (cur.isNotEmpty()) ch.add(cur)
        val out = StringBuilder()
        for (c in ch) out.append(gtx(c)).append(' ')
        return out.toString().trim()
    }

    private fun ck(it: Item) = "3|" + it.key
    fun cached(store: Store, it: Item): Info? = store.info(ck(it))

    /** priority = tela de detalhes (não espera a fila das capas) */
    suspend fun get(store: Store, it: Item, priority: Boolean = false): Info? {
        if (it.kind == 'l' || it.adult) return null
        val c = store.info(ck(it))
        if (c != null && (!c.none || System.currentTimeMillis() - c.ts < 3 * 3600_000L)) {
            if (c.ov.isNotEmpty() && !Util.looksPT(c.ov)) {
                val x = toPT(c.ov)
                if (x != c.ov) { val n = Info(c.img, x, c.r, c.y, false, c.ts); store.putInfo(ck(it), n); return n }
            }
            if (!priority || c.ov.isNotEmpty() || c.none) return c
        }
        return if (priority) fetch(store, it) else semBg.withPermit { fetch(store, it) }
    }

    private suspend fun fetch(store: Store, it: Item): Info = withContext(Dispatchers.IO) {
        val t = Util.cleanT(it.title).replace(Regex("\\b(19|20)\\d{2}\\b"), " ").replace(Regex("\\s+"), " ").trim()
        val y = Util.yearOf(it.title)
        var img = ""; var ov = ""; var r = 0.0; var yr = y
        fun take(i: String, o: String) { if (img.isEmpty() && i.isNotEmpty()) img = i; if (ov.isEmpty() && o.length > 30) ov = o }
        try { Xtream.info(it)?.let { x -> take(x.img, x.ov); r = x.r; if (x.y.isNotEmpty()) yr = x.y } } catch (e: Exception) { }
        if (ov.isEmpty() || img.isEmpty()) try {
            if (it.kind == 's') {
                val a = Net.getQuick("https://api.tvmaze.com/search/shows?q=" + Net.enc(t))?.let { s -> JSONArray(s) }
                if (a != null) {
                    var best: JSONObject? = null; var bs = 0.0
                    for (i in 0 until a.length()) {
                        val sh = a.getJSONObject(i).optJSONObject("show") ?: continue
                        val s = maxOf(sim(sh.optString("name"), t), sim(sh.optString("name"), it.title))
                        if (s > bs) { bs = s; best = sh }
                    }
                    if (best != null && bs >= 0.6) {
                        take(best.optJSONObject("image")?.let { i -> i.optString("original").ifEmpty { i.optString("medium") } }?.replace("http:", "https:") ?: "", stripH(best.optString("summary")))
                        if (r == 0.0) r = best.optJSONObject("rating")?.optDouble("average", 0.0) ?: 0.0
                        if (yr.isEmpty()) yr = best.optString("premiered").take(4)
                    }
                }
            }
        } catch (e: Exception) { }
        if (ov.isEmpty() || img.isEmpty()) try {
            val media = if (it.kind == 's') "media=tvShow&entity=tvSeason" else "media=movie"
            val x = json("https://itunes.apple.com/search?$media&country=br&lang=pt_br&limit=8&term=" + Net.enc(t))?.optJSONArray("results")
            if (x != null) for (i in 0 until x.length()) {
                val o = x.getJSONObject(i)
                val nm = if (it.kind == 's') o.optString("artistName").ifEmpty { o.optString("collectionName").replace(Regex(",? (Season|Temporada) \\d+.*", RegexOption.IGNORE_CASE), "") } else o.optString("trackName")
                if (sim(nm, t) >= 0.6) {
                    take(o.optString("artworkUrl100").replace("100x100bb", "600x900bb"), o.optString("longDescription").ifEmpty { o.optString("description") }.ifEmpty { o.optString("shortDescription") })
                    if (yr.isEmpty()) yr = o.optString("releaseDate").take(4)
                    break
                }
            }
        } catch (e: Exception) { }
        if (ov.isEmpty() || img.isEmpty()) try { wiki("pt", it, t, y)?.let { w -> take(w.first, w.second) } } catch (e: Exception) { }
        if (ov.isEmpty() || img.isEmpty()) try { wiki("en", it, t, y)?.let { w -> take(w.first, w.second) } } catch (e: Exception) { }
        if (ov.length > 900) ov = ov.take(900)
        if (ov.isNotEmpty()) ov = toPT(ov)
        val res = if (img.isEmpty() && ov.isEmpty()) Info("", "", r, yr, true, System.currentTimeMillis())
        else Info(img, ov, r, yr, false, System.currentTimeMillis())
        store.putInfo(ck(it), res)
        res
    }
}

object Relay {
    /** Escuta mensagens enviadas pelo celular (QR code). Devolve texto já decifrado (URL ou lista). */
    suspend fun listen(topic: String, key: String, onMsg: (String, String?) -> Unit) = withContext(Dispatchers.IO) {
        val job = coroutineContext[Job]!!
        val seen = HashSet<String>()
        while (isActive) {
            val call = Net.sse.newCall(Request.Builder().url("https://ntfy.sh/$topic/sse").build())
            val h = job.invokeOnCompletion { call.cancel() }
            try {
                call.execute().use { r ->
                    val src = r.body!!.source()
                    while (isActive && !src.exhausted()) {
                        val line = src.readUtf8Line() ?: break
                        if (!line.startsWith("data:")) continue
                        try {
                            val o = JSONObject(line.substring(5).trim())
                            if (o.optString("event") != "message") continue
                            val id = o.optString("id")
                            if (!seen.add(id)) continue
                            val m = o.optString("message").trim()
                            val dec = decrypt(m, key)
                            if (dec != null) {
                                val j = JSONObject(dec)
                                if (j.has("u")) onMsg(j.getString("u"), j.optString("n").ifEmpty { null })
                                else if (j.has("t")) onMsg(j.getString("t"), j.optString("n").ifEmpty { null })
                            } else {
                                val u = Regex("https?://\\S+").find(m)
                                if (m.contains("#EXTM3U") || m.contains("#EXTINF")) onMsg(m, null)
                                else if (u != null) onMsg(u.value, null)
                            }
                        } catch (e: CancellationException) { throw e } catch (e: Exception) { }
                    }
                }
            } catch (e: CancellationException) { throw e } catch (e: Exception) { }
            finally { h.dispose() }
            if (isActive) delay(3000)
        }
    }

    private fun decrypt(b64: String, key: String): String? = try {
        val all = Base64.decode(b64, Base64.URL_SAFE)
        val k = Base64.decode(key, Base64.URL_SAFE)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, SecretKeySpec(k, "AES"), GCMParameterSpec(128, all.copyOfRange(0, 12)))
        String(c.doFinal(all, 12, all.size - 12), Charsets.UTF_8)
    } catch (e: Exception) { null }
}

object Qr {
    fun bitmap(text: String, size: Int = 360): Bitmap {
        val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, mapOf(EncodeHintType.MARGIN to 1))
        val px = IntArray(size * size)
        for (y in 0 until size) for (x in 0 until size) px[y * size + x] = if (m.get(x, y)) Color.BLACK else Color.WHITE
        return Bitmap.createBitmap(px, size, size, Bitmap.Config.ARGB_8888)
    }
}

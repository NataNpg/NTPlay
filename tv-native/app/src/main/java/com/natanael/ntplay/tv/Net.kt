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

    val sse: OkHttpClient = client.newBuilder().readTimeout(0, TimeUnit.SECONDS).build()

    fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

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
    private val sem = Semaphore(2)

    private fun okT(a: String, b: String): Boolean {
        val x = Util.norm(a)
        val y = Util.norm(b)
        return x.isNotEmpty() && y.isNotEmpty() && (x == y || (x.length > 3 && y.length > 3 && (x.startsWith("$y ") || y.startsWith("$x "))))
    }

    private fun json(url: String): JSONObject? {
        val t = Net.getText(url) ?: return null
        return try { JSONObject(t) } catch (e: Exception) { null }
    }

    private fun wiki(it: Item, t: String, y: String): Pair<String, String>? {
        val q = t + (if (it.kind == 's') " série de televisão" else " filme" + (if (y.isNotEmpty()) " $y" else ""))
        val r = json("https://pt.wikipedia.org/w/api.php?action=query&format=json&generator=search&gsrnamespace=0&gsrlimit=5&prop=pageimages|extracts&piprop=thumbnail&pithumbsize=500&exintro=1&explaintext=1&exlimit=max&exsentences=4&gsrsearch=" + Net.enc(q))
        val pages = r?.optJSONObject("query")?.optJSONObject("pages") ?: return null
        val list = ArrayList<JSONObject>()
        for (k in pages.keys()) list.add(pages.getJSONObject(k))
        list.sortBy { it.optInt("index") }
        for (p in list) {
            val title = p.optString("title").replace(Regex("\\s*\\(.*?\\)\\s*"), " ")
            val img = p.optJSONObject("thumbnail")?.optString("source") ?: ""
            val ex = p.optString("extract")
            if (okT(title, t) && (img.isNotEmpty() || ex.isNotEmpty())) return img to ex
        }
        return null
    }

    private fun stripH(s: String) = s.replace(Regex("<[^>]+>"), "").trim()

    private suspend fun gtx(c: String): String {
        val r = withContext(Dispatchers.IO) { Net.getText("https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=pt&dt=t&q=" + Net.enc(c)) }
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

    suspend fun get(store: Store, it: Item): Info? {
        if (it.kind == 'l' || it.adult) return null
        val c = store.info(it.key)
        if (c != null && (!c.none || System.currentTimeMillis() - c.ts < 6 * 3600_000L)) {
            if (c.ov.isNotEmpty() && !Util.looksPT(c.ov)) {
                val x = toPT(c.ov)
                if (x != c.ov) { val n = Info(c.img, x, c.r, c.y, false, c.ts); store.putInfo(it.key, n); return n }
            }
            return c
        }
        return sem.withPermit {
            withContext(Dispatchers.IO) {
                val t = Util.cleanT(it.title)
                val y = Util.yearOf(it.title)
                var img = ""; var ov = ""; var r = 0.0; var yr = y
                try { wiki(it, t, y)?.let { w -> img = w.first; ov = w.second } } catch (e: Exception) { }
                try {
                    if (it.kind == 's') {
                        val x = json("https://api.tvmaze.com/singlesearch/shows?q=" + Net.enc(t))
                        if (x != null && okT(x.optString("name"), t)) {
                            if (img.isEmpty()) img = x.optJSONObject("image")?.let { i -> i.optString("original").ifEmpty { i.optString("medium") } }?.replace("http:", "https:") ?: ""
                            if (ov.isEmpty()) ov = stripH(x.optString("summary"))
                            r = x.optJSONObject("rating")?.optDouble("average", 0.0) ?: 0.0
                            if (yr.isEmpty()) yr = x.optString("premiered").take(4)
                        }
                    } else {
                        val x = json("https://itunes.apple.com/search?media=movie&country=br&limit=5&term=" + Net.enc(t))?.optJSONArray("results")
                        if (x != null) for (i in 0 until x.length()) {
                            val o = x.getJSONObject(i)
                            if (okT(o.optString("trackName"), t)) {
                                if (img.isEmpty()) img = o.optString("artworkUrl100").replace("100x100bb", "600x900bb")
                                if (ov.isEmpty()) ov = o.optString("longDescription").ifEmpty { o.optString("shortDescription") }
                                if (yr.isEmpty()) yr = o.optString("releaseDate").take(4)
                                break
                            }
                        }
                    }
                } catch (e: Exception) { }
                if (ov.length > 800) ov = ov.take(800)
                if (ov.isNotEmpty()) ov = toPT(ov)
                val res = if (img.isEmpty() && ov.isEmpty()) Info("", "", 0.0, "", true, System.currentTimeMillis())
                else Info(img, ov, r, yr, false, System.currentTimeMillis())
                store.putInfo(it.key, res)
                res
            }
        }
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

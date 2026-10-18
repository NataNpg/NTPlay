package com.natanael.ntplay.tv

import android.content.Context
import android.util.JsonReader
import android.util.JsonToken
import okhttp3.Request
import java.io.File
import java.io.InputStreamReader
import java.text.Normalizer

/**
 * Logos de canais que a lista não traz: usa a base pública do iptv-org
 * (channels.json + logos.json), baixada uma vez e guardada em cache (30 dias).
 */
object Logos {
    private const val CH = "https://iptv-org.github.io/api/channels.json"
    private const val LG = "https://iptv-org.github.io/api/logos.json"
    @Volatile private var map: HashMap<String, String>? = null
    @Volatile private var loading = false

    private val tags = Regex("\\b(fhd|hd|sd|uhd|4k|8k|fhdr|hdr|h\\.?265|hevc|h\\.?264|leg|legendado|dub|dublado|alt|backup|br|bra|brasil|24h|24 horas|1080p|720p|60fps|50fps|tv)\\b")
    private val brk = Regex("\\[.*?\\]|\\(.*?\\)|\\{.*?\\}")
    private val diac = Regex("\\p{InCombiningDiacriticalMarks}+")

    fun key(name: String): String {
        var t = Normalizer.normalize(name.lowercase(), Normalizer.Form.NFD)
        t = diac.replace(t, "")
        t = brk.replace(t, " ")
        t = t.replace("+", " plus ").replace("&", " e ")
        t = tags.replace(t, " ")
        return t.replace(Regex("[^a-z0-9]+"), "")
    }

    fun find(name: String): String? {
        val m = map ?: return null
        val k = key(name)
        if (k.length < 2) return null
        return m[k] ?: m[k.removePrefix("canal")] ?: m["canal$k"]
    }

    val ready: Boolean get() = map != null

    /** carrega do cache ou baixa; chame em background */
    fun ensure(ctx: Context): Boolean {
        if (map != null) return true
        if (loading) return false
        loading = true
        val prio = android.os.Process.getThreadPriority(android.os.Process.myTid())
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_LOWEST)
        try {
            val f = File(ctx.filesDir, "logos.tsv")
            if (f.exists() && System.currentTimeMillis() - f.lastModified() < 30L * 86400_000L) {
                map = read(f); return true
            }
            val ids = HashMap<String, Pair<String, Boolean>>() // key -> (id, isBR)
            stream(CH) { r ->
                r.beginArray()
                while (r.hasNext()) {
                    var id = ""; var name = ""; var country = ""; val alts = ArrayList<String>()
                    r.beginObject()
                    while (r.hasNext()) {
                        when (r.nextName()) {
                            "id" -> id = str(r)
                            "name" -> name = str(r)
                            "country" -> country = str(r)
                            "alt_names" -> if (r.peek() == JsonToken.BEGIN_ARRAY) { r.beginArray(); while (r.hasNext()) alts.add(str(r)); r.endArray() } else r.skipValue()
                            else -> r.skipValue()
                        }
                    }
                    r.endObject()
                    val br = country == "BR"
                    for (n in listOf(name) + alts) {
                        val k = key(n); if (k.length < 2) continue
                        val o = ids[k]
                        if (o == null || (br && !o.second)) ids[k] = id to br
                    }
                }
                r.endArray()
            }
            val logo = HashMap<String, String>() // id -> url
            stream(LG) { r ->
                r.beginArray()
                while (r.hasNext()) {
                    var ch = ""; var url = ""; var feed = ""
                    r.beginObject()
                    while (r.hasNext()) {
                        when (r.nextName()) {
                            "channel" -> ch = str(r)
                            "url" -> url = str(r)
                            "feed" -> feed = str(r)
                            else -> r.skipValue()
                        }
                    }
                    r.endObject()
                    if (ch.isNotEmpty() && url.isNotEmpty() && (feed.isEmpty() || !logo.containsKey(ch))) logo[ch] = url
                }
                r.endArray()
            }
            val out = HashMap<String, String>(ids.size)
            for ((k, v) in ids) logo[v.first]?.let { out[k] = it }
            f.bufferedWriter().use { w -> for ((k, v) in out) w.write("$k\t$v\n") }
            map = out
            return true
        } catch (e: Throwable) {
            return false
        } finally { loading = false; try { android.os.Process.setThreadPriority(prio) } catch (e: Exception) { } }
    }

    private fun read(f: File): HashMap<String, String> {
        val m = HashMap<String, String>()
        f.forEachLine { l -> val i = l.indexOf('\t'); if (i > 0) m[l.substring(0, i)] = l.substring(i + 1) }
        return m
    }

    private fun str(r: JsonReader): String = when (r.peek()) {
        JsonToken.NULL -> { r.nextNull(); "" }
        JsonToken.STRING, JsonToken.NUMBER -> r.nextString()
        else -> { r.skipValue(); "" }
    }

    private fun stream(url: String, body: (JsonReader) -> Unit) {
        Net.img.newCall(Request.Builder().url(url).build()).execute().use { resp ->
            if (!resp.isSuccessful) throw RuntimeException("HTTP " + resp.code)
            JsonReader(InputStreamReader(resp.body!!.byteStream(), Charsets.UTF_8)).use(body)
        }
    }
}

package com.natanael.ntplay.tv

import android.util.Xml
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.util.zip.GZIPInputStream

/** Guia de programação (XMLTV): baixa o guia do provedor (Xtream: xmltv.php; M3U: url-tvg) e guarda só os canais da lista. */
object Epg {
    class P(val title: String, val start: Long, val stop: Long)

    @Volatile private var byId: Map<String, List<P>> = emptyMap()
    @Volatile private var byName: Map<String, List<P>> = emptyMap()
    @Volatile var ts = 0L
    @Volatile var loading = false
    @Volatile var count = 0

    fun programs(it: Item): List<P> {
        if (it.tvgId.isNotEmpty()) byId[it.tvgId]?.let { l -> return l }
        return byName[it.norm] ?: emptyList()
    }

    fun nowNext(it: Item): Pair<P?, P?> {
        val l = programs(it); if (l.isEmpty()) return null to null
        val n = System.currentTimeMillis()
        val cur = l.firstOrNull { p -> p.start <= n && p.stop > n }
        val nxt = l.firstOrNull { p -> p.start >= (cur?.stop ?: n) && p.start > n - 1 && p !== cur }
        return cur to nxt
    }

    fun nowTitle(it: Item): String? = nowNext(it).first?.title

    private val urlAttr = Regex("(?:url-tvg|x-tvg-url)=\"([^\"]+)\"", RegexOption.IGNORE_CASE)

    fun sources(store: Store): List<String> {
        val out = LinkedHashSet<String>()
        for (m in store.lists()) {
            if (!m.active || m.url.isEmpty()) continue
            val c = Xtream.creds(m.url)
            if (c != null) { out.add("${c.base}/xmltv.php?username=${Net.enc(c.user)}&password=${Net.enc(c.pass)}"); continue }
            try {
                val f = File(store.file(m.id).path)
                if (f.exists()) {
                    val head = f.bufferedReader().use { it.readLine() ?: "" }
                    urlAttr.find(head)?.groupValues?.get(1)?.split(',')?.map { it.trim() }?.filter { it.startsWith("http") }?.forEach { out.add(it) }
                }
            } catch (e: Throwable) { }
        }
        return out.toList()
    }

    private fun tsOf(s: String): Long {
        // "20261027120000 +0000"
        if (s.length < 14) return 0
        return try {
            val y = s.substring(0, 4).toInt(); val mo = s.substring(4, 6).toInt(); val d = s.substring(6, 8).toInt()
            val h = s.substring(8, 10).toInt(); val mi = s.substring(10, 12).toInt(); val se = s.substring(12, 14).toInt()
            var off = 0
            val z = s.substring(14).trim()
            if (z.length >= 5 && (z[0] == '+' || z[0] == '-')) off = (z.substring(1, 3).toInt() * 3600 + z.substring(3, 5).toInt() * 60) * (if (z[0] == '-') -1 else 1)
            java.time.LocalDateTime.of(y, mo, d, h, mi, se).toEpochSecond(java.time.ZoneOffset.ofTotalSeconds(off)) * 1000
        } catch (e: Throwable) { 0 }
    }

    private fun open(url: String): InputStream? {
        val r = Net.client.newBuilder().readTimeout(90, java.util.concurrent.TimeUnit.SECONDS).build()
            .newCall(Request.Builder().url(url).header("User-Agent", Util.UA).build()).execute()
        if (!r.isSuccessful) { r.close(); return null }
        val b = BufferedInputStream(r.body?.byteStream() ?: return null, 1 shl 16)
        b.mark(2); val a = b.read(); val c = b.read(); b.reset()
        return if (a == 0x1f && c == 0x8b) GZIPInputStream(b, 1 shl 16) else b
    }

    /** lê o XMLTV de uma fonte e acrescenta os programas dos canais que existem na lista */
    private fun parse(ins: InputStream, wantIds: Set<String>, wantNames: Set<String>, ids: HashMap<String, ArrayList<P>>, names: HashMap<String, ArrayList<P>>) {
        val p = Xml.newPullParser()
        p.setInput(ins, null)
        val now = System.currentTimeMillis()
        val lo = now - 2 * 3600_000L; val hi = now + 16 * 3600_000L
        val keep = HashSet<String>()                  // ids de canal que interessam
        val idNames = HashMap<String, ArrayList<String>>()
        var curCh = ""; var inDn = false
        var pc = ""; var ps = 0L; var pe = 0L; var inTitle = false; var title = ""
        var ev = p.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            when (ev) {
                XmlPullParser.START_TAG -> when (p.name) {
                    "channel" -> { curCh = p.getAttributeValue(null, "id") ?: ""; if (curCh in wantIds) keep.add(curCh) }
                    "display-name" -> inDn = true
                    "programme" -> {
                        pc = p.getAttributeValue(null, "channel") ?: ""
                        ps = tsOf(p.getAttributeValue(null, "start") ?: ""); pe = tsOf(p.getAttributeValue(null, "stop") ?: "")
                        title = ""
                    }
                    "title" -> inTitle = true
                }
                XmlPullParser.TEXT -> {
                    if (inDn && curCh.isNotEmpty()) {
                        val n = Util.norm(p.text.trim())
                        if (n.isNotEmpty() && n in wantNames) { keep.add(curCh); idNames.getOrPut(curCh) { ArrayList() }.add(n) }
                    } else if (inTitle && title.isEmpty()) title = p.text.trim()
                }
                XmlPullParser.END_TAG -> when (p.name) {
                    "display-name" -> inDn = false
                    "title" -> inTitle = false
                    "channel" -> curCh = ""
                    "programme" -> if (pc in keep && pe > lo && ps < hi && title.isNotEmpty()) {
                        val pr = P(title, ps, pe)
                        val l = ids.getOrPut(pc) { ArrayList() }
                        if (l.size < 14) l.add(pr)
                        idNames[pc]?.let { for (n in it) { val nl = names.getOrPut(n) { ArrayList() }; if (nl.size < 14) nl.add(pr) } }
                        count++
                    }
                }
            }
            ev = p.next()
        }
    }

    suspend fun load(store: Store, lib: Library, force: Boolean = false) {
        if (loading) return
        if (!force && ts > 0 && System.currentTimeMillis() - ts < 3 * 3600_000L) return
        val src = sources(store); if (src.isEmpty()) return
        loading = true
        try {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val wantIds = HashSet<String>(); val wantNames = HashSet<String>()
                for (c in lib.live) { if (c.tvgId.isNotEmpty()) wantIds.add(c.tvgId); wantNames.add(c.norm) }
                val ids = HashMap<String, ArrayList<P>>(); val names = HashMap<String, ArrayList<P>>()
                count = 0
                for (u in src) try { open(u)?.use { parse(it, wantIds, wantNames, ids, names) } } catch (e: Throwable) { }
                for (l in ids.values) l.sortBy { it.start }
                for (l in names.values) l.sortBy { it.start }
                if (ids.isNotEmpty() || names.isNotEmpty()) { byId = ids; byName = names; ts = System.currentTimeMillis() }
            }
        } finally { loading = false }
    }
}

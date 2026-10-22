package com.natanael.ntplay.tv

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.SecureRandom
import android.util.Base64

class ListMeta(val id: String, var name: String, val url: String, var count: Int, var date: Long, var active: Boolean)
class Prog(var t: Long, var d: Long, var ts: Long, var done: Boolean, var k: String, var title: String)
class Info(val img: String, val ov: String, val r: Double, val y: String, val none: Boolean, val ts: Long)

class Store(ctx: Context) {
    private val sp = ctx.getSharedPreferences("ntplay", Context.MODE_PRIVATE)
    private val dir = File(ctx.filesDir, "lists").also { it.mkdirs() }
    private val infoFile = File(ctx.filesDir, "info.json")
    private var infoMap: HashMap<String, Info>? = null

    fun file(id: String) = File(dir, "$id.m3u")

    fun lists(): MutableList<ListMeta> {
        val out = ArrayList<ListMeta>()
        try {
            val a = JSONArray(sp.getString("lists", "[]"))
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                out.add(ListMeta(o.getString("id"), o.getString("name"), o.optString("url"), o.optInt("count"), o.optLong("date"), o.optBoolean("active", true)))
            }
        } catch (e: Exception) { }
        return out
    }

    fun saveLists(l: List<ListMeta>) {
        val a = JSONArray()
        for (m in l) a.put(JSONObject().put("id", m.id).put("name", m.name).put("url", m.url).put("count", m.count).put("date", m.date).put("active", m.active))
        sp.edit().putString("lists", a.toString()).apply()
    }

    fun deleteList(id: String) { file(id).delete() }

    var favs: Set<String>
        get() = HashSet(sp.getStringSet("favs", emptySet()) ?: emptySet())
        set(v) { sp.edit().putStringSet("favs", HashSet(v)).apply() }

    var adult: Boolean
        get() = sp.getBoolean("adult", false)
        set(v) { sp.edit().putBoolean("adult", v).apply() }

    var tab: String
        get() = sp.getString("tab", "RECOMENDAÇÕES") ?: "RECOMENDAÇÕES"
        set(v) { sp.edit().putString("tab", v).apply() }

    var subs: Boolean
        get() = sp.getBoolean("subs", false)
        set(v) { sp.edit().putBoolean("subs", v).apply() }

    var skipIntro: Int
        get() = sp.getInt("skipintro", 85)
        set(v) { sp.edit().putInt("skipintro", v).apply() }

    /** vencimento da lista: "exp|status|max|ativos|quando" */
    fun account(id: String): String = sp.getString("acc_$id", "") ?: ""
    fun setAccount(id: String, v: String) { sp.edit().putString("acc_$id", v).apply() }

    var autoNext: Boolean
        get() = sp.getBoolean("autonext", true)
        set(v) { sp.edit().putBoolean("autonext", v).apply() }

    // 0 = padrão, 1 = grande (~25 s), 2 = muito grande (~45 s) — buffer de filmes/séries
    var bufferMode: Int
        get() = sp.getInt("buffermode", 0)
        set(v) { sp.edit().putInt("buffermode", v).apply() }

    // decodifica nomes/categorias Xtream em Base64 (só quando o provedor os guarda assim)
    var base64: Boolean
        get() = sp.getBoolean("b64names", false)
        set(v) { sp.edit().putBoolean("b64names", v).apply() }

    // detectar conteúdo adulto pelo nome da categoria (quando o provedor não marca a flag)
    var adultByName: Boolean
        get() = sp.getBoolean("adultbyname", true)
        set(v) { sp.edit().putBoolean("adultbyname", v).apply() }

    // grupos personalizados do usuário: JSON { "nome": ["key1","key2"], ... }
    var groupsRaw: String
        get() = sp.getString("groups", "{}") ?: "{}"
        set(v) { sp.edit().putString("groups", v).apply() }

    // atualização automática das listas
    var updateHours: Int
        get() = sp.getInt("updhours", 0) // 0 = desligado; senão 6/12/24
        set(v) { sp.edit().putInt("updhours", v).apply() }
    var updateOnStart: Boolean
        get() = sp.getBoolean("updstart", false)
        set(v) { sp.edit().putBoolean("updstart", v).apply() }

    fun relay(): Pair<String, String> {
        var t = sp.getString("rt", null)
        var k = sp.getString("rk", null)
        if (t == null || k == null) {
            val r = SecureRandom()
            val a = ByteArray(12); r.nextBytes(a)
            val b = ByteArray(16); r.nextBytes(b)
            t = "ntplay-" + a.joinToString("") { "%02x".format(it) }
            k = Base64.encodeToString(b, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
            sp.edit().putString("rt", t).putString("rk", k).apply()
        }
        return t!! to k!!
    }

    // progresso por URL do episódio/filme
    fun progress(): HashMap<String, Prog> {
        val m = HashMap<String, Prog>()
        try {
            val o = JSONObject(sp.getString("prog", "{}"))
            for (u in o.keys()) {
                val p = o.getJSONObject(u)
                m[u] = Prog(p.optLong("t"), p.optLong("d"), p.optLong("ts"), p.optBoolean("done"), p.optString("k"), p.optString("n"))
            }
        } catch (e: Exception) { }
        return m
    }

    fun saveProgress(m: Map<String, Prog>) {
        var e = m.entries.toList()
        if (e.size > 600) e = e.sortedByDescending { it.value.ts }.take(500)
        val o = JSONObject()
        for ((u, p) in e) o.put(u, JSONObject().put("t", p.t).put("d", p.d).put("ts", p.ts).put("done", p.done).put("k", p.k).put("n", p.title))
        sp.edit().putString("prog", o.toString()).apply()
    }

    @Synchronized
    fun info(key: String): Info? {
        if (infoMap == null) {
            val m = HashMap<String, Info>()
            try {
                if (infoFile.exists()) {
                    val o = JSONObject(infoFile.readText())
                    for (k in o.keys()) {
                        val p = o.getJSONObject(k)
                        m[k] = Info(p.optString("img"), p.optString("ov"), p.optDouble("r", 0.0), p.optString("y"), p.optBoolean("none"), p.optLong("ts"))
                    }
                }
            } catch (e: Exception) { }
            infoMap = m
        }
        return infoMap!![key]
    }

    @Volatile private var saveScheduled = false

    @Synchronized
    fun putInfo(key: String, i: Info) {
        info(key)
        infoMap!![key] = i
        if (!saveScheduled) {
            saveScheduled = true
            Thread {
                try { Thread.sleep(4000) } catch (e: InterruptedException) { }
                saveInfo()
            }.start()
        }
    }

    private fun saveInfo() {
        val e: List<Map.Entry<String, Info>>
        synchronized(this) {
            saveScheduled = false
            var l = infoMap!!.entries.toList()
            if (l.size > 4000) {
                l = l.sortedByDescending { it.value.ts }.take(3500)
                val keep = l.map { it.key }.toHashSet()
                infoMap!!.keys.retainAll(keep)
            }
            e = l.map { java.util.AbstractMap.SimpleEntry(it.key, it.value) }
        }
        try {
            val o = JSONObject()
            for ((k, v) in e) o.put(k, JSONObject().put("img", v.img).put("ov", v.ov).put("r", v.r).put("y", v.y).put("none", v.none).put("ts", v.ts))
            val tmp = File(infoFile.parentFile, "info.json.tmp")
            tmp.writeText(o.toString())
            tmp.renameTo(infoFile)
        } catch (x: Exception) { }
    }
}

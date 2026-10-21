package com.natanael.ntplay.tv

import android.content.Context
import android.os.Build
import android.util.Base64
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPublicKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Presença: avisa ao painel do desenvolvedor (admin.html) que esta TV está usando o NTPlay.
 * Criptografado (ECDH P-256 + AES-GCM, mesmo formato "ntp1." do app web). Envia nome do aparelho,
 * versão, se está assistindo e as listas (nome, servidor, itens, vencimento, telas) — nunca usuário/senha.
 */
object Presence {
    private const val PUB = "BHI9Apeb-q1NfFyzK1M50pJL7IWEGH0pJbwT0FXFtTD6B4986Gp-qjGP0IeKLIEMFH_UfcvuDGTL3GqAs3k8g4A"
    private const val TOPIC = "ntplay-pres-381affd473dafb5cac"

    private fun b64(b: ByteArray) = Base64.encodeToString(b, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    private fun unb64(s: String) = Base64.decode(s, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    private fun fixed(x: BigInteger): ByteArray {
        val b = x.toByteArray()
        return when {
            b.size == 32 -> b
            b.size > 32 -> b.copyOfRange(b.size - 32, b.size)
            else -> ByteArray(32 - b.size) + b
        }
    }

    fun seal(json: String): String {
        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(ECGenParameterSpec("secp256r1"))
        val eph = kpg.generateKeyPair()
        val ephPub = eph.public as ECPublicKey
        val raw = unb64(PUB)
        val pub = KeyFactory.getInstance("EC").generatePublic(
            ECPublicKeySpec(ECPoint(BigInteger(1, raw.copyOfRange(1, 33)), BigInteger(1, raw.copyOfRange(33, 65))), ephPub.params)
        )
        val ka = KeyAgreement.getInstance("ECDH")
        ka.init(eph.private)
        ka.doPhase(pub, true)
        val key = MessageDigest.getInstance("SHA-256").digest(ka.generateSecret())
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        val ct = c.doFinal(json.toByteArray(Charsets.UTF_8))
        val epRaw = byteArrayOf(4) + fixed(ephPub.w.affineX) + fixed(ephPub.w.affineY)
        return "ntp1." + b64(epRaw) + "." + b64(iv) + "." + b64(ct)
    }

    private fun deviceId(ctx: Context): String {
        val sp = ctx.getSharedPreferences("ntplay", Context.MODE_PRIVATE)
        var id = sp.getString("dev_id", "") ?: ""
        if (id.isEmpty()) {
            val r = ByteArray(8).also { SecureRandom().nextBytes(it) }
            id = "t" + r.joinToString("") { "%02x".format(it) }
            sp.edit().putString("dev_id", id).apply()
        }
        return id
    }

    fun enabled(ctx: Context) = ctx.getSharedPreferences("ntplay", Context.MODE_PRIVATE).getBoolean("presence", true)
    fun setEnabled(ctx: Context, v: Boolean) { ctx.getSharedPreferences("ntplay", Context.MODE_PRIVATE).edit().putBoolean("presence", v).apply() }

    /** monta e envia (chamar fora da thread principal) */
    fun send(ctx: Context, st: AppState) {
        if (!enabled(ctx)) return
        try {
            val ls = JSONArray()
            for (m in st.store.lists().take(12)) {
                val a = st.store.account(m.id).split('|')
                val host = try { android.net.Uri.parse(m.url).host ?: "arquivo" } catch (e: Exception) { "arquivo" }
                ls.put(JSONObject().apply {
                    put("n", m.name); put("h", if (m.url.isEmpty()) "arquivo" else host); put("c", m.count); put("on", if (m.active) 1 else 0)
                    if (a.size >= 4) { put("e", a[0].toLongOrNull() ?: 0L); put("s", a[1]); put("m", a[2].toIntOrNull() ?: 0); put("a", a[3].toIntOrNull() ?: 0) }
                })
            }
            val p = st.play
            val ver = try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "" } catch (e: Exception) { "" }
            val o = JSONObject().apply {
                put("id", deviceId(ctx)); put("nm", ""); put("p", "tv"); put("v", "tv-" + ver)
                put("os", "Android " + Build.VERSION.RELEASE + " • " + Build.MANUFACTURER + " " + Build.MODEL)
                put("t", System.currentTimeMillis()); put("off", 0)
                if (p != null) put("w", JSONObject().apply { put("k", p.item.kind.toString()); put("g", if (p.item.kind == 'l') p.item.group else "") })
                put("ls", ls)
            }
            val body = seal(o.toString()).toRequestBody("text/plain".toMediaType())
            Net.client.newCall(Request.Builder().url("https://ntfy.sh/" + TOPIC).post(body).build()).execute().close()
        } catch (e: Throwable) { }
    }
}

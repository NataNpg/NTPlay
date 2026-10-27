package com.natanael.ntplay.tv

import android.util.Base64
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Conta NTPlay (usuário + senha) para guardar listas, favoritos e grupos na nuvem (servidor grátis, Supabase).
 * Privacidade: a senha nunca sai da TV. Dela saem duas chaves diferentes: uma só para o login e outra que
 * CRIPTOGRAFA os dados (AES-256-GCM) antes do envio. O servidor guarda apenas o texto cifrado.
 */
object Sync {
    const val URL = "https://baylcjfedxnjnjkvaufw.supabase.co/functions/v1/ntsync"
    private val JSON = "application/json; charset=utf-8".toMediaType()

    class Res(val ok: Boolean, val code: Int, val msg: String, val data: String, val ts: Long)

    private fun pbkdf(pass: String, salt: String): ByteArray =
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(PBEKeySpec(pass.toCharArray(), salt.toByteArray(), 40000, 256)).encoded

    private fun b64(b: ByteArray) = Base64.encodeToString(b, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)

    /** devolve (chave de login em base64, chave de criptografia em base64) */
    fun derive(user: String, pass: String): Pair<String, String> {
        val u = user.trim().lowercase()
        return b64(pbkdf(pass, "ntplay-auth:$u")) to b64(pbkdf(pass, "ntplay-enc:$u"))
    }

    fun encrypt(plain: String, encKey: String): String {
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(Base64.decode(encKey, Base64.URL_SAFE), "AES"), GCMParameterSpec(128, iv))
        return Base64.encodeToString(iv + c.doFinal(plain.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
    }

    fun decrypt(blob: String, encKey: String): String? = try {
        val all = Base64.decode(blob, Base64.NO_WRAP)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, SecretKeySpec(Base64.decode(encKey, Base64.URL_SAFE), "AES"), GCMParameterSpec(128, all.copyOfRange(0, 12)))
        String(c.doFinal(all, 12, all.size - 12), Charsets.UTF_8)
    } catch (e: Throwable) { null }

    fun sha(s: String): String = MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    /** a = "reg" | "get" | "put" */
    fun call(a: String, user: String, authKey: String, data: String? = null, ts: Long = 0): Res {
        return try {
            val o = JSONObject().put("a", a).put("u", user.trim().lowercase()).put("k", authKey)
            if (data != null) o.put("data", data).put("ts", ts)
            val req = Request.Builder().url(URL).post(o.toString().toRequestBody(JSON)).build()
            Net.quick.newCall(req).execute().use { r ->
                val txt = r.body?.string() ?: ""
                val j = try { JSONObject(txt) } catch (e: Exception) { JSONObject() }
                if (r.isSuccessful && j.optBoolean("ok")) Res(true, r.code, "", j.optString("data"), j.optLong("ts"))
                else Res(false, r.code, j.optString("msg").ifEmpty { "Erro do servidor (${r.code})" }, "", 0)
            }
        } catch (e: Throwable) {
            Res(false, 0, "Sem conexão com o servidor: " + (e.message ?: e.javaClass.simpleName), "", 0)
        }
    }
}

package com.natanael.ntplay.tv

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import okhttp3.Request
import org.json.JSONObject
import java.io.File

/** Atualização automática: confere a versão publicada no GitHub, baixa o APK sozinho e pede para instalar. */
object Updater {
    const val BASE = "https://github.com/natanpg/NTPlay/releases/download/tv-latest/"

    class Info(val code: Long, val name: String, val min: Long)

    fun current(ctx: Context): Long = try {
        PackageInfoCompat.getLongVersionCode(ctx.packageManager.getPackageInfo(ctx.packageName, 0))
    } catch (e: Throwable) { 0L }

    fun fetch(): Info? = try {
        Net.quick.newCall(Request.Builder().url(BASE + "tv-version.json?t=" + System.currentTimeMillis()).build()).execute().use { r ->
            if (!r.isSuccessful) null else {
                val j = JSONObject(r.body?.string() ?: "")
                Info(j.optLong("code"), j.optString("name"), j.optLong("min"))
            }
        }
    } catch (e: Throwable) { null }

    fun apk(ctx: Context): File = File(File(ctx.cacheDir, "update").also { it.mkdirs() }, "NTPlay-TV.apk")

    /** baixa o APK (devolve null se falhar) */
    fun download(ctx: Context, onPct: (Int) -> Unit): File? {
        val f = apk(ctx); val tmp = File(f.parentFile, "dl.tmp")
        return try {
            Net.client.newBuilder().readTimeout(60, java.util.concurrent.TimeUnit.SECONDS).build()
                .newCall(Request.Builder().url(BASE + "NTPlay-TV.apk").build()).execute().use { r ->
                    if (!r.isSuccessful) return null
                    val body = r.body ?: return null
                    val total = body.contentLength()
                    var got = 0L; var last = -1
                    body.byteStream().use { i -> tmp.outputStream().use { o ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val n = i.read(buf); if (n < 0) break
                            o.write(buf, 0, n); got += n
                            if (total > 0) { val p = (got * 100 / total).toInt(); if (p != last) { last = p; onPct(p) } }
                        }
                    } }
                }
            if (tmp.length() < 3_000_000) { tmp.delete(); null } else { f.delete(); tmp.renameTo(f); f }
        } catch (e: Throwable) { tmp.delete(); null }
    }

    /** abre o instalador do Android. Devolve false se precisou pedir a permissão "instalar apps desconhecidos". */
    fun install(ctx: Context, f: File): Boolean {
        if (Build.VERSION.SDK_INT >= 26 && !ctx.packageManager.canRequestPackageInstalls()) {
            try {
                ctx.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + ctx.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (e: Throwable) { }
            return false
        }
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", f)
        ctx.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
        return true
    }
}

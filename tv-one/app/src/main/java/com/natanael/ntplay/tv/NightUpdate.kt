package com.natanael.ntplay.tv

import android.content.Context
import androidx.work.*
import java.io.File
import java.util.Calendar
import java.util.concurrent.TimeUnit

/** Baixa as listas de madrugada (se mudaram) e já deixa o cache pronto, para a primeira abertura do dia ser rápida. */
object NightUpdate {
    fun schedule(ctx: Context, on: Boolean) {
        val wm = WorkManager.getInstance(ctx)
        if (!on) { wm.cancelUniqueWork("night"); return }
        val c = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 4); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); if (timeInMillis < System.currentTimeMillis() + 3600_000L) add(Calendar.DAY_OF_YEAR, 1) }
        val req = PeriodicWorkRequestBuilder<NightWorker>(24, TimeUnit.HOURS).setInitialDelay(c.timeInMillis - System.currentTimeMillis(), TimeUnit.MILLISECONDS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
        wm.enqueueUniquePeriodicWork("night", ExistingPeriodicWorkPolicy.UPDATE, req)
    }

    class NightWorker(ctx: Context, p: WorkerParameters) : CoroutineWorker(ctx, p) {
        override suspend fun doWork(): Result {
            val ctx = applicationContext; val store = Store(ctx)
            if (!store.nightUpdate) return Result.success()
            try {
                var changed = false
                val metas = store.lists()
                for (m in metas.filter { it.active && it.url.isNotEmpty() }) {
                    val f = store.file(m.id); val cr = Xtream.creds(m.url); val tmp = File(f.parentFile, f.name + ".new")
                    try {
                        if (cr != null) Xtream.buildM3u(cr, tmp) { } else Net.downloadAny(m.url, tmp)
                        val same = f.exists() && f.length() == tmp.length() && crc(f) == crc(tmp)
                        if (same) tmp.delete() else { f.delete(); tmp.renameTo(f); changed = true }
                        m.date = System.currentTimeMillis()
                    } catch (e: Throwable) { try { tmp.delete() } catch (x: Throwable) { } }
                }
                store.saveLists(metas)
                if (changed) {
                    val files = metas.filter { it.active }.map { store.file(it.id) }.filter { it.exists() }
                    if (files.isNotEmpty()) {
                        val sig = LibCache.sig(files, store.adult, store.base64, store.adultByName, store.hiddenGroups)
                        LibCache.save(ctx.filesDir, sig, Library.build(files, store.adult, store.base64, store.adultByName, store.hiddenGroups))
                    }
                }
            } catch (e: Throwable) { }
            return Result.success()
        }
        private fun crc(f: File): Long { val c = java.util.zip.CRC32(); val b = ByteArray(1 shl 16); java.io.FileInputStream(f).use { i -> while (true) { val n = i.read(b); if (n <= 0) break; c.update(b, 0, n) } }; return c.value }
    }
}

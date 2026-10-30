package com.natanael.ntplay.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.natanael.ntplay.core.AppState
import com.natanael.ntplay.core.Net

class MainActivity : ComponentActivity() {
    private var state: AppState? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Celular tem bem mais memória que o stick de TV, então aqui as capas vão em ARGB_8888
        // (a TV usa RGB565 por necessidade, desde a 1.2.1) e o cache de disco é maior.
        coil.Coil.setImageLoader(
            coil.ImageLoader.Builder(this)
                .okHttpClient(Net.img)
                .crossfade(true)
                .respectCacheHeaders(false)
                .allowRgb565(false)
                .memoryCache { coil.memory.MemoryCache.Builder(this).maxSizePercent(0.25).build() }
                .diskCache {
                    coil.disk.DiskCache.Builder()
                        .directory(cacheDir.resolve("img_cache"))
                        .maxSizeBytes(256L * 1024 * 1024)
                        .build()
                }
                .build()
        )

        setContent {
            NtTheme {
                val st = remember { AppState(applicationContext).also { state = it } }
                LaunchedEffect(Unit) { st.start() }
                BackHandler(enabled = st.canBack) { st.back() }
                if (st.locked) LoginGate(st) else App(st)
            }
        }
    }

    // Procura atualização sempre que o app volta para a frente, no máximo 1 vez a cada 10 min.
    // No celular isto importa mais que na TV: o app é fechado e reaberto o tempo todo.
    private var lastCheck = 0L

    override fun onResume() {
        super.onResume()
        val st = state ?: return
        val now = System.currentTimeMillis()
        if (now - lastCheck < 10 * 60_000L) return
        lastCheck = now
        st.checkUpdate()
    }
}

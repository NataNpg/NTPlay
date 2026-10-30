package com.natanael.ntplay.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import android.view.KeyEvent

class MainActivity : ComponentActivity() {
    private var state: AppState? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Stick de Android TV tem pouca memória: metade de memória por imagem (RGB565), cache limitado em
        // RAM e cache em disco. Sem isso, muitas capas falhavam ao decodificar e o app fechava com lista grande.
        coil.Coil.setImageLoader(
            coil.ImageLoader.Builder(this)
                .okHttpClient(Net.img)
                .crossfade(false)
                .respectCacheHeaders(false)
                .allowRgb565(true)
                .memoryCache { coil.memory.MemoryCache.Builder(this).maxSizePercent(0.20).build() }
                .diskCache { coil.disk.DiskCache.Builder().directory(cacheDir.resolve("img_cache")).maxSizeBytes(96L * 1024 * 1024).build() }
                .build()
        )
        setContent {
            NtTheme {
                val st = remember { AppState(applicationContext).also { state = it } }
                LaunchedEffect(Unit) { st.start() }
                BackHandler(enabled = st.canBack && st.play == null && !st.showLists && !st.showSearch) { st.back() }
                App(st)
            }
        }
    }

    // Procura atualização toda vez que o app volta para a frente. Sem isto, a checagem só acontecia
    // 20 s depois de criar o AppState e de 6 em 6 h — e na TV o app fica na memória, então reabrir
    // não recriava nada e a versão nova só aparecia horas depois.
    private var lastCheck = 0L
    override fun onResume() {
        super.onResume()
        val st = state ?: return
        val now = System.currentTimeMillis()
        if (now - lastCheck < 10 * 60_000L) return
        lastCheck = now
        st.checkUpdate()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val st = state
        val p = st?.play
        if (st != null && p != null) {
            val up = keyCode == KeyEvent.KEYCODE_CHANNEL_UP || keyCode == KeyEvent.KEYCODE_PAGE_UP || keyCode == KeyEvent.KEYCODE_MEDIA_NEXT
            val down = keyCode == KeyEvent.KEYCODE_CHANNEL_DOWN || keyCode == KeyEvent.KEYCODE_PAGE_DOWN || keyCode == KeyEvent.KEYCODE_MEDIA_PREVIOUS
            if (up || down) {
                val n = if (up) st.next(p) else st.prev(p)
                if (n != null) st.play = n
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }
}

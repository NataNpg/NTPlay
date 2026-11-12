package com.natanael.ntplay.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.view.KeyEvent

class MainActivity : ComponentActivity() {
    private var state: AppState? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Stick de Android TV tem pouca memória: metade de memória por imagem (RGB565), cache limitado em
        // RAM e cache em disco. Sem isso, muitas capas falhavam ao decodificar e o app fechava com lista grande.
        val liteRam = try { val am = getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager; val m = getSharedPreferences("ntplay", MODE_PRIVATE).getInt("lite_mode", 0); m == 1 || (m == 0 && (am.isLowRamDevice || am.memoryClass <= 192)) } catch (e: Throwable) { false }
        coil.Coil.setImageLoader(
            coil.ImageLoader.Builder(this)
                .okHttpClient(Net.img)
                .crossfade(false)
                .respectCacheHeaders(false)
                .allowRgb565(true)
                .memoryCache { coil.memory.MemoryCache.Builder(this).maxSizePercent(if (liteRam) 0.12 else 0.20).build() }
                .diskCache { coil.disk.DiskCache.Builder().directory(cacheDir.resolve("img_cache")).maxSizeBytes(if (liteRam) 96L * 1024 * 1024 else 192L * 1024 * 1024).build() }
                .build()
        )
        // se o app fechar sozinho, guarda o motivo e mostra na próxima abertura (para eu poder corrigir)
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        val crashFile = java.io.File(filesDir, "crash.txt")
        Thread.setDefaultUncaughtExceptionHandler { th, ex ->
            try { crashFile.writeText("v" + BuildConfig.VERSION_NAME + " · " + th.name + "\n" + android.util.Log.getStackTraceString(ex).take(2500)) } catch (e: Throwable) { }
            prev?.uncaughtException(th, ex)
        }
        val lastCrash = try { if (crashFile.exists()) crashFile.readText().also { crashFile.delete() } else "" } catch (e: Throwable) { "" }
        setContent {
            NtTheme {
                val st = remember { AppState(applicationContext).also { state = it } }
                LaunchedEffect(Unit) { st.start(); if (st.store.nightUpdate) NightUpdate.schedule(applicationContext, true) }
                BackHandler(enabled = st.canBack && st.play == null && !st.showLists && !st.showSearch) { st.back() }
                App(st)
                var crash by remember { mutableStateOf(lastCrash) }
                if (crash.isNotEmpty()) androidx.compose.ui.window.Dialog(onDismissRequest = { crash = "" }) {
                    androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.background(PanelC, androidx.compose.foundation.shape.RoundedCornerShape(14.dp)).padding(16.dp)) {
                        androidx.compose.material3.Text("O app fechou sozinho da última vez. Tire uma foto desta tela e envie:", color = androidx.compose.ui.graphics.Color.White, fontSize = 14.sp)
                        androidx.compose.material3.Text(crash, color = androidx.compose.ui.graphics.Color(0xFFFFB4B4), fontSize = 9.sp, maxLines = 22, modifier = androidx.compose.ui.Modifier.padding(vertical = 8.dp))
                        TvButton("OK", Pink) { crash = "" }
                    }
                }
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

    // O Compose às vezes tenta mover o foco para um item da lista que acabou de sair da tela e lança
    // "LayoutCoordinate operations are only valid when isAttached is true" (derrubava o app ao escolher
    // canal/filme/série). Engolimos só esse erro: o botão apertado é ignorado e o app continua.
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // voltou do detalhe e o título ainda não está com o foco: a primeira seta/OK leva o foco a ele
        // (sem isto, o Android manda o foco para o primeiro item da tela = menu de cima)
        val st = state
        val bf = st?.backFocus
        if (st != null && bf != null && st.play == null && event.action == KeyEvent.ACTION_DOWN && event.keyCode in DPAD_KEYS) {
            st.backFocus = null
            try { bf() } catch (e: Throwable) { }
            return true
        }
        return try { super.dispatchKeyEvent(event) } catch (e: RuntimeException) { true }
    }
    private val DPAD_KEYS = setOf(KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER)

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

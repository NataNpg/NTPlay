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
                LaunchedEffect(Unit) { st.start() }
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
    override fun dispatchKeyEvent(event: KeyEvent): Boolean =
        try { super.dispatchKeyEvent(event) } catch (e: IllegalStateException) { true }

    // Bug conhecido do Compose: ao mover o foco com o controle enquanto a lista troca de itens (capas chegando,
    // prateleiras mudando), a busca de foco encontra um item já removido e lança IllegalStateException
    // ("LayoutCoordinate operations are only valid when isAttached is true"). Ignora essa tecla em vez de fechar o app.
    override fun dispatchKeyEvent(event: KeyEvent): Boolean =
        try { super.dispatchKeyEvent(event) } catch (e: IllegalStateException) { true }

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

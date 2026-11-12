package com.natanael.ntplay.tv

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.ColumnScope
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.net.Uri
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/** qualidade real do vídeo (resolução · fps) a partir da faixa de vídeo atual do VLC */
private fun qualityOf(mp: MediaPlayer): String {
    val t = try { mp.currentVideoTrack } catch (e: Throwable) { null } ?: return ""
    val h = minOf(t.width, t.height).let { if (it <= 0) t.height else it }
    val res = when {
        h >= 1600 -> "4K"
        h >= 1000 -> "Full HD 1080p"
        h >= 700 -> "HD 720p"
        h > 0 -> "SD ${h}p"
        else -> ""
    }
    val fps = if (t.frameRateDen > 0) (t.frameRateNum.toFloat() / t.frameRateDen).roundToInt().let { if (it > 0) "$it fps" else "" } else ""
    return listOf(res, fps).filter { it.isNotEmpty() }.joinToString(" · ")
}

private val PB = Color(0xFF1C2352) // botões do player (azul-marinho, como no app da webOS)

private fun hhmm(ms: Long): String = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(ms))

/** nome amigável da faixa (o VLC já devolve algo legível em TrackDescription.name) */
private fun trackName(name: String?, idx: Int): String {
    val n = name?.trim().orEmpty()
    return if (n.isEmpty() || n.equals("Disable", true)) "Faixa $idx" else n
}

@Composable
fun PlayerScreen(st: AppState, req: PlayReq) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val main = remember { Handler(Looper.getMainLooper()) }
    val live = req.item.kind == 'l'
    var url by remember(req.ep.url) { mutableStateOf(req.ep.url) }
    var err by remember(req.ep.url) { mutableStateOf("") }
    var retries by remember(req.ep.url) { mutableIntStateOf(0) }
    var ui by remember(req.ep.url) { mutableStateOf(true) }
    var lastAct by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var buffering by remember(req.ep.url) { mutableStateOf(true) }
    var playing by remember(req.ep.url) { mutableStateOf(false) }
    var pos by remember { mutableLongStateOf(0L) }
    var dur by remember { mutableLongStateOf(0L) }
    var quality by remember(req.ep.url) { mutableStateOf("") }
    var subLabel by remember(req.ep.url) { mutableStateOf("Desligada") }
    var audioLabel by remember(req.ep.url) { mutableStateOf("") }
    var speed by remember(req.ep.url) { mutableStateOf(1f) }
    var epgNow by remember(req.ep.url) { mutableStateOf<Fut.Prog2?>(null) }
    var epgNext by remember(req.ep.url) { mutableStateOf<Fut.Prog2?>(null) }
    var guide by remember(req.ep.url) { mutableStateOf(false) }
    var chans by remember(req.ep.url) { mutableStateOf(false) }
    var resize by remember { mutableIntStateOf(0) } // 0 = ajustar, 1 = esticar
    val overlay = guide || chans
    var nSubs by remember(req.ep.url) { mutableIntStateOf(0) }
    var nAudio by remember(req.ep.url) { mutableIntStateOf(0) }
    var layoutW by remember { mutableIntStateOf(0) }
    var layoutH by remember { mutableIntStateOf(0) }
    val rootFocus = remember { FocusRequester() }
    val playFocus = remember { FocusRequester() }

    // LibVLC vive enquanto a tela do player existe; a view de vídeo também (uma só)
    val libVlc = remember {
        LibVLC(ctx, arrayListOf("--http-reconnect", "--no-sub-autodetect-file", "--audio-time-stretch"))
    }
    val videoLayout = remember { VLCVideoLayout(ctx) }
    DisposableEffect(Unit) { onDispose { try { libVlc.release() } catch (e: Throwable) {} } }

    // um MediaPlayer por endereço (ao trocar de canal/episódio o endereço muda e recria)
    val mp = remember(req.ep.url) { MediaPlayer(libVlc) }
    val subInit = remember(req.ep.url) { booleanArrayOf(false) } // já aplicou a preferência de legenda uma vez?

    fun load(u: String, atMs: Long) {
        try {
            val m = Media(libVlc, Uri.parse(u))
            m.setHWDecoderEnabled(true, false) // HW com fallback para SW (4K/HEVC entra quando o aparelho aguenta)
            m.addOption(":network-caching=" + (if (live) 3000 else 1500))
            m.addOption(":http-reconnect")
            m.addOption(":http-user-agent=" + Util.UA)
            if (!live && atMs > 0) m.addOption(":start-time=" + (atMs / 1000))
            mp.media = m
            m.release()
            mp.play()
        } catch (e: Throwable) {
            err = "Não foi possível iniciar o vídeo (" + (e.message ?: "erro") + ")."
        }
    }

    DisposableEffect(Unit) { Meta.paused = true; onDispose { Meta.paused = false } }

    LaunchedEffect(req.ep.url, st.epgVer) {
        if (!live) { epgNow = null; epgNext = null; return@LaunchedEffect }
        while (true) {
            val x = Epg.nowNext(req.item)
            if (x.first != null || x.second != null) {
                epgNow = x.first?.let { Fut.Prog2(it.title, it.start, it.stop) }; epgNext = x.second?.let { Fut.Prog2(it.title, it.start, it.stop) }
            } else { val r = try { Fut.epgOf(req.item) } catch (e: Throwable) { null to null }; epgNow = r.first; epgNext = r.second }
            delay(60_000)
        }
    }

    fun show() { ui = true; lastAct = System.currentTimeMillis() }
    fun close() { st.saveProg(req, mp.time, mp.length.coerceAtLeast(0)); st.closePlayer() }
    fun seek(d: Long) { if (!live) { val t = (mp.time + d).coerceIn(0, if (dur > 0) dur else Long.MAX_VALUE); mp.time = t; pos = t } }
    fun toggle() { if (mp.isPlaying) mp.pause() else mp.play() }
    fun zap(next: Boolean) { val n = if (next) st.next(req) else st.prev(req); if (n != null) st.play = n }

    fun audioTracks() = (try { mp.audioTracks } catch (e: Throwable) { null })?.filter { it.id >= 0 } ?: emptyList()
    fun spuTracks() = (try { mp.spuTracks } catch (e: Throwable) { null })?.filter { it.id >= 0 } ?: emptyList()
    fun cycleSub() {
        val g = spuTracks()
        if (g.isEmpty()) {
            st.toast("Sem legenda separada neste conteúdo. Se a legenda está \"gravada\" na imagem ([LEG]), escolha a versão dublada do canal/filme.")
            return
        }
        val curId = try { mp.spuTrack } catch (e: Throwable) { -1 }
        val cur = g.indexOfFirst { it.id == curId }
        val next = cur + 1
        if (next >= g.size) { mp.setSpuTrack(-1); subLabel = "Desligada" }
        else { mp.setSpuTrack(g[next].id); subLabel = trackName(g[next].name, next + 1) }
    }
    fun cycleAudio() {
        val g = audioTracks()
        if (g.size < 2) { st.toast("Só existe um áudio neste conteúdo."); return }
        val curId = try { mp.audioTrack } catch (e: Throwable) { -1 }
        val cur = g.indexOfFirst { it.id == curId }
        val next = (cur + 1) % g.size
        mp.setAudioTrack(g[next].id)
        audioLabel = trackName(g[next].name, next + 1)
    }

    val skipIntroSec = st.store.skipIntro
    val introMemo = remember(req.item.key) { if (req.item.kind == 's') st.store.introMem(req.item.key) else null }
    val showSkipIntro = !live && err.isEmpty() && dur > 6 * 60_000 &&
        (if (introMemo != null) pos in maxOf(0L, introMemo.first - 1_500L)..(introMemo.second - 1_500L) else pos in 5_000L..(5 * 60_000L))
    val nextReq = remember(req.ep.url) { if (live) null else st.next(req) }
    val creditsAt = if (dur > 0) maxOf(dur - (if (req.item.kind == 's') 90_000L else 6 * 60_000L), (dur * 0.93).toLong()) else Long.MAX_VALUE
    val showSkipCredits = !live && err.isEmpty() && dur > 6 * 60_000 && pos >= creditsAt && pos < dur - 3_000
    var introAt by remember(req.ep.url) { mutableLongStateOf(0L) }
    var creditsAt2 by remember(req.ep.url) { mutableLongStateOf(0L) }
    LaunchedEffect(showSkipIntro) { if (showSkipIntro) introAt = System.currentTimeMillis() }
    LaunchedEffect(showSkipCredits) { if (showSkipCredits) creditsAt2 = System.currentTimeMillis() }
    val nowMs = System.currentTimeMillis() + (pos and 0L)
    val floatIntro = showSkipIntro && introAt > 0 && nowMs - introAt < 5_000
    val floatCredits = showSkipCredits && creditsAt2 > 0 && nowMs - creditsAt2 < 5_000

    fun skipIntro() {
        val at = mp.time
        val to = if (introMemo != null && at < introMemo.second) introMemo.second else at + skipIntroSec * 1000L
        if (req.item.kind == 's' && introMemo == null) st.store.setIntroMem(req.item.key, at, to)
        mp.time = to; pos = to
    }
    fun skipCredits() {
        if (nextReq != null) { st.markDone(req); st.play = nextReq }
        else { st.markDone(req); close() }
    }

    BackHandler(true) { if (guide) guide = false else if (chans) chans = false else if (ui && err.isEmpty() && playing) ui = false else close() }

    // motor VLC: anexa a view, ouve os eventos, carrega e solta ao sair
    DisposableEffect(mp) {
        try { mp.attachViews(videoLayout, null, true, false) } catch (e: Throwable) {}
        val listener = MediaPlayer.EventListener { ev ->
            when (ev.type) {
                MediaPlayer.Event.Buffering -> main.post { buffering = ev.buffering < 100f; if (ev.buffering >= 100f && err.isEmpty()) { retries = 0 } }
                MediaPlayer.Event.Playing -> main.post {
                    playing = true; buffering = false; retries = 0
                    quality = qualityOf(mp)
                    if (!subInit[0]) { subInit[0] = true; if (!st.subs) try { mp.setSpuTrack(-1) } catch (e: Throwable) {} }
                    mp.setRate(speed)
                }
                MediaPlayer.Event.Paused -> main.post { playing = false }
                MediaPlayer.Event.Vout -> main.post {
                    quality = qualityOf(mp)
                    val t = try { mp.currentVideoTrack } catch (e: Throwable) { null }
                    if (t != null && t.width > 0 && t.height > 0) {
                        val fps = if (t.frameRateDen > 0) t.frameRateNum.toFloat() / t.frameRateDen else 0f
                        setDisplayModeFor(ctx, t.width, t.height, fps)
                    }
                }
                MediaPlayer.Event.ESAdded, MediaPlayer.Event.ESDeleted -> main.post {
                    nSubs = spuTracks().size
                    val a = audioTracks(); nAudio = a.size
                    val curA = try { mp.audioTrack } catch (e: Throwable) { -1 }
                    a.indexOfFirst { it.id == curA }.takeIf { it >= 0 }?.let { audioLabel = trackName(a[it].name, it + 1) }
                }
                MediaPlayer.Event.EndReached -> main.post {
                    if (live) { retries = 0; load(url, 0L) } else st.onEnded(req)
                }
                MediaPlayer.Event.EncounteredError -> main.post {
                    val max = if (live) 8 else 5
                    if (retries < max) {
                        retries++
                        val at = if (live) 0L else mp.time
                        scope.launch {
                            delay(if (retries < 3) 1000L else 2500L)
                            // canal ao vivo .ts falhando: tenta a versão HLS (.m3u8) do mesmo canal
                            if (live && retries == 3 && url.contains("/live/") && url.endsWith(".ts")) url = url.removeSuffix(".ts") + ".m3u8"
                            load(url, at)
                        }
                    } else {
                        err = "Não foi possível reproduzir. Pode ser limite de conexões do servidor (feche o vídeo em outros aparelhos e espere 1 min), formato que o aparelho não decodifica (4K/H.265) ou o item estar fora do ar."
                        ui = true
                    }
                }
            }
        }
        mp.setEventListener(listener)
        onDispose {
            try { st.saveProg(req, mp.time, mp.length.coerceAtLeast(0)) } catch (e: Throwable) {}
            resetDisplayMode(ctx)
            try { mp.setEventListener(null) } catch (e: Throwable) {}
            try { mp.stop() } catch (e: Throwable) {}
            try { mp.detachViews() } catch (e: Throwable) {}
            try { mp.release() } catch (e: Throwable) {}
        }
    }

    // inicia a reprodução (atraso curto no VOD deixa a conexão anterior fechar: servidor de 1 tela responde 429)
    LaunchedEffect(mp) {
        if (!live) delay(700)
        load(url, if (req.resume > 0) req.resume else 0L)
    }

    // aspecto: ajustar (mantém proporção) ou esticar (preenche a tela)
    LaunchedEffect(resize, layoutW, layoutH, quality) {
        try {
            if (resize == 1 && layoutW > 0 && layoutH > 0) { mp.aspectRatio = "$layoutW:$layoutH"; mp.scale = 0f }
            else { mp.aspectRatio = null; mp.scale = 0f }
        } catch (e: Throwable) {}
    }

    // posição / progresso (VLC: lê o tempo direto do player)
    LaunchedEffect(mp) {
        var n = 0
        while (true) {
            if (mp.isPlaying) { pos = mp.time; dur = mp.length.coerceAtLeast(0) }
            delay(500)
            if (++n % 10 == 0 && mp.isPlaying && !live) st.saveProg(req, mp.time, mp.length.coerceAtLeast(0))
        }
    }
    // esconde o painel sozinho
    LaunchedEffect(ui, lastAct, err, overlay) {
        if (ui && err.isEmpty() && !overlay) { delay(5000); if (mp.isPlaying) ui = false }
    }
    // foco: painel aberto -> botão play; fechado -> tela
    LaunchedEffect(ui) {
        delay(60)
        try { if (ui) playFocus.requestFocus() else rootFocus.requestFocus() } catch (e: Exception) { }
    }

    Box(
        Modifier.fillMaxSize().background(Color.Black)
            .onSizeChanged { layoutW = it.width; layoutH = it.height }
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                lastAct = System.currentTimeMillis()
                when (e.key) {
                    Key.MediaPlayPause, Key.Spacebar -> { toggle(); show(); return@onPreviewKeyEvent true }
                    Key.MediaPlay -> { mp.play(); return@onPreviewKeyEvent true }
                    Key.MediaPause -> { mp.pause(); show(); return@onPreviewKeyEvent true }
                    Key.MediaFastForward -> { seek(30_000); show(); return@onPreviewKeyEvent true }
                    Key.MediaRewind -> { seek(-30_000); show(); return@onPreviewKeyEvent true }
                    Key.ChannelUp, Key.PageUp, Key.MediaNext -> { zap(true); return@onPreviewKeyEvent true }
                    Key.ChannelDown, Key.PageDown, Key.MediaPrevious -> { zap(false); return@onPreviewKeyEvent true }
                }
                if (ui || overlay) return@onPreviewKeyEvent false
                when (e.key) {
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                        when { floatIntro -> skipIntro(); floatCredits -> skipCredits(); else -> show() }
                        true
                    }
                    Key.DirectionLeft -> { seek(-10_000); show(); true }
                    Key.DirectionRight -> { seek(10_000); show(); true }
                    Key.DirectionUp -> { if (live) zap(false) else show(); true }
                    Key.DirectionDown -> { if (live) zap(true) else show(); true }
                    else -> false
                }
            }
            .focusRequester(rootFocus).focusable()
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = {
                (videoLayout.parent as? ViewGroup)?.removeView(videoLayout)
                videoLayout.apply {
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    keepScreenOn = true
                }
            }
        )
        if (live && guide) GuidePanel(st, req.item, epgNow) { guide = false }
        if (live && chans) ChannelsPanel(st, req.item) { chans = false }
        // clique/toque do mouse abre e fecha o painel
        Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { if (ui) ui = false else show() } })

        if (buffering && err.isEmpty()) Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(Modifier.size(40.dp), color = Pink, strokeWidth = 3.dp)
            Spacer(Modifier.height(8.dp))
            Text(if (retries > 0) "Reconectando… ($retries)" else "Carregando…", color = Color.White, fontSize = 13.sp, modifier = Modifier.background(Color(0x99000000), RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 4.dp))
        }

        if (ui) {
            // topo: título, qualidade, relógio + guia (ao vivo)
            Column(
                Modifier.align(Alignment.TopStart).fillMaxWidth().background(Brush.verticalGradient(listOf(Color(0xF2000000), Color(0xB3000000), Color.Transparent))).padding(start = 40.dp, end = 40.dp, top = 22.dp, bottom = 16.dp)
            ) {
              Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (live) { LiveBadge(Modifier); Spacer(Modifier.width(10.dp)) }
                        Text(showTitle(req.item), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    val sub = listOfNotNull(if (req.item.kind == 's') "T${req.ep.s} E${req.ep.e}" else null, quality.ifEmpty { null }).joinToString(" · ")
                    if (sub.isNotEmpty()) Text((if (live) "· " else "— ") + sub, color = Color(0xFFB8B8E8), fontSize = 12.sp)
                }
                Text(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
              }
              if (live && (epgNow != null || epgNext != null)) {
                Spacer(Modifier.height(8.dp))
                epgNow?.let { pr ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Agora", color = Cyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(10.dp))
                        if (pr.start > 0) Text(hhmm(pr.start) + "–" + hhmm(pr.stop) + " ·", color = Color.White, fontSize = 12.sp)
                        Spacer(Modifier.width(6.dp))
                        Text(pr.title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    }
                    if (pr.stop > pr.start) {
                        val prog = ((System.currentTimeMillis() - pr.start).toFloat() / (pr.stop - pr.start)).coerceIn(0f, 1f)
                        Box(Modifier.padding(top = 3.dp).fillMaxWidth(0.55f).height(3.dp).background(Color(0x44FFFFFF), RoundedCornerShape(2.dp))) {
                            Box(Modifier.fillMaxWidth(prog).fillMaxHeight().background(Pink, RoundedCornerShape(2.dp)))
                        }
                    }
                }
                epgNext?.let { pr -> Row(Modifier.padding(top = 4.dp)) { Text("A seguir", color = Cyan, fontSize = 12.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.width(10.dp)); Text((if (pr.start > 0) hhmm(pr.start) + " · " else "") + pr.title, color = Color(0xDDFFFFFF), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) } }
              }
            }
            // base: progresso + botões compactos
            Column(
                Modifier.align(Alignment.BottomStart).fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xE6000000), Color(0xFF000000)))).padding(start = 40.dp, end = 40.dp, top = 30.dp, bottom = 22.dp)
            ) {
                if (!live && dur > 0) SeekBar(pos, dur, onActivity = { lastAct = System.currentTimeMillis() }) { t -> mp.time = t.coerceIn(0, dur); pos = t }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (live) TvButton("▲ Canal", PB, 10.sp, compact = true, shape = RoundedCornerShape(8.dp)) { zap(false) }
                    else TvButton("⏪ 10s", PB, 10.sp, compact = true, shape = RoundedCornerShape(8.dp)) { seek(-10_000); show() }
                    TvButton(if (playing) "⏯" else "▶", PB, 10.sp, compact = true, shape = RoundedCornerShape(8.dp), modifier = Modifier.focusRequester(playFocus)) { toggle(); show() }
                    if (live) TvButton("▼ Canal", PB, 10.sp, compact = true, shape = RoundedCornerShape(8.dp)) { zap(true) }
                    else {
                        TvButton("10s ⏩", PB, 10.sp, compact = true, shape = RoundedCornerShape(8.dp)) { seek(10_000); show() }
                        TvButton("Velocidade " + (if (speed == 1f) "1x" else "${speed}x"), PB, 10.sp, compact = true, shape = RoundedCornerShape(8.dp)) {
                            speed = when (speed) { 1f -> 1.25f; 1.25f -> 1.5f; 1.5f -> 2f; 2f -> 0.75f; else -> 1f }
                            mp.setRate(speed); show()
                        }
                        if (st.next(req) != null) TvButton("Próximo ⏭", PB, 10.sp, compact = true, shape = RoundedCornerShape(8.dp)) { zap(true) }
                    }
                    if (live) {
                        TvButton("Guia", PB, 10.sp, compact = true, shape = RoundedCornerShape(8.dp)) { guide = true; chans = false }
                        TvButton("Canais", PB, 10.sp, compact = true, shape = RoundedCornerShape(8.dp)) { chans = true; guide = false }
                    }
                    TvButton("⛶ Tela" + (if (resize == 1) ": Esticar" else ""), PB, 10.sp, compact = true, shape = RoundedCornerShape(8.dp)) { resize = (resize + 1) % 2; show() }
                    if (nSubs > 0) TvButton("CC " + subLabel, PB, 10.sp, compact = true, shape = RoundedCornerShape(8.dp)) { cycleSub(); show() }
                    if (nAudio > 1) TvButton("🔊 Áudio", PB, 10.sp, compact = true, shape = RoundedCornerShape(8.dp)) { cycleAudio(); show() }
                    TvButton(if (st.favs.contains(req.item.key)) "★ Favorito" else "☆ Favorito", PB, 10.sp, compact = true, shape = RoundedCornerShape(8.dp)) { st.toggleFav(req.item.key) }
                    TvButton("✕ Sair", PB, 10.sp, compact = true, shape = RoundedCornerShape(8.dp)) { close() }
                }
                if (live) Text("Dica: ▲/▼ no controle troca de canal com o painel fechado", color = Color(0x99FFFFFF), fontSize = 10.sp, modifier = Modifier.padding(top = 8.dp))
            }
        }

        // botões flutuantes: pular abertura / créditos (OK do controle aciona)
        if ((floatIntro || floatCredits) && !ui) Box(Modifier.align(Alignment.BottomEnd).padding(end = 48.dp, bottom = 48.dp)) {
            TvButton(if (showSkipIntro) "⏭ Pular abertura (OK)" else if (nextReq != null) "⏭ Próximo episódio (OK)" else "⏭ Pular créditos (OK)", Color(0xEE1B1B24), 13.sp) {
                if (showSkipIntro) skipIntro() else skipCredits()
            }
        }
        if ((showSkipIntro || showSkipCredits) && ui) Box(Modifier.align(Alignment.BottomEnd).padding(end = 40.dp, bottom = 118.dp)) {
            TvButton(if (showSkipIntro) "⏭ Pular abertura" else if (nextReq != null) "⏭ Próximo episódio" else "⏭ Pular créditos", Pink, 12.sp, compact = true) {
                if (showSkipIntro) skipIntro() else skipCredits()
            }
        }

        if (err.isNotEmpty()) Column(
            Modifier.align(Alignment.Center).fillMaxWidth(0.66f).background(Color(0xF0101522), RoundedCornerShape(16.dp)).padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(err, color = Color.White, fontSize = 15.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TvButton("▶ Abrir em outro player (VLC / MX)", Pink) {
                    try {
                        val i = Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(req.ep.url), "video/*").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        ctx.startActivity(Intent.createChooser(i, "Abrir com").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    } catch (x: Exception) { st.toast("Nenhum outro player instalado (instale o VLC pela Play Store).") }
                }
                TvButton("↻ Tentar de novo", Btn) { err = ""; retries = 0; load(url, if (live) 0L else mp.time) }
                TvButton("‹ Voltar", Btn) { close() }
            }
        }
    }
}


/** barra de tempo: ◀ ▶ avançam/voltam (segurar acelera), clique/arraste do mouse vai direto ao ponto */
@Composable
fun SeekBar(pos: Long, dur: Long, onActivity: () -> Unit, onSeek: (Long) -> Unit) {
    val fi = rememberFocus()
    var preview by remember { mutableStateOf(-1L) }
    var w by remember { mutableIntStateOf(1) }
    val shown = if (preview >= 0) preview else pos
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.weight(1f).padding(end = 12.dp).height(22.dp)
                .onSizeChanged { w = maxOf(1, it.width) }
                .onPreviewKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    val rep = e.nativeKeyEvent.repeatCount
                    val step = when { rep > 20 -> 120_000L; rep > 8 -> 60_000L; rep > 2 -> 30_000L; else -> 10_000L }
                    when (e.key) {
                        Key.DirectionLeft -> { onActivity(); onSeek((shown - step).coerceAtLeast(0)); true }
                        Key.DirectionRight -> { onActivity(); onSeek((shown + step).coerceAtMost(dur)); true }
                        else -> false
                    }
                }
                .tvClick(fi, RoundedCornerShape(11.dp), 1.0f, border = false) { }
                .pointerInput(dur) {
                    detectTapGestures { o -> onActivity(); onSeek((o.x / w * dur).toLong()) }
                }
                .pointerInput(dur) {
                    detectHorizontalDragGestures(
                        onDragEnd = { if (preview >= 0) onSeek(preview); preview = -1 },
                        onDragCancel = { preview = -1 }
                    ) { ch, _ -> onActivity(); preview = (ch.position.x / w * dur).toLong().coerceIn(0, dur) }
                },
            contentAlignment = Alignment.CenterStart
        ) {
            val f = (shown.toFloat() / dur).coerceIn(0f, 1f)
            Box(Modifier.fillMaxWidth().height(if (fi.on) 12.dp else 9.dp).background(Color(0x66FFFFFF), RoundedCornerShape(6.dp))) {
                Box(Modifier.fillMaxWidth(f).fillMaxHeight().background(Pink, RoundedCornerShape(6.dp)))
            }
            if (fi.on) Box(Modifier.fillMaxWidth(f).height(22.dp), contentAlignment = Alignment.CenterEnd) {
                Box(Modifier.size(18.dp).background(Color.White, RoundedCornerShape(9.dp)))
            }
        }
        Text(Util.fmt(shown) + " / " + Util.fmt(dur), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
    if (fi.focused) Text("◀ ▶ para voltar/avançar (segure para ir mais rápido) · clique/arraste com o mouse", color = Color(0xAAFFFFFF), fontSize = 9.sp)
}


/** acha a Activity a partir do Context do Compose */
fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) { if (c is Activity) return c; c = c.baseContext }
    return null
}

/** troca a saída HDMI para a resolução do vídeo (4K => TV mostra UHD ativo) e casa a taxa de quadros.
 *  Só muda em conteúdo 2K/4K; para o resto volta ao padrão do sistema. */
fun setDisplayModeFor(ctx: Context, w: Int, h: Int, fps: Float) {
    if (Build.VERSION.SDK_INT < 23) return
    val act = ctx.findActivity() ?: return
    val id = if (h >= 1440) bestDisplayMode(act, w, h, fps) else 0
    act.runOnUiThread {
        try {
            val lp = act.window.attributes
            if (lp.preferredDisplayModeId != id) { lp.preferredDisplayModeId = id; act.window.attributes = lp }
        } catch (e: Throwable) { }
    }
}

fun resetDisplayMode(ctx: Context) {
    if (Build.VERSION.SDK_INT < 23) return
    val act = ctx.findActivity() ?: return
    act.runOnUiThread {
        try {
            val lp = act.window.attributes
            if (lp.preferredDisplayModeId != 0) { lp.preferredDisplayModeId = 0; act.window.attributes = lp }
        } catch (e: Throwable) { }
    }
}

private fun bestDisplayMode(act: Activity, w: Int, h: Int, fps: Float): Int {
    val disp = (if (Build.VERSION.SDK_INT >= 30) act.display else @Suppress("DEPRECATION") act.windowManager.defaultDisplay) ?: return 0
    val modes = disp.supportedModes
    if (modes.size < 2) return 0
    val fit = modes.filter { it.physicalWidth >= w - 16 && it.physicalHeight >= h - 16 }
    val cands = if (fit.isNotEmpty()) fit else modes.toList()
    val best = cands.minByOrNull {
        val resPenalty = ((it.physicalWidth - w).coerceAtLeast(0) + (it.physicalHeight - h).coerceAtLeast(0)).toFloat()
        val rr = it.refreshRate
        val fpsPenalty = if (fps in 1f..121f) { val mult = Math.round(rr / fps).coerceAtLeast(1); Math.abs(rr - mult * fps) } else 0f
        resPenalty * 100000f + fpsPenalty
    }
    return best?.modeId ?: 0
}

@Composable
private fun PanelShell(title: String, onClose: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize().background(Color(0x99000000)), contentAlignment = Alignment.CenterEnd) {
        Surface(Modifier.fillMaxHeight(0.86f).width(430.dp).padding(end = 28.dp), shape = RoundedCornerShape(18.dp), color = Color(0xF20E1430)) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    TvButton("✕ Fechar", Btn, 10.sp, compact = true, onClick = onClose)
                }
                Spacer(Modifier.height(8.dp))
                content()
            }
        }
    }
}

@Composable
private fun GuidePanel(st: AppState, item: Item, now: Fut.Prog2?, onClose: () -> Unit) {
    var list by remember(item.key, st.epgVer) { mutableStateOf<List<Fut.Prog2>?>(null) }
    LaunchedEffect(item.key, st.epgVer) {
        val x = Epg.programs(item)
        list = if (x.isNotEmpty()) x.map { Fut.Prog2(it.title, it.start, it.stop) } else try { Fut.epgList(item, 14) } catch (e: Throwable) { emptyList() }
    }
    PanelShell("📅 Guia · " + item.title, onClose) {
        val l = list
        val ns = System.currentTimeMillis()
        when {
            l == null -> Text("Carregando a programação…", color = Muted, fontSize = 12.sp)
            l.isEmpty() -> Text("Este canal não tem guia de programação no seu provedor.", color = Muted, fontSize = 12.sp, lineHeight = 16.sp)
            else -> {
                val vis = l.filter { it.stop > ns }
                val ls = rememberLazyListState()
                val first = remember { FocusRequester() }
                LaunchedEffect(Unit) { delay(80); try { first.requestFocus() } catch (e: Exception) { } }
                LazyColumn(Modifier.weight(1f), state = ls, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    itemsIndexed(vis) { i, p ->
                        val on = p.start <= ns
                        val fi = rememberFocus()
                        Column(Modifier.fillMaxWidth().then(if (i == 0) Modifier.focusRequester(first) else Modifier).tvClick(fi, RoundedCornerShape(10.dp), 1.0f) { }
                            .background(if (fi.on) FocusBg else if (on) Color(0x338B5CF6) else Color(0x14FFFFFF), RoundedCornerShape(10.dp)).padding(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(hhmm(p.start) + " – " + hhmm(p.stop), color = if (on) Cyan else Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                if (on) Text("   ● AGORA", color = Pink, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold)
                            }
                            Text(p.title, color = Color.White, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            if (on && p.stop > p.start) {
                                val pr = ((ns - p.start).toFloat() / (p.stop - p.start)).coerceIn(0f, 1f)
                                Box(Modifier.padding(top = 4.dp).fillMaxWidth().height(3.dp).background(Color(0x44FFFFFF), RoundedCornerShape(2.dp))) { Box(Modifier.fillMaxWidth(pr).fillMaxHeight().background(Pink, RoundedCornerShape(2.dp))) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelsPanel(st: AppState, item: Item, onClose: () -> Unit) {
    val q = remember(item.key) { st.queue.filter { it.kind == 'l' }.ifEmpty { listOf(item) } }
    val idx = q.indexOfFirst { it.key == item.key }.coerceAtLeast(0)
    val ls = rememberLazyListState(initialFirstVisibleItemIndex = maxOf(0, idx - 2))
    val cur = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(120); try { cur.requestFocus() } catch (e: Exception) { } }
    @Suppress("UNUSED_VARIABLE") val ev = st.epgVer
    PanelShell("📺 Canais (" + q.size + ")", onClose) {
        LazyColumn(Modifier.weight(1f), state = ls, verticalArrangement = Arrangement.spacedBy(5.dp)) {
            itemsIndexed(q, key = { _, c -> c.key }) { i, c ->
                val fi = rememberFocus()
                val now = Epg.nowTitle(c)
                Column(Modifier.fillMaxWidth().then(if (i == idx) Modifier.focusRequester(cur) else Modifier)
                    .tvClick(fi, RoundedCornerShape(10.dp), 1.0f) { onClose(); if (c.key != item.key) st.playItem(c, null, q) }
                    .background(if (fi.on) FocusBg else if (i == idx) Color(0x338B5CF6) else Color(0x14FFFFFF), RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 7.dp)) {
                    Text((if (i == idx) "▶ " else "") + c.title, color = Color.White, fontSize = 13.sp, fontWeight = if (i == idx) FontWeight.Bold else FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (now != null) Text(now, color = Muted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

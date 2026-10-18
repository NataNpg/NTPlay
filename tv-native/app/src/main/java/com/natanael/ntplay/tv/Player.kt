package com.natanael.ntplay.tv

import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private fun qualityOf(p: ExoPlayer): String {
    val f = p.videoFormat ?: return ""
    val h = minOf(f.height, f.width).let { if (it <= 0) f.height else it }
    val res = when {
        h >= 1600 -> "4K"
        h >= 1000 -> "Full HD 1080p"
        h >= 700 -> "HD 720p"
        h > 0 -> "SD ${h}p"
        else -> ""
    }
    val m = f.sampleMimeType ?: ""
    val c = when {
        m.contains("hevc") -> "H.265"
        m.contains("avc") -> "H.264"
        m.contains("av01") -> "AV1"
        m.contains("vp9") -> "VP9"
        m.contains("mpeg2") -> "MPEG-2"
        else -> ""
    }
    val fps = if (f.frameRate > 0) "${f.frameRate.roundToInt()} fps" else ""
    return listOf(res, c, fps).filter { it.isNotEmpty() }.joinToString(" · ")
}

private fun langName(l: String?): String = when (l?.lowercase()?.take(2)) {
    null, "", "un" -> "Faixa"
    "pt" -> "Português"; "en" -> "Inglês"; "es" -> "Espanhol"; "ja" -> "Japonês"; "ko" -> "Coreano"
    "fr" -> "Francês"; "de" -> "Alemão"; "it" -> "Italiano"
    else -> l.uppercase()
}

@Composable
fun PlayerScreen(st: AppState, req: PlayReq) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
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
    var nSubs by remember(req.ep.url) { mutableIntStateOf(0) }
    var nAudio by remember(req.ep.url) { mutableIntStateOf(0) }
    val rootFocus = remember { FocusRequester() }
    val playFocus = remember { FocusRequester() }

    val player = remember(req.ep.url) {
        val ds = OkHttpDataSource.Factory(Net.client).setUserAgent(Util.UA)
        val ex = DefaultExtractorsFactory()
            .setConstantBitrateSeekingEnabled(true)
            .setTsExtractorFlags(DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES or DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS)
        val rf = DefaultRenderersFactory(ctx).setEnableDecoderFallback(true).setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
        // buffer maior = menos travadas; início rápido (1,5 s) e recuperação com 4 s de folga
        val lc = DefaultLoadControl.Builder()
            .setBufferDurationsMs(if (live) 20_000 else 30_000, if (live) 60_000 else 120_000, 1_500, 4_000)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()
        val p = ExoPlayer.Builder(ctx, rf).setLoadControl(lc).setMediaSourceFactory(DefaultMediaSourceFactory(ctx, ex).setDataSourceFactory(ds)).build()
        p.trackSelectionParameters = p.trackSelectionParameters.buildUpon().setPreferredAudioLanguage("pt").build()
        p
    }

    fun load(u: String, at: Long) {
        val mi = MediaItem.Builder().setUri(u).apply { if (u.lowercase().contains(".m3u8")) setMimeType(MimeTypes.APPLICATION_M3U8) }.build()
        player.setMediaItem(mi, at)
        player.playWhenReady = true
        player.prepare()
    }

    LaunchedEffect(player) { load(url, if (req.resume > 0) req.resume else 0L) }

    fun show() { ui = true; lastAct = System.currentTimeMillis() }
    fun close() { st.saveProg(req, player.currentPosition, player.duration.coerceAtLeast(0)); st.closePlayer() }
    fun seek(d: Long) { if (!live) player.seekTo((player.currentPosition + d).coerceAtLeast(0)) }
    fun toggle() { if (player.isPlaying) player.pause() else player.play() }
    fun zap(next: Boolean) { val n = if (next) st.next(req) else st.prev(req); if (n != null) st.play = n }

    fun groups(type: Int) = player.currentTracks.groups.filter { it.type == type && it.isSupported }
    fun cycleSub() {
        val g = groups(C.TRACK_TYPE_TEXT)
        if (g.isEmpty()) {
            st.toast("Sem legenda separada neste conteúdo. Se a legenda está \"gravada\" na imagem ([LEG]), escolha a versão dublada do canal/filme.")
            return
        }
        val cur = g.indexOfFirst { it.isSelected }
        val next = cur + 1
        val b = player.trackSelectionParameters.buildUpon()
        if (next >= g.size) {
            b.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).clearOverridesOfType(C.TRACK_TYPE_TEXT); subLabel = "Desligada"
        } else {
            b.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false).setOverrideForType(TrackSelectionOverride(g[next].mediaTrackGroup, 0))
            subLabel = langName(g[next].getTrackFormat(0).language)
        }
        player.trackSelectionParameters = b.build()
    }
    fun cycleAudio() {
        val g = groups(C.TRACK_TYPE_AUDIO)
        if (g.size < 2) { st.toast("Só existe um áudio neste conteúdo."); return }
        val cur = g.indexOfFirst { it.isSelected }
        val next = (cur + 1) % g.size
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().setOverrideForType(TrackSelectionOverride(g[next].mediaTrackGroup, 0)).build()
        audioLabel = langName(g[next].getTrackFormat(0).language)
    }

    BackHandler(true) { if (ui && err.isEmpty() && playing) ui = false else close() }

    DisposableEffect(player) {
        val l = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) { retries = 0; quality = qualityOf(player) }
                if (state == Player.STATE_ENDED) { if (live) { player.seekToDefaultPosition(); player.prepare() } else st.onEnded(req) }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }

            override fun onTracksChanged(tracks: Tracks) {
                quality = qualityOf(player)
                nSubs = groups(C.TRACK_TYPE_TEXT).size
                val a = groups(C.TRACK_TYPE_AUDIO); nAudio = a.size
                a.firstOrNull { it.isSelected }?.let { audioLabel = langName(it.getTrackFormat(0).language) }
                groups(C.TRACK_TYPE_TEXT).firstOrNull { it.isSelected }?.let { subLabel = langName(it.getTrackFormat(0).language) }
            }

            override fun onPlayerError(e: PlaybackException) {
                if (e.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) { player.seekToDefaultPosition(); player.prepare(); return }
                val dec = e.errorCode in listOf(
                    PlaybackException.ERROR_CODE_DECODER_INIT_FAILED, PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
                    PlaybackException.ERROR_CODE_DECODING_FAILED, PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
                    PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED
                )
                val max = if (live) 8 else 4
                if (!dec && retries < max) {
                    retries++
                    val at = if (live) 0L else player.currentPosition
                    scope.launch {
                        delay(if (retries < 3) 800L else 2000L)
                        // canal ao vivo .ts falhando: tenta a versão HLS (.m3u8) do mesmo canal
                        if (live && retries == 3 && url.contains("/live/") && url.endsWith(".ts")) url = url.removeSuffix(".ts") + ".m3u8"
                        load(url, at)
                    }
                    return
                }
                err = if (dec) "Este aparelho não consegue decodificar este vídeo (comum em 4K / H.265). Tente a versão Full HD/HD ou abra em outro player."
                else "Não foi possível reproduzir (" + e.errorCodeName + "). O servidor pode estar fora do ar para este item."
                ui = true
            }
        }
        player.addListener(l)
        onDispose {
            st.saveProg(req, player.currentPosition, player.duration.coerceAtLeast(0))
            player.removeListener(l)
            player.release()
        }
    }

    // posição / progresso
    LaunchedEffect(player) {
        var n = 0
        while (true) {
            pos = player.currentPosition; dur = player.duration.coerceAtLeast(0)
            delay(500)
            if (++n % 10 == 0 && player.isPlaying && !live) st.saveProg(req, player.currentPosition, player.duration.coerceAtLeast(0))
        }
    }
    // esconde o painel sozinho
    LaunchedEffect(ui, lastAct, err) {
        if (ui && err.isEmpty()) { delay(5000); if (player.isPlaying) ui = false }
    }
    // foco: painel aberto -> botão play; fechado -> tela
    LaunchedEffect(ui) {
        delay(60)
        try { if (ui) playFocus.requestFocus() else rootFocus.requestFocus() } catch (e: Exception) { }
    }

    Box(
        Modifier.fillMaxSize().background(Color.Black)
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                lastAct = System.currentTimeMillis()
                when (e.key) {
                    Key.MediaPlayPause, Key.Spacebar -> { toggle(); show(); return@onPreviewKeyEvent true }
                    Key.MediaPlay -> { player.play(); return@onPreviewKeyEvent true }
                    Key.MediaPause -> { player.pause(); show(); return@onPreviewKeyEvent true }
                    Key.MediaFastForward -> { seek(30_000); show(); return@onPreviewKeyEvent true }
                    Key.MediaRewind -> { seek(-30_000); show(); return@onPreviewKeyEvent true }
                    Key.ChannelUp, Key.PageUp, Key.MediaNext -> { zap(true); return@onPreviewKeyEvent true }
                    Key.ChannelDown, Key.PageDown, Key.MediaPrevious -> { zap(false); return@onPreviewKeyEvent true }
                }
                if (ui) return@onPreviewKeyEvent false
                when (e.key) {
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> { show(); true }
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
            factory = { c ->
                PlayerView(c).apply {
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    keepScreenOn = true
                    isFocusable = false
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                }
            },
            update = { v -> v.player = player }
        )
        // clique/toque do mouse abre e fecha o painel
        Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { if (ui) ui = false else show() } })

        if (buffering && err.isEmpty()) Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(Modifier.size(40.dp), color = Pink, strokeWidth = 3.dp)
            Spacer(Modifier.height(8.dp))
            Text(if (retries > 0) "Reconectando… ($retries)" else "Carregando…", color = Color.White, fontSize = 13.sp, modifier = Modifier.background(Color(0x99000000), RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 4.dp))
        }

        if (ui) {
            // topo: título, qualidade, relógio
            Row(
                Modifier.align(Alignment.TopStart).fillMaxWidth().background(Brush.verticalGradient(listOf(Color(0xCC000000), Color.Transparent))).padding(start = 40.dp, end = 40.dp, top = 22.dp, bottom = 30.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (live) { LiveBadge(Modifier); Spacer(Modifier.width(10.dp)) }
                Text(
                    showTitle(req.item) + if (req.item.kind == 's') "  ·  T${req.ep.s} E${req.ep.e}" else "",
                    color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(Modifier.width(10.dp))
                if (quality.isNotEmpty()) Text(quality, color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.background(Cyan, RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 3.dp))
                Spacer(Modifier.weight(1f))
                Text(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
            // base: progresso + botões compactos
            Column(
                Modifier.align(Alignment.BottomStart).fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xDD000000)))).padding(start = 40.dp, end = 40.dp, top = 30.dp, bottom = 22.dp)
            ) {
                if (!live && dur > 0) Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(Util.fmt(pos), color = Color.White, fontSize = 11.sp)
                    Box(Modifier.weight(1f).padding(horizontal = 10.dp).height(4.dp).background(Color(0x55FFFFFF), RoundedCornerShape(2.dp))) {
                        Box(Modifier.fillMaxWidth((pos.toFloat() / dur).coerceIn(0f, 1f)).fillMaxHeight().background(Pink, RoundedCornerShape(2.dp)))
                    }
                    Text(Util.fmt(dur), color = Color.White, fontSize = 11.sp)
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (live) TvButton("▲ Canal anterior", Btn, 11.sp, compact = true) { zap(false) }
                    else TvButton("⏪ 10s", Btn, 11.sp, compact = true) { seek(-10_000); show() }
                    TvButton(if (playing) "⏸ Pausar" else "▶ Continuar", Pink, 11.sp, compact = true, modifier = Modifier.focusRequester(playFocus)) { toggle(); show() }
                    if (live) TvButton("▼ Próximo canal", Btn, 11.sp, compact = true) { zap(true) }
                    else {
                        TvButton("10s ⏩", Btn, 11.sp, compact = true) { seek(10_000); show() }
                        if (st.next(req) != null) TvButton("⏭ Próximo", Btn, 11.sp, compact = true) { zap(true) }
                    }
                    TvButton("CC Legenda: $subLabel" + if (nSubs > 0) "" else " (—)", Btn, 11.sp, compact = true) { cycleSub(); show() }
                    if (nAudio > 1) TvButton("🔊 Áudio: $audioLabel", Btn, 11.sp, compact = true) { cycleAudio(); show() }
                    TvButton(if (st.favs.contains(req.item.key)) "★ Favorito" else "☆ Favoritar", Btn, 11.sp, compact = true) { st.toggleFav(req.item.key) }
                    TvButton("✕ Sair", Btn, 11.sp, compact = true) { close() }
                }
                if (live) Text("Dica: ▲/▼ no controle troca de canal com o painel fechado", color = Color(0x99FFFFFF), fontSize = 10.sp, modifier = Modifier.padding(top = 8.dp))
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
                TvButton("↻ Tentar de novo", Btn) { err = ""; retries = 0; load(url, if (live) 0L else player.currentPosition) }
                TvButton("‹ Voltar", Btn) { close() }
            }
        }
    }
}

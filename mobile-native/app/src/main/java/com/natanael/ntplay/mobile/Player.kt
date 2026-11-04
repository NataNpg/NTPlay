package com.natanael.ntplay.mobile

import android.app.Activity
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.natanael.ntplay.core.AppState
import com.natanael.ntplay.core.Epg
import com.natanael.ntplay.core.LiveLoopDataSource
import com.natanael.ntplay.core.Net
import com.natanael.ntplay.core.PlayReq
import com.natanael.ntplay.core.Util
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlinx.coroutines.launch

private fun fmt(ms: Long): String {
    if (ms <= 0) return "0:00"
    val s = ms / 1000
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

/**
 * Player de toque.
 *
 * O miolo é o mesmo do player da TV, que já está provado: LiveLoopDataSource para os canais que
 * o servidor entrega em pedaços de ~20 s, reconexão automática, e a tentativa de .m3u8 quando o
 * .ts falha. O que é escrito do zero aqui são os controles: na TV são teclas do controle, aqui
 * são toque, duplo toque e arrastar.
 *
 * Não existe troca de modo de tela (HDMI) como na TV — isso só faz sentido ligado a um televisor.
 */
@Composable
fun PlayerScreen(st: AppState, req: PlayReq) {
    val ctx = LocalContext.current
    val activity = ctx as? Activity
    val live = req.item.kind == 'l'
    var url by remember(req.ep.url) { mutableStateOf(req.ep.url) }

    var ui by remember { mutableStateOf(true) }
    var lastAct by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var err by remember(req.ep.url) { mutableStateOf("") }
    var retries by remember(req.ep.url) { mutableIntStateOf(0) }
    var pos by remember { mutableLongStateOf(0L) }
    var dur by remember { mutableLongStateOf(0L) }
    var playing by remember { mutableStateOf(true) }
    var toast by remember { mutableStateOf("") }
    var epgNow by remember(req.ep.url) { mutableStateOf<String?>(null) }

    val player = remember(req.ep.url) {
        val okF = OkHttpDataSource.Factory(Net.client).setUserAgent(Util.UA)
        // canal ao vivo: emenda os pedaços que o servidor fecha a cada ~20 s
        val ds: DataSource.Factory =
            if (live) DataSource.Factory { LiveLoopDataSource(okF.createDataSource(), req.ep.url) } else okF
        val ex = DefaultExtractorsFactory()
            .setConstantBitrateSeekingEnabled(true)
            .setTsExtractorFlags(
                DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES or
                    DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS,
            )
        val rf = DefaultRenderersFactory(ctx)
            .setEnableDecoderFallback(true)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
        val minV = when (st.store.bufferMode) { 1 -> 25_000; 2 -> 45_000; else -> 15_000 }
        val maxV = when (st.store.bufferMode) { 1 -> 60_000; 2 -> 90_000; else -> 50_000 }
        val lc = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                if (live) 20_000 else minV,
                if (live) 60_000 else maxV,
                1_500,
                if (live) 4_000 else 5_000,
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()
        val p = ExoPlayer.Builder(ctx, rf)
            .setLoadControl(lc)
            .setMediaSourceFactory(DefaultMediaSourceFactory(ctx, ex).setDataSourceFactory(ds))
            .build()
        p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
            .setPreferredAudioLanguage("pt")
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !st.subs)
            .apply { if (st.subs) setPreferredTextLanguage("pt") }
            .build()
        p
    }

    fun load(u: String, at: Long) {
        val mi = MediaItem.Builder().setUri(u)
            .apply { if (u.lowercase().contains(".m3u8")) setMimeType(MimeTypes.APPLICATION_M3U8) }
            .build()
        player.setMediaItem(mi, at)
        player.playWhenReady = true
        player.prepare()
    }

    fun show() { ui = true; lastAct = System.currentTimeMillis() }
    fun seek(d: Long) {
        if (live) return
        player.seekTo((player.currentPosition + d).coerceAtLeast(0))
        show()
    }
    fun save() = st.saveProg(req, player.currentPosition, player.duration.coerceAtLeast(0))
    fun leave() { save(); st.closePlayer() }

    // paisagem, tela cheia de verdade (sem barras do sistema) e tela acesa enquanto toca.
    // As barras importam: em paisagem, com barra de navegação por botões, elas cobrem os
    // controles de baixo (CC, Áudio) e o ‹ Voltar do topo.
    DisposableEffect(Unit) {
        val old = activity?.requestedOrientation
        activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val insets = activity?.window?.let { w ->
            WindowCompat.getInsetsController(w, w.decorView).apply {
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                hide(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose {
            insets?.show(WindowInsetsCompat.Type.systemBars())
            activity?.requestedOrientation =
                old ?: android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            activity?.window?.attributes = activity?.window?.attributes?.apply {
                screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            }
        }
    }

    // O app indo para trás precisa PARAR o vídeo e guardar o ponto. Sem isto, apertar Home no
    // meio de um filme deixa o vídeo baixando em segundo plano por tempo indefinido — gastando
    // dados móveis que ninguém pediu e, em conta de 1 tela, prendendo a conexão e impedindo a TV
    // de tocar. Áudio em segundo plano está fora do escopo desta versão de propósito.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, player) {
        var wasPlaying = true
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_PAUSE -> {
                    wasPlaying = player.isPlaying
                    save()
                    player.pause()
                }
                Lifecycle.Event.ON_RESUME -> if (wasPlaying) player.play()
                else -> Unit
            }
        }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }

    LaunchedEffect(player) { load(url, if (req.resume > 0) req.resume else 0L) }

    // relógio do player e autoesconder dos controles
    LaunchedEffect(player) {
        while (true) {
            pos = player.currentPosition
            dur = player.duration.coerceAtLeast(0)
            playing = player.isPlaying
            if (ui && System.currentTimeMillis() - lastAct > 4000 && err.isEmpty()) ui = false
            delay(500)
        }
    }

    // guarda o ponto de tempo em tempo, para não perder se o app for morto
    LaunchedEffect(req.ep.url) {
        while (true) {
            delay(20_000)
            if (!live) save()
        }
    }

    LaunchedEffect(req.ep.url, st.epgVer) {
        if (!live) { epgNow = null; return@LaunchedEffect }
        while (true) {
            epgNow = Epg.nowNext(req.item).first?.title
            delay(60_000)
        }
    }

    LaunchedEffect(toast) {
        if (toast.isNotEmpty()) { delay(3000); toast = "" }
    }

    DisposableEffect(player) {
        val l = object : Player.Listener {
            override fun onPlaybackStateChanged(s: Int) {
                if (s == Player.STATE_ENDED) st.onEnded(req)
            }

            override fun onPlayerError(e: PlaybackException) {
                if (e.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                    player.seekToDefaultPosition(); player.prepare(); return
                }
                val dec = e.errorCode in listOf(
                    PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
                    PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
                    PlaybackException.ERROR_CODE_DECODING_FAILED,
                    PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
                    PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
                )
                val max = if (live) 8 else 4
                if (!dec && retries < max) {
                    retries++
                    val at = if (live) 0L else player.currentPosition
                    st.scope.launch {
                        delay(if (retries < 3) 800L else 2000L)
                        // .ts falhando num canal: tenta a versão HLS do mesmo canal
                        if (live && retries == 3 && url.contains("/live/") && url.endsWith(".ts")) {
                            url = url.removeSuffix(".ts") + ".m3u8"
                        }
                        load(url, at)
                    }
                    return
                }
                err = if (dec)
                    "Este aparelho não consegue decodificar este vídeo (comum em 4K / H.265). Tente a versão Full HD/HD ou abra em outro player."
                else
                    "Não foi possível reproduzir (" + e.errorCodeName + "). O servidor pode estar fora do ar para este item."
                ui = true
            }
        }
        player.addListener(l)
        onDispose { player.removeListener(l); save(); player.release() }
    }

    BackHandler { leave() }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = {
                PlayerView(it).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    this.player = player
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        // ---------------------------------------------------------------- gestos
        // Tudo num nó por metade, nada de camada por cima: em Compose, um Box irmão sobreposto
        // com pointerInput ganha o hit-test e os de baixo não recebem evento nenhum — foi assim
        // que, numa versão anterior, os controles do canal ao vivo ficaram inalcançáveis.
        // O eixo do arraste é decidido depois dos primeiros 16 px: horizontal troca de canal,
        // vertical ajusta brilho (esquerda) ou volume (direita).
        Row(Modifier.fillMaxSize()) {
            GestureHalf(
                key = req.ep.url,
                left = true,
                live = live,
                onTap = { if (ui) ui = false else show() },
                onDoubleTap = { seek(-10_000); toast = "⏪ 10s" },
                onVertical = { dy ->
                    val w = activity?.window
                    if (w != null) {
                        val a = w.attributes
                        val cur = if (a.screenBrightness < 0) 0.5f else a.screenBrightness
                        val v = (cur - dy / 600f).coerceIn(0.02f, 1f)
                        a.screenBrightness = v
                        w.attributes = a
                        toast = "☀ ${(v * 100).toInt()}%"
                    }
                },
                onZap = { forward ->
                    val n = if (forward) st.next(req) else st.prev(req)
                    if (n != null) { save(); st.play = n }
                    else toast = "Não há outro canal nesta categoria"
                },
            )
            GestureHalf(
                key = req.ep.url,
                left = false,
                live = live,
                onTap = { if (ui) ui = false else show() },
                onDoubleTap = { seek(10_000); toast = "10s ⏩" },
                onVertical = { dy ->
                    val am = ctx.getSystemService(android.content.Context.AUDIO_SERVICE) as AudioManager
                    val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                    val cur = am.getStreamVolume(AudioManager.STREAM_MUSIC)
                    val step = if (dy < -10) 1 else if (dy > 10) -1 else 0
                    if (step != 0) {
                        val v = (cur + step).coerceIn(0, max)
                        am.setStreamVolume(AudioManager.STREAM_MUSIC, v, 0)
                        toast = "🔊 ${v * 100 / max}%"
                    }
                },
                onZap = { forward ->
                    val n = if (forward) st.next(req) else st.prev(req)
                    if (n != null) { save(); st.play = n }
                    else toast = "Não há outro canal nesta categoria"
                },
            )
        }

        if (toast.isNotEmpty()) {
            Text(
                toast,
                Modifier.align(Alignment.Center)
                    .background(Color(0xCC000000), RoundedCornerShape(10.dp))
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold,
            )
        }

        if (err.isNotEmpty()) {
            Column(
                Modifier.align(Alignment.Center).padding(24.dp)
                    .background(Color(0xDD0B1024), RoundedCornerShape(14.dp)).padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(err, color = Danger, fontSize = 13.sp, lineHeight = 18.sp)
                // Em conta de 1 tela o erro cru mente por omissão: o usuário leria "o servidor
                // pode estar fora do ar" e desconfiaria do provedor, quando a causa é a TV
                // ocupando a única conexão.
                val hint = remember(err) {
                    st.store.lists().firstNotNullOfOrNull { playbackErrorHint(st.store.account(it.id)) }
                }
                if (hint != null) {
                    Spacer(Modifier.height(10.dp))
                    Text("⚠ $hint", color = Star, fontSize = 12.sp, lineHeight = 17.sp)
                }
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CtlBtn("Abrir em outro player") {
                        try {
                            ctx.startActivity(
                                Intent(Intent.ACTION_VIEW)
                                    .setDataAndType(Uri.parse(url), "video/*")
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        } catch (e: Throwable) {
                            toast = "Nenhum player instalado (tente VLC ou MX Player)"
                        }
                    }
                    CtlBtn("Sair") { leave() }
                }
            }
        }

        if (ui) Controls(
            st = st, req = req, live = live, playing = playing, pos = pos, dur = dur,
            epgNow = epgNow,
            onToggle = {
                if (player.isPlaying) player.pause() else player.play()
                show()
            },
            onSeekTo = { f ->
                if (!live && dur > 0) { player.seekTo((dur * f).toLong()); show() }
            },
            onSeek = ::seek,
            onNext = {
                val n = st.next(req)
                if (n != null) { save(); st.play = n } else toast = "Não há próximo"
            },
            onSubs = {
                val g = player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_TEXT && it.isSupported }
                if (g.isEmpty()) {
                    toast = "Sem legenda separada neste conteúdo"
                } else {
                    val sel = g.indexOfFirst { it.isSelected }
                    val next = sel + 1
                    player.trackSelectionParameters = if (next >= g.size) {
                        player.trackSelectionParameters.buildUpon()
                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
                    } else {
                        player.trackSelectionParameters.buildUpon()
                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                            .setOverrideForType(TrackSelectionOverride(g[next].mediaTrackGroup, 0)).build()
                    }
                    toast = if (next >= g.size) "Legenda desligada" else "Legenda ${next + 1}"
                }
                show()
            },
            onAudio = {
                val g = player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO && it.isSupported }
                if (g.size < 2) {
                    toast = "Só uma faixa de áudio neste conteúdo"
                } else {
                    val next = (g.indexOfFirst { it.isSelected } + 1) % g.size
                    player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                        .setOverrideForType(TrackSelectionOverride(g[next].mediaTrackGroup, 0)).build()
                    toast = "Áudio ${next + 1} de ${g.size}"
                }
                show()
            },
            onSkipIntro = {
                val add = st.store.skipIntro * 1000L
                player.seekTo(player.currentPosition + add)
                toast = "Abertura pulada"
                show()
            },
            onLeave = ::leave,
        )
    }
}

/**
 * Metade da tela do player, com toque, duplo toque e arraste no MESMO nó de ponteiro.
 *
 * O eixo é decidido uma vez por gesto, depois de 16 px de movimento, e não muda até o dedo
 * levantar — sem isso, um arraste levemente torto alternaria entre trocar de canal e mexer no
 * volume no meio do caminho.
 */
@Composable
private fun RowScope.GestureHalf(
    key: String,
    left: Boolean,
    live: Boolean,
    onTap: () -> Unit,
    onDoubleTap: () -> Unit,
    onVertical: (Float) -> Unit,
    onZap: (Boolean) -> Unit,
) {
    Box(
        Modifier.weight(1f).fillMaxHeight()
            .pointerInput(key) {
                detectTapGestures(onDoubleTap = { onDoubleTap() }, onTap = { onTap() })
            }
            .pointerInput(key, live) {
                var axis = 0          // 0 = ainda não decidido, 1 = horizontal, 2 = vertical
                var ax = 0f
                var ay = 0f
                var zap = 0f
                detectDragGestures(
                    onDragStart = { axis = 0; ax = 0f; ay = 0f; zap = 0f },
                    onDragEnd = { axis = 0; zap = 0f },
                    onDragCancel = { axis = 0; zap = 0f },
                ) { _, d ->
                    if (axis == 0) {
                        ax += d.x; ay += d.y
                        if (abs(ax) > 16f || abs(ay) > 16f) axis = if (abs(ax) > abs(ay)) 1 else 2
                    }
                    when (axis) {
                        1 -> if (live) {
                            zap += d.x
                            if (abs(zap) > 220f) { onZap(zap < 0); zap = 0f }
                        }
                        2 -> onVertical(d.y)
                    }
                }
            },
    )
}

@Composable
private fun CtlBtn(label: String, onClick: () -> Unit) {
    Text(
        label,
        Modifier.background(Btn, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        color = Color.White, fontSize = 13.sp,
    )
}

@Composable
private fun Controls(
    st: AppState,
    req: PlayReq,
    live: Boolean,
    playing: Boolean,
    pos: Long,
    dur: Long,
    epgNow: String?,
    onToggle: () -> Unit,
    onSeekTo: (Float) -> Unit,
    onSeek: (Long) -> Unit,
    onNext: () -> Unit,
    onSubs: () -> Unit,
    onAudio: () -> Unit,
    onSkipIntro: () -> Unit,
    onLeave: () -> Unit,
) {
    val fav = st.favs.contains(req.item.key)
    Box(Modifier.fillMaxSize()) {
        // topo: nome e, no ao vivo, o que está passando
        Column(
            Modifier.align(Alignment.TopStart).fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color(0xCC000000), Color.Transparent)))
                .padding(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "‹",
                    Modifier.clickable(onClick = onLeave).padding(horizontal = 8.dp),
                    color = Color.White, fontSize = 26.sp,
                )
                Spacer(Modifier.width(6.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        Util.cleanT(req.item.title), color = Color.White,
                        fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                    )
                    if (live && !epgNow.isNullOrBlank()) {
                        Text(epgNow, color = Cyan, fontSize = 12.sp, maxLines = 1)
                    } else if (!live && req.ep.e > 0) {
                        Text("Episódio ${req.ep.e}", color = Muted, fontSize = 12.sp)
                    }
                }
                Text(
                    if (fav) "★" else "☆",
                    Modifier.clickable { st.toggleFav(req.item.key) }.padding(8.dp),
                    color = if (fav) Star else Color.White, fontSize = 20.sp,
                )
            }
        }

        // baixo: barra de tempo e botões
        Column(
            Modifier.align(Alignment.BottomStart).fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xDD000000))))
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            if (!live && dur > 0) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(fmt(pos), color = Color.White, fontSize = 11.sp)
                    Box(
                        Modifier.weight(1f).padding(horizontal = 10.dp).height(22.dp)
                            .pointerInput(dur) {
                                detectTapGestures { o -> onSeekTo((o.x / size.width).coerceIn(0f, 1f)) }
                            }
                            .pointerInput(dur) {
                                detectHorizontalDragGestures { ch, _ ->
                                    onSeekTo((ch.position.x / size.width).coerceIn(0f, 1f))
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(Modifier.fillMaxWidth().height(4.dp).background(Color(0x55FFFFFF), RoundedCornerShape(2.dp)))
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                            Box(
                                Modifier.fillMaxWidth((pos.toFloat() / dur).coerceIn(0f, 1f))
                                    .height(4.dp).background(Purple, RoundedCornerShape(2.dp)),
                            )
                        }
                    }
                    Text(fmt(dur), color = Color.White, fontSize = 11.sp)
                }
                Spacer(Modifier.height(8.dp))
            } else if (live) {
                Text("● AO VIVO", color = Color(0xFFFF3B3B), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!live) CtlBtn("⏪10") { onSeek(-10_000) }
                CtlBtn(if (playing) "⏸" else "▶", onToggle)
                if (!live) CtlBtn("10⏩") { onSeek(10_000) }
                if (!live && st.store.skipIntro > 0) CtlBtn("Abertura", onSkipIntro)
                if (!live && req.item.kind == 's') CtlBtn("Próximo", onNext)
                Spacer(Modifier.weight(1f))
                CtlBtn("CC", onSubs)
                CtlBtn("Áudio", onAudio)
            }
        }
    }
}

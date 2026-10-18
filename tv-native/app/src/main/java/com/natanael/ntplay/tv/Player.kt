package com.natanael.ntplay.tv

import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay

@Composable
fun PlayerScreen(st: AppState, req: PlayReq) {
    val ctx = LocalContext.current
    var err by remember(req.ep.url) { mutableStateOf("") }
    var retries by remember(req.ep.url) { mutableStateOf(0) }
    val live = req.item.kind == 'l'

    val player = remember(req.ep.url) {
        val ds = OkHttpDataSource.Factory(Net.client).setUserAgent(Util.UA)
        val rf = DefaultRenderersFactory(ctx).setEnableDecoderFallback(true)
        val p = ExoPlayer.Builder(ctx, rf).setMediaSourceFactory(DefaultMediaSourceFactory(ctx).setDataSourceFactory(ds)).build()
        val mi = MediaItem.Builder().setUri(req.ep.url).apply { if (req.ep.url.lowercase().contains(".m3u8")) setMimeType(MimeTypes.APPLICATION_M3U8) }.build()
        p.setMediaItem(mi, if (req.resume > 0) req.resume else 0L)
        p.playWhenReady = true
        p.prepare()
        p
    }

    BackHandler(true) {
        st.saveProg(req, player.currentPosition, player.duration.coerceAtLeast(0))
        st.closePlayer()
    }

    DisposableEffect(player) {
        val l = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) st.onEnded(req)
            }

            override fun onPlayerError(e: PlaybackException) {
                if (e.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                    player.seekToDefaultPosition(); player.prepare(); return
                }
                if (retries < 2) { retries++; player.prepare(); return }
                err = "Não foi possível reproduzir (" + e.errorCodeName + "). Tente outro item."
            }
        }
        player.addListener(l)
        onDispose {
            st.saveProg(req, player.currentPosition, player.duration.coerceAtLeast(0))
            player.removeListener(l)
            player.release()
        }
    }

    LaunchedEffect(player) {
        while (true) {
            delay(5000)
            if (player.isPlaying && !live) st.saveProg(req, player.currentPosition, player.duration.coerceAtLeast(0))
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { c ->
                PlayerView(c).apply {
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    this.player = player
                    useController = true
                    controllerShowTimeoutMs = 4000
                    keepScreenOn = true
                    isFocusable = true
                    isFocusableInTouchMode = true
                    requestFocus()
                }
            }
        )
        Text(
            req.item.title + if (req.item.kind == 's') "  •  T${req.ep.s} E${req.ep.e}" else "",
            Modifier.align(Alignment.TopStart).padding(16.dp), color = Color(0xCCFFFFFF), fontSize = 13.sp
        )
        if (err.isNotEmpty()) Text(err, Modifier.align(Alignment.Center).background(Color(0xCC000000)).padding(16.dp), color = Color.White, fontSize = 14.sp)
    }
}

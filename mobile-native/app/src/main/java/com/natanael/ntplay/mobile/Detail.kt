package com.natanael.ntplay.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.natanael.ntplay.core.AppState
import com.natanael.ntplay.core.Ep
import com.natanael.ntplay.core.Util

/** Tela do título: capa, sinopse e episódios. Entra por cima das abas, sai com o voltar. */
@Composable
fun DetailScreen(st: AppState) {
    val item = st.detail ?: return
    val info = remember(item.key) { st.store.info(item.key) }
    val fav = st.favs.contains(item.key)
    var season by remember(item.key) { mutableIntStateOf(0) }

    LaunchedEffect(item.key) { if (st.needsEps(item)) st.loadEps(item) }

    // st.epsVer muda quando os episódios chegam do servidor, e é isso que recompõe a lista
    val seasons = remember(item.key, st.epsVer) {
        item.eps.map { it.s }.distinct().sorted()
    }
    val eps = remember(item.key, st.epsVer, season) {
        if (seasons.size <= 1) item.eps.toList()
        else item.eps.filter { it.s == seasons.getOrElse(season) { 0 } }
    }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 10f)) {
                if (item.logo.isNotEmpty()) {
                    AsyncImage(
                        item.logo, item.title,
                        Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
                    )
                } else {
                    Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(CardC, Bg2))))
                }
                // degradê embaixo para o título ficar legível sobre qualquer capa
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent, Bg)),
                    ),
                )
                Text(
                    "‹ Voltar",
                    Modifier.align(Alignment.TopStart).padding(12.dp)
                        .background(Color(0xAA000000), RoundedCornerShape(8.dp))
                        .clickable { st.back() }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    color = Color.White, fontSize = 14.sp,
                )
            }
        }

        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text(
                    Util.cleanT(item.title), color = Color.White,
                    fontSize = 20.sp, fontWeight = FontWeight.Bold,
                )
                val meta = listOfNotNull(
                    Util.yearOf(item.title).ifEmpty { info?.y?.ifEmpty { null } },
                    Util.quality(item.title).ifEmpty { null },
                    info?.r?.takeIf { it > 0 }?.let { "★ %.1f".format(it) },
                    Util.cleanG(item.group).ifEmpty { null },
                ).joinToString(" · ")
                if (meta.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(meta, color = Muted, fontSize = 12.sp)
                }

                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        {
                            val ep = st.startEp(item)
                            st.playItem(item, ep)
                        },
                        Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Purple),
                    ) {
                        val r = st.resumeOf(st.startEp(item))
                        Text(
                            if (r > 0) "▶ Continuar" else "▶ Assistir",
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Button(
                        { st.toggleFav(item.key) },
                        Modifier.height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = if (fav) Star else Btn),
                    ) { Text(if (fav) "★" else "☆", fontSize = 18.sp, color = if (fav) Color(0xFF05101F) else Color.White) }
                    Button(
                        { st.openGroups(item) },
                        Modifier.height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Btn),
                    ) { Text("＋", fontSize = 16.sp, color = Color.White) }
                }

                val ov = info?.ov ?: ""
                if (ov.isNotEmpty()) {
                    Spacer(Modifier.height(14.dp))
                    Text(ov, color = Color(0xFFD6DEF2), fontSize = 13.sp, lineHeight = 19.sp)
                }
            }
        }

        if (item.kind == 's') {
            if (seasons.size > 1) {
                item {
                    Spacer(Modifier.height(16.dp))
                    LazyRow(
                        Modifier.fillMaxWidth(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(seasons) { s ->
                            val i = seasons.indexOf(s)
                            Chip("Temporada $s", i == season) { season = i }
                        }
                    }
                }
            }
            if (st.epsLoading) {
                item {
                    Box(Modifier.fillMaxWidth().padding(28.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Purple)
                    }
                }
            }
            if (st.epsErr.isNotEmpty()) {
                item {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text(st.epsErr, color = Danger, fontSize = 13.sp)
                        Spacer(Modifier.height(10.dp))
                        Button(
                            { st.loadEps(item) },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Btn),
                        ) { Text("Tentar de novo") }
                    }
                }
            }
            items(eps, key = { it.url }) { ep -> EpRow(st, ep) }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun EpRow(st: AppState, ep: Ep) {
    val prog = st.progOf(ep)
    val done = prog?.done == true
    Column {
        Row(
            Modifier.fillMaxWidth()
                .clickable { st.detail?.let { st.playItem(it, ep) } }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(34.dp).background(if (done) Btn else CardC, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(if (done) "✓" else "${ep.e}", color = if (done) Cyan else Color.White, fontSize = 13.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (ep.e > 0) "Episódio ${ep.e}" else Util.cleanT(ep.name),
                    color = Color.White, fontSize = 14.sp,
                )
                if (prog != null && !prog.done && prog.t > 10_000 && prog.d > 0) {
                    val pct = (prog.t * 100 / prog.d).coerceIn(0, 100)
                    Spacer(Modifier.height(4.dp))
                    Box(Modifier.fillMaxWidth().height(3.dp).background(Btn)) {
                        Box(
                            Modifier.fillMaxWidth(pct / 100f).height(3.dp).background(Cyan),
                        )
                    }
                }
            }
        }
        Divider(color = Bg2, thickness = 1.dp)
    }
}

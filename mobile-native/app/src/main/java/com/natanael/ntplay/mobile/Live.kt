package com.natanael.ntplay.mobile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Divider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.natanael.ntplay.core.AppState
import com.natanael.ntplay.core.Epg

/**
 * Aba TV: lista vertical, não grade. No celular o nome do canal e o que está passando valem mais
 * que a logo grande, e dá para percorrer muitos canais com o dedo.
 */
@Composable
fun LiveScreen(st: AppState) {
    val lib = st.lib ?: return
    var query by remember { mutableStateOf("") }
    var cat by remember { mutableStateOf("") }

    // zera o filtro de categoria se a biblioteca for recarregada
    LaunchedEffect(lib) { cat = "" }

    val groups = lib.liveGroups
    val base = if (cat.isEmpty()) lib.live else groups.firstOrNull { it.first == cat }?.second ?: lib.live
    val shown = filterChannels(base, query)

    Column(Modifier.fillMaxSize()) {
        SearchField(query, "Buscar canal") { query = it }
        if (groups.size > 1) {
            CategoryStrip(categoriesOf(groups), cat) { cat = it }
        }
        if (shown.isEmpty()) {
            EmptyState(
                if (query.isNotEmpty()) "Nenhum canal com \"$query\"." else "Nenhum canal nesta categoria.",
            )
            return@Column
        }
        Text(
            "${shown.size} ${if (shown.size == 1) "canal" else "canais"}",
            Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            color = Muted, fontSize = 11.sp,
        )
        LazyColumn(Modifier.fillMaxSize()) {
            items(shown, key = { it.key }) { ch ->
                // st.epgVer muda quando a programação termina de carregar, e isso recompõe a linha
                val now = remember(ch.key, st.epgVer, Epg.ts) { channelNow(ch) }
                ChannelRow(ch, now) { st.playItem(ch) }
                Divider(color = Bg2, thickness = 1.dp, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

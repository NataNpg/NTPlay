package com.natanael.ntplay.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.natanael.ntplay.core.AppState
import com.natanael.ntplay.core.Item

/**
 * Aba Início: prateleiras. Prateleira sem item não é composta — melhor uma tela curta que uma
 * cheia de títulos vazios.
 */
@Composable
fun HomeScreen(st: AppState) {
    val lib = st.lib ?: return
    val cont = remember(st.progVer, lib) { st.continueList() }
    val favs = remember(st.favs, lib) {
        val byKey = lib.byKey
        st.favs.mapNotNull { byKey[it] }
    }
    val favMovies = favs.filter { it.kind == 'm' }
    val favSeries = favs.filter { it.kind == 's' }
    val favLive = favs.filter { it.kind == 'l' }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        if (cont.isNotEmpty()) {
            item { ShelfTitle("Continuar assistindo", cont.size) }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(cont, key = { it.first.key + it.second.url }) { (item, _) ->
                        Box {
                            PosterCard(item) { st.openDetail(item) }
                            Text(
                                "✕",
                                Modifier.align(Alignment.TopEnd).padding(8.dp)
                                    .background(Color(0xCC000000), CircleShape)
                                    .clickable { st.removeProg(item.key) }
                                    .padding(horizontal = 6.dp, vertical = 1.dp),
                                color = Color.White, fontSize = 11.sp,
                            )
                        }
                    }
                }
            }
        }

        // Em alta, Lançamentos e destaques vêm do Trending, que só devolve o que existe na lista
        for (row in st.trend) {
            if (row.items.isEmpty()) continue
            item(key = "t-" + row.title) { ShelfTitle(row.title, row.items.size) }
            item(key = "tr-" + row.title) { Shelf(row.items) { st.openDetail(it) } }
        }

        if (favLive.isNotEmpty()) {
            item { ShelfTitle("★ Canais favoritos", favLive.size) }
            item { Shelf(favLive) { st.playItem(it) } }
        }
        if (favMovies.isNotEmpty()) {
            item { ShelfTitle("★ Filmes favoritos", favMovies.size) }
            item { Shelf(favMovies) { st.openDetail(it) } }
        }
        if (favSeries.isNotEmpty()) {
            item { ShelfTitle("★ Séries favoritas", favSeries.size) }
            item { Shelf(favSeries) { st.openDetail(it) } }
        }

        // Sem internet o Trending vem vazio; aí pelo menos mostra o que a lista tem.
        if (cont.isEmpty() && st.trend.isEmpty() && favs.isEmpty()) {
            if (lib.movies.isNotEmpty()) {
                item { ShelfTitle("Filmes", lib.movies.size) }
                item { Shelf(lib.movies.take(30)) { st.openDetail(it) } }
            }
            if (lib.series.isNotEmpty()) {
                item { ShelfTitle("Séries", lib.series.size) }
                item { Shelf(lib.series.take(30)) { st.openDetail(it) } }
            }
        }
    }
}

@Composable
private fun Shelf(items: List<Item>, onPick: (Item) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        items(items, key = { it.key }) { it2 -> PosterCard(it2) { onPick(it2) } }
    }
}

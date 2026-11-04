package com.natanael.ntplay.mobile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.natanael.ntplay.core.AppState
import com.natanael.ntplay.core.Item

@Composable
fun MoviesScreen(st: AppState) {
    val lib = st.lib ?: return
    CatalogScreen(st, "FILMES", lib.movies, lib.movieGroups)
}

@Composable
fun SeriesScreen(st: AppState) {
    val lib = st.lib ?: return
    CatalogScreen(st, "SÉRIES", lib.series, lib.serieGroups)
}

/**
 * Grade de capas com fita de categorias e filtro por ano — a mesma tela serve Filmes e Séries.
 *
 * A posição da rolagem é guardada por `rememberSaveable`, então voltar dos detalhes cai onde o
 * usuário estava, e não no topo (na TV isso foi corrigido na 1.4.0; aqui nasce assim).
 */
@Composable
private fun CatalogScreen(
    st: AppState,
    tab: String,
    all: List<Item>,
    groups: List<Pair<String, List<Item>>>,
) {
    var cat by rememberSaveable(tab) { mutableStateOf("") }
    var year by rememberSaveable(tab) { mutableStateOf("") }
    var years by remember { mutableStateOf(false) }
    val gridState = rememberLazyGridState()

    // zera os filtros quando a BIBLIOTECA troca (lista atualizada), não a cada entrada na tela:
    // com `all` como chave isso rodava na composição inicial e apagava o que o rememberSaveable
    // tinha guardado. Comparar uma List<Item> grande por == também custa caro.
    LaunchedEffect(st.lib) { cat = ""; year = "" }

    val byCat = if (cat.isEmpty()) all else groups.firstOrNull { it.first == cat }?.second ?: all
    val shown = filterByYear(byCat, year)
    val anos = remember(byCat) { yearsOf(byCat) }

    Column(Modifier.fillMaxSize()) {
        if (groups.size > 1) CategoryStrip(categoriesOf(groups), cat) { cat = it }

        if (anos.isNotEmpty()) YearStrip(anos, year) { year = it }

        if (shown.isEmpty()) {
            EmptyState(
                if (year.isNotEmpty()) "Nada de $year nesta categoria." else "Nada nesta categoria.",
            )
            return@Column
        }

        Text(
            "${shown.size} ${if (tab == "FILMES") "filmes" else "séries"}",
            Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
            color = Muted, fontSize = 11.sp,
        )
        LazyVerticalGrid(
            GridCells.Fixed(3),
            Modifier.fillMaxSize(),
            state = gridState,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
        ) {
            items(shown, key = { it.key }) { it2 ->
                PosterCard(it2) {
                    // a fila alimenta "próximo" no player e o autoplay ao terminar
                    st.queue = shown
                    st.openDetail(it2)
                }
            }
        }
    }
}

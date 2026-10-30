package com.natanael.ntplay.mobile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import com.natanael.ntplay.core.Item
import kotlinx.coroutines.delay

/**
 * Aba Busca: filmes e séries (os animes estão dentro desses dois).
 *
 * Canal ao vivo de propósito não entra, igual à TV desde a 1.10.6 — é o que `AppState.search`
 * faz. Para achar canal por nome existe o campo da própria aba TV.
 */
@Composable
fun SearchScreen(st: AppState) {
    var q by remember { mutableStateOf("") }
    var hits by remember { mutableStateOf<List<Item>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }

    // espera o usuário parar de digitar: varrer a biblioteca a cada tecla trava a tela
    LaunchedEffect(q) {
        if (q.trim().length < 2) { hits = emptyList(); searching = false; return@LaunchedEffect }
        searching = true
        delay(300)
        hits = st.search(q)
        searching = false
    }

    val movies = hits.filter { it.kind == 'm' }
    val series = hits.filter { it.kind == 's' }

    Column(Modifier.fillMaxSize()) {
        SearchField(q, "Buscar filme ou série") { q = it }

        when {
            q.trim().length < 2 -> EmptyState("Digite ao menos duas letras para buscar.")
            searching -> EmptyState("Buscando…")
            hits.isEmpty() -> EmptyState("Nada encontrado para \"$q\".")
            else -> LazyVerticalGrid(
                GridCells.Fixed(3),
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            ) {
                if (movies.isNotEmpty()) {
                    item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(3) }) {
                        ShelfTitle("Filmes", movies.size)
                    }
                    items(movies, key = { "m" + it.key }) { it2 -> PosterCard(it2) { st.openDetail(it2) } }
                }
                if (series.isNotEmpty()) {
                    item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(3) }) {
                        ShelfTitle("Séries", series.size)
                    }
                    items(series, key = { "s" + it.key }) { it2 -> PosterCard(it2) { st.openDetail(it2) } }
                }
            }
        }
    }
}

package com.natanael.ntplay.mobile

import androidx.compose.runtime.Composable
import com.natanael.ntplay.core.AppState

// Esqueleto: preenchido na tarefa das abas de conteúdo.
@Composable
fun MoviesScreen(st: AppState) = EmptyState("Carregando filmes…")

@Composable
fun SeriesScreen(st: AppState) = EmptyState("Carregando séries…")

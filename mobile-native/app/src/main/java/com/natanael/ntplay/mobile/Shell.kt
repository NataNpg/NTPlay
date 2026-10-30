package com.natanael.ntplay.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.natanael.ntplay.core.AppState
import com.natanael.ntplay.core.TABS

/** Ícone de cada aba, na ordem de TABS (que o gen_core.py fixa para o celular). */
private fun iconFor(tab: String): ImageVector = when (tab) {
    "INÍCIO" -> Icons.Filled.Home
    "FILMES" -> Icons.Filled.Movie
    "SÉRIES" -> Icons.Filled.Tv
    "TV" -> Icons.Filled.LiveTv
    else -> Icons.Filled.Search
}

private fun labelFor(tab: String): String = when (tab) {
    "INÍCIO" -> "Início"
    "FILMES" -> "Filmes"
    "SÉRIES" -> "Séries"
    "TV" -> "TV"
    else -> "Busca"
}

/**
 * A casca do app: cabeçalho, barra de abas embaixo e o corpo da aba atual.
 *
 * O player e a tela de detalhes entram por cima de tudo (não são abas), para o usuário voltar
 * ao lugar onde estava com o botão de voltar do sistema.
 */
@Composable
fun App(st: AppState) {
    var menu by remember { mutableStateOf(false) }

    st.play?.let { req ->
        PlayerScreen(st, req)
        return
    }
    st.detail?.let {
        DetailScreen(st)
        return
    }

    Scaffold(
        containerColor = Bg,
        topBar = { TopBar(st) { menu = true } },
        bottomBar = {
            NavigationBar(containerColor = PanelC) {
                for (tab in TABS) {
                    NavigationBarItem(
                        selected = st.tab == tab,
                        onClick = { st.tab = tab },
                        icon = { Icon(iconFor(tab), labelFor(tab), Modifier.size(22.dp)) },
                        label = { Text(labelFor(tab), fontSize = 10.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.White,
                            selectedTextColor = Color.White,
                            indicatorColor = Purple,
                            unselectedIconColor = Muted,
                            unselectedTextColor = Muted,
                        ),
                    )
                }
            }
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            // lê as listas do disco só quando algo que as afeta muda, não a cada recomposição
            val listCount = remember(st.lib, st.busy) { st.store.lists().size }
            val empty = emptyMessage(st.tab, st.lib, st.busy, listCount)
            when {
                st.busy && st.lib == null -> Loading(st.status)
                empty != null -> EmptyState(empty, "☰ Abrir Listas") { menu = true }
                st.tab == "INÍCIO" -> HomeScreen(st)
                st.tab == "FILMES" -> MoviesScreen(st)
                st.tab == "SÉRIES" -> SeriesScreen(st)
                st.tab == "TV" -> LiveScreen(st)
                else -> SearchScreen(st)
            }
        }
    }

    if (menu) MenuSheet(st) { menu = false }
}

@Composable
private fun TopBar(st: AppState, onMenu: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Bg2).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.foundation.Image(
            androidx.compose.ui.res.painterResource(R.drawable.logo), "NTPlay",
            Modifier.size(28.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(labelFor(st.tab), color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))
        if (st.busy) {
            CircularProgressIndicator(Modifier.size(16.dp), color = Cyan, strokeWidth = 2.dp)
            Spacer(Modifier.width(10.dp))
        }
        IconButton(onMenu) {
            Text("☰", color = Color.White, fontSize = 20.sp)
        }
    }
}

@Composable
private fun Loading(status: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            CircularProgressIndicator(color = Purple)
            Spacer(Modifier.height(16.dp))
            Text(status, color = Muted, fontSize = 14.sp)
        }
    }
}

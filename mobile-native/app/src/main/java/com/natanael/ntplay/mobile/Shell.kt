package com.natanael.ntplay.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.natanael.ntplay.core.AppState

// Provisório: a casca de verdade (barra de abas, roteamento das telas) é a próxima tarefa.
// Aqui só para a tela de entrada ter para onde ir e esta tarefa compilar por si.
@Composable
fun App(st: AppState) {
    Box(Modifier.fillMaxSize().background(Bg), contentAlignment = Alignment.Center) {
        Text(st.status, Modifier.padding(24.dp), color = Muted, fontSize = 15.sp)
    }
}

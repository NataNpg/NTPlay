package com.natanael.ntplay.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** PIN de 4 dígitos com teclado numérico (funciona só com o controle remoto). */
@Composable
fun PinDialog(st: AppState) {
    var code by remember { mutableStateOf("") }
    var first by remember { mutableStateOf<String?>(null) }
    var msg by remember { mutableStateOf("") }
    val title = when (st.pinMode) { "set" -> if (first == null) "Crie um PIN de 4 dígitos" else "Repita o PIN"; "off" -> "Digite o PIN para desligar"; else -> "Digite o PIN dos pais" }
    fun press(d: String) {
        if (code.length >= 4) return
        code += d
        if (code.length == 4) {
            if (st.pinMode == "set" && first == null) { first = code; code = ""; msg = ""; return }
            val r = st.pinEntered(code, first)
            if (r != null) { msg = r; code = ""; if (st.pinMode == "set") first = null }
        }
    }
    Dialog(onDismissRequest = { st.showPin = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.width(320.dp), shape = RoundedCornerShape(16.dp), color = PanelC) {
            Column(Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(10.dp))
                Text("●".repeat(code.length) + "○".repeat(4 - code.length), color = Color.White, fontSize = 22.sp, letterSpacing = 6.sp)
                Text(msg, color = Color(0xFFFF8A8A), fontSize = 11.sp, modifier = Modifier.height(18.dp))
                for (row in listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"))) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 3.dp)) { for (d in row) TvButton(d, Btn, 15.sp, modifier = Modifier.width(64.dp)) { press(d) } }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 3.dp)) {
                    TvButton("Apagar", Btn, 11.sp, modifier = Modifier.width(64.dp)) { code = code.dropLast(1) }
                    TvButton("0", Btn, 15.sp, modifier = Modifier.width(64.dp)) { press("0") }
                    TvButton("Cancelar", Btn, 11.sp, modifier = Modifier.width(64.dp)) { st.showPin = false }
                }
            }
        }
    }
}

@Composable
fun DiagDialog(st: AppState) {
    Dialog(onDismissRequest = { st.showDiag = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth(0.8f).fillMaxHeight(0.85f), shape = RoundedCornerShape(16.dp), color = PanelC) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Diagnóstico", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Light, modifier = Modifier.weight(1f))
                    TvButton("Testar capas", Btn) { st.testTmdb() }
                    Spacer(Modifier.width(8.dp))
                    TvButton("Fechar", Btn) { st.showDiag = false }
                }
                if (st.tmdbTest.isNotEmpty()) Text(st.tmdbTest, color = Color(0xFF8BE9C8), fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                Spacer(Modifier.height(8.dp))
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) { Text(st.diagText(), color = Color(0xDDFFFFFF), fontSize = 12.sp, lineHeight = 17.sp) }
            }
        }
    }
}

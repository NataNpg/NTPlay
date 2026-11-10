package com.natanael.ntplay.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Guia de programação: canais da categoria aberta com os próximos programas. OK num programa abre o canal. */
@Composable
fun GuideDialog(st: AppState) {
    val lib = st.lib
    @Suppress("UNUSED_VARIABLE") val ev = st.epgVer
    val fmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val rows = remember(lib, st.group, st.epgVer) {
        val base = if (lib == null) emptyList() else (lib.liveGroups.firstOrNull { it.first == st.group }?.second ?: lib.live)
        val now = System.currentTimeMillis()
        base.asSequence().map { it to Epg.programs(it).filter { p -> p.stop > now && p.start < now + 6 * 3600_000L }.take(5) }.filter { it.second.isNotEmpty() }.take(60).toList()
    }
    Dialog(onDismissRequest = { st.showGuide = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth(0.94f).fillMaxHeight(0.92f), shape = RoundedCornerShape(16.dp), color = PanelC) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Guia de programação" + (if (st.group.isNotEmpty()) " — " + st.group else ""), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Light, modifier = Modifier.weight(1f))
                    TvButton("Fechar", Btn) { st.showGuide = false }
                }
                Spacer(Modifier.height(8.dp))
                if (rows.isEmpty()) Text("Sem guia para os canais desta categoria. O guia vem da sua lista (XMLTV) e pode demorar um pouco depois de abrir o app.", color = Muted, fontSize = 12.sp)
                else LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(rows, key = { it.first.key }) { (ch, progs) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(ch.title, Modifier.width(150.dp), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                val now = System.currentTimeMillis()
                                for (p in progs) {
                                    val on = p.start <= now
                                    TvButton(fmt.format(Date(p.start)) + "  " + p.title, if (on) Color(0xFF2A48B5) else Btn, 10.sp, compact = true, shape = RoundedCornerShape(8.dp), modifier = Modifier.widthIn(max = 230.dp)) {
                                        st.showGuide = false; st.playItem(ch, null, rows.map { it.first })
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

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
                    TvButton("Fechar", Btn) { st.showDiag = false }
                }
                Spacer(Modifier.height(8.dp))
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) { Text(st.diagText(), color = Color(0xDDFFFFFF), fontSize = 12.sp, lineHeight = 17.sp) }
            }
        }
    }
}

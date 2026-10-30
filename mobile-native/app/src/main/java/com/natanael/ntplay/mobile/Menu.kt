package com.natanael.ntplay.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.natanael.ntplay.core.AppState
import kotlinx.coroutines.launch

/**
 * Menu ☰: conta e nuvem, listas e provedores, preferências, grupos, Infantil e Animes.
 *
 * No celular tudo isto é uma folha que sobe de baixo, em vez dos diálogos da TV: a barra de abas
 * fica com as cinco coisas que o usuário faz o tempo todo, e o resto mora aqui.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuSheet(st: AppState, onClose: () -> Unit) {
    LaunchedEffect(Unit) { st.refreshAccounts() }

    ModalBottomSheet(onClose, containerColor = PanelC) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp).padding(bottom = 32.dp),
        ) {
            AccountBlock(st)
            Gap()
            ListsBlock(st)
            Gap()
            PrefsBlock(st)
            Gap()
            ThemesBlock(st, onClose)
        }
    }
}

/**
 * Diálogo de grupos, aberto pelo botão ＋ da tela de um título.
 *
 * `AppState.openGroups` só liga `groupDialog`; sem alguém lendo esse estado, o botão ＋ não fazia
 * nada — e o menu ainda ensinava o usuário a usá-lo.
 */
@Composable
fun GroupDialog(st: AppState, onClose: () -> Unit) {
    val item = st.groupDialogItem
    var novo by remember { mutableStateOf("") }
    val groups = remember(st.groupsVer) { st.groupNames() }

    AlertDialog(
        onDismissRequest = onClose,
        containerColor = PanelC,
        title = { Text("Adicionar a um grupo", color = Color.White, fontSize = 16.sp) },
        text = {
            Column {
                if (item != null) {
                    Text(
                        com.natanael.ntplay.core.Util.cleanT(item.title),
                        color = Cyan, fontSize = 13.sp,
                    )
                    Spacer(Modifier.height(12.dp))
                }
                if (groups.isEmpty()) {
                    Text("Nenhum grupo ainda. Crie o primeiro abaixo.", color = Muted, fontSize = 12.sp)
                }
                for (g in groups) {
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(g, Modifier.weight(1f), color = Color.White, fontSize = 14.sp)
                        if (item != null) {
                            val inside = remember(st.groupsVer, g) { st.inGroup(g, item.key) }
                            Text(
                                if (inside) "✓ no grupo" else "+ adicionar",
                                Modifier.clickable { st.toggleInGroup(g, item.key) },
                                color = if (inside) Cyan else Purple, fontSize = 12.sp,
                            )
                        } else {
                            Text("${st.groupCount(g)}", color = Muted, fontSize = 12.sp)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    novo, { novo = it },
                    Modifier.fillMaxWidth(),
                    label = { Text("Novo grupo (ex.: Para ver depois)") },
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
                SmallBtn("Criar grupo", novo.isNotBlank()) {
                    st.createGroup(novo)
                    item?.let { st.toggleInGroup(novo.trim().take(40), it.key) }
                    novo = ""
                }
            }
        },
        confirmButton = { SmallBtn("Fechar", true, Btn, onClose) },
    )
}

@Composable private fun Gap() {
    Spacer(Modifier.height(18.dp))
    Divider(color = Bg2)
    Spacer(Modifier.height(14.dp))
}

@Composable private fun Head(t: String) =
    Text(t, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)

// ------------------------------------------------------------------------- conta e nuvem

@Composable
private fun AccountBlock(st: AppState) {
    Head("☁ Conta")
    Spacer(Modifier.height(6.dp))
    Text(st.syncUser, color = Cyan, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    val valid = st.accessText()
    if (valid.isNotEmpty()) Text(valid, color = Muted, fontSize = 12.sp)
    if (st.syncMsg.isNotEmpty()) Text(st.syncMsg, color = Muted, fontSize = 12.sp)
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        // syncNow é suspensa; o escopo do AppState já vive enquanto o app vive
        SmallBtn("Sincronizar agora", !st.syncBusy) {
            st.scope.launch { st.syncNow(true) }
        }
        SmallBtn("Sair da conta", true, Btn) { st.accountLogout() }
    }
    Spacer(Modifier.height(12.dp))
    Text(
        "Precisa de ajuda ou renovação? WhatsApp $WHATS_NUM",
        color = Muted, fontSize = 11.sp,
    )
}

// ------------------------------------------------------------------------- listas e provedores

@Composable
private fun ListsBlock(st: AppState) {
    var url by remember { mutableStateOf("") }
    val lists = remember(st.lib, st.busy, st.accVer) { st.store.lists() }

    Head("☰ Listas e provedores")
    Spacer(Modifier.height(8.dp))

    if (lists.isEmpty()) {
        Text("Nenhuma lista ainda. Cole o link M3U ou Xtream abaixo.", color = Muted, fontSize = 12.sp)
    }

    for (m in lists) {
        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(m.name, color = Color.White, fontSize = 14.sp)
                    val info = st.accountText(m.id)
                    if (info.isNotEmpty()) Text(info, color = Muted, fontSize = 11.sp)
                    Text("${m.count} itens", color = Muted, fontSize = 11.sp)
                }
                Switch(
                    m.active, { st.toggleList(m.id) },
                    colors = SwitchDefaults.colors(checkedTrackColor = Purple),
                )
            }

            // O aviso que importa com o celular em cena: conta de 1 tela derruba a TV.
            oneScreenWarning(st.store.account(m.id))?.let { w ->
                Spacer(Modifier.height(6.dp))
                Text(
                    "⚠ $w",
                    Modifier.fillMaxWidth().background(Bg2, RoundedCornerShape(8.dp)).padding(10.dp),
                    color = Star, fontSize = 11.sp, lineHeight = 15.sp,
                )
            }

            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallBtn("Atualizar", !st.busy) { st.refreshList(m) }
                SmallBtn("Excluir", true, Btn) { st.deleteList(m.id) }
            }
        }
        Divider(color = Bg2)
    }

    Spacer(Modifier.height(14.dp))
    var byXtream by remember { mutableStateOf(false) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Chip("Por link", !byXtream) { byXtream = false }
        Chip("Por Xtream", byXtream) { byXtream = true }
    }
    Spacer(Modifier.height(8.dp))

    if (!byXtream) {
        OutlinedTextField(
            url, { url = it },
            Modifier.fillMaxWidth(),
            label = { Text("Link M3U") },
            singleLine = true,
        )
        Spacer(Modifier.height(8.dp))
        SmallBtn("Adicionar lista", url.isNotBlank() && !st.busy) {
            st.addUrl(url.trim(), null)
            url = ""
        }
    } else {
        // O provedor entrega servidor, usuário e senha separados. Sem este formulário, quem tem
        // só isso não conseguia cadastrar: Xtream.creds só reconhece URL terminada em get.php
        // ou player_api.php, então o host cru era recusado.
        var host by remember { mutableStateOf("") }
        var user by remember { mutableStateOf("") }
        var pass by remember { mutableStateOf("") }
        OutlinedTextField(
            host, { host = it }, Modifier.fillMaxWidth(),
            label = { Text("Servidor (ex.: http://servidor.com:8080)") }, singleLine = true,
        )
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            user, { user = it.trim() }, Modifier.fillMaxWidth(),
            label = { Text("Usuário") }, singleLine = true,
        )
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            pass, { pass = it.trim() }, Modifier.fillMaxWidth(),
            label = { Text("Senha") }, singleLine = true,
        )
        Spacer(Modifier.height(8.dp))
        SmallBtn(
            "Adicionar lista",
            host.isNotBlank() && user.isNotBlank() && pass.isNotBlank() && !st.busy,
        ) {
            st.addUrl(xtreamUrl(host, user, pass), null)
            host = ""; user = ""; pass = ""
        }
    }
}

// ------------------------------------------------------------------------- preferências

@Composable
private fun PrefsBlock(st: AppState) {
    Head("⚙ Preferências")
    Spacer(Modifier.height(4.dp))
    Toggle("Mostrar conteúdo adulto", st.adult) { st.changeAdult(it) }
    Toggle("Legendas ligadas por padrão", st.subs) { st.changeSubs(it) }
    Toggle("Próximo episódio automático", st.autoNext) { st.changeAutoNext(it) }
    Toggle("Detectar adulto pelo nome da categoria", st.adultByName) { st.changeAdultByName(it) }
    Toggle("Atualizar listas ao abrir", st.updateOnStart) { st.changeUpdateOnStart(it) }

    Spacer(Modifier.height(10.dp))
    Text("Buffer de filmes e séries", color = Muted, fontSize = 12.sp)
    Spacer(Modifier.height(6.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for ((i, n) in listOf("Padrão", "Grande", "Muito grande").withIndex()) {
            Chip(n, st.bufferMode == i) { st.changeBuffer(i) }
        }
    }
}

// ------------------------------------------------------------------------- grupos, infantil, animes

@Composable
private fun ThemesBlock(st: AppState, onClose: () -> Unit) {
    val lib = st.lib
    Head("★ Favoritos e grupos")
    Spacer(Modifier.height(6.dp))
    val groups = remember(st.groupsVer) { st.groupNames() }
    if (groups.isEmpty()) {
        Text(
            "Você ainda não criou grupos. Na tela de um filme ou série, toque em ＋ Grupo.",
            color = Muted, fontSize = 12.sp,
        )
    } else {
        for (g in groups) {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(g, Modifier.weight(1f), color = Color.White, fontSize = 13.sp)
                Text("${st.groupCount(g)}", color = Muted, fontSize = 12.sp)
                Spacer(Modifier.height(0.dp))
                Text(
                    "  ✕",
                    Modifier.clickable { st.deleteGroup(g) },
                    color = Muted, fontSize = 13.sp,
                )
            }
        }
    }

    if (lib != null) {
        Spacer(Modifier.height(14.dp))
        Head("Mais")
        Spacer(Modifier.height(6.dp))
        Text(
            "Infantil: ${lib.kids.size} títulos · Animes: ${lib.anime.size} títulos",
            color = Muted, fontSize = 12.sp,
        )
    }
}

// ------------------------------------------------------------------------- peças

@Composable
private fun Toggle(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f), color = Color.White, fontSize = 13.sp)
        Switch(value, onChange, colors = SwitchDefaults.colors(checkedTrackColor = Purple))
    }
}

@Composable
private fun SmallBtn(
    label: String,
    enabled: Boolean,
    color: Color = Purple,
    onClick: () -> Unit,
) {
    Button(
        onClick,
        enabled = enabled,
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 6.dp),
    ) { Text(label, fontSize = 12.sp) }
}

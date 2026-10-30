package com.natanael.ntplay.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.natanael.ntplay.core.Item
import com.natanael.ntplay.core.Library
import com.natanael.ntplay.core.Util

/** Largura de um cartão de capa na grade de 3 colunas; serve também nas prateleiras. */
val CARD_W = 108.dp

/**
 * O texto de orientação a mostrar quando não há conteúdo, ou null quando há.
 *
 * Isto é função pura de propósito: é a regra que decide se o usuário vê uma grade vazia (ruim) ou
 * uma frase dizendo o que fazer, e assim ela tem teste de unidade rodando sem emulador.
 *
 * @param tab a aba atual, como aparece em TABS
 * @param lib a biblioteca carregada, ou null se ainda não há
 * @param busy true enquanto a lista está carregando (aí quem aparece é o indicador de progresso)
 * @param listCount quantas listas o usuário cadastrou
 */
fun emptyMessage(tab: String, lib: Library?, busy: Boolean, listCount: Int): String? = null

@Composable
fun EmptyState(msg: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                msg, color = Muted, fontSize = 15.sp, textAlign = TextAlign.Center,
                modifier = Modifier.testTag("empty-state"),
            )
            if (action != null && onAction != null) {
                Spacer(Modifier.height(18.dp))
                Button(
                    onAction,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Purple),
                ) { Text(action, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

/** Cartão de capa de filme, série ou anime. */
@Composable
fun PosterCard(item: Item, onClick: () -> Unit) {
    val tag = Util.audioTag(item)
    val year = Util.yearOf(item.title)
    val q = Util.quality(item.title)
    Column(
        Modifier.width(CARD_W).clickable(onClick = onClick).padding(4.dp),
    ) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(10.dp)).background(CardC),
        ) {
            if (item.logo.isNotEmpty()) {
                AsyncImage(
                    item.logo, item.title,
                    Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
                )
            }
            if (tag.isNotEmpty()) {
                Text(
                    tag,
                    Modifier.align(Alignment.BottomStart).padding(5.dp)
                        .background(if (tag == "DUB") Purple else Cyan, RoundedCornerShape(4.dp))
                        .padding(horizontal = 5.dp, vertical = 1.dp),
                    color = if (tag == "DUB") Color.White else Color(0xFF05101F),
                    fontSize = 9.sp, fontWeight = FontWeight.ExtraBold,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            Util.cleanT(item.title), color = Color.White, fontSize = 12.sp,
            maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 14.sp,
        )
        val sub = listOfNotNull(year.ifEmpty { null }, q.ifEmpty { null }).joinToString(" · ")
        if (sub.isNotEmpty()) Text(sub, color = Muted, fontSize = 10.sp, maxLines = 1)
    }
}

/** Linha de canal ao vivo: logo, nome e — só quando existe — o programa de agora. */
@Composable
fun ChannelRow(item: Item, now: String?, onClick: () -> Unit) {
    androidx.compose.foundation.layout.Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(52.dp).clip(RoundedCornerShape(8.dp)).background(CardC),
            contentAlignment = Alignment.Center,
        ) {
            if (item.logo.isNotEmpty()) {
                AsyncImage(item.logo, item.title, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            } else {
                Text("TV", color = Muted, fontSize = 11.sp)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Text(
                item.title, color = Color.White, fontSize = 14.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            // Nada de linha reservada quando a lista não tem programação: sem EPG, só o nome.
            if (!now.isNullOrBlank()) {
                Text(
                    now, color = Cyan, fontSize = 11.sp, maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("epg-now"),
                )
            }
        }
    }
}

/** Ponto vermelho de "ao vivo". */
@Composable
fun LiveDot() {
    Box(Modifier.size(6.dp).background(Color(0xFFFF3B3B), CircleShape))
}

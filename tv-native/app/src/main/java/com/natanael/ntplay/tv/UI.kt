package com.natanael.ntplay.tv

import androidx.compose.foundation.Image
import androidx.compose.foundation.Indication
import androidx.compose.foundation.IndicationInstance
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ---------------------------------------------------------------- tema
val Bg = Color(0xFF0D0D12)
val Bg2 = Color(0xFF17171F)
val PanelC = Color(0xFF1B1B24)
val CardC = Color(0xFF24242F)
val Pink = Color(0xFFE93A7D)
val Cyan = Color(0xFF22D3EE)
val Purple = Color(0xFFA855F7)
val Muted = Color(0xFF9AA4B8)
val Dark = Color(0xFF0D0D12)
val Btn = Color(0xFF34343F)

/** sem "camada branca" do Material ao focar: o foco é desenhado por nós */
/** tamanho geral da interface (1.0 = original) */
const val UI_SCALE = 0.8f

object NoIndication : Indication {
    private object Inst : IndicationInstance {
        override fun ContentDrawScope.drawIndication() { drawContent() }
    }
    @Composable
    override fun rememberUpdatedInstance(interactionSource: InteractionSource): IndicationInstance = Inst
}

@Composable
fun NtTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(primary = Pink, background = Bg, surface = PanelC)) {
        val d = LocalDensity.current
        CompositionLocalProvider(
            LocalIndication provides NoIndication,
            LocalDensity provides Density(d.density * UI_SCALE, d.fontScale),
            content = content
        )
    }
}

// ---------------------------------------------------------------- foco (controle) + hover (mouse)
class FocusInfo {
    var focused by mutableStateOf(false)
    var hovered by mutableStateOf(false)
    val on: Boolean get() = focused || hovered
}

@Composable
fun rememberFocus() = remember { FocusInfo() }

@Composable
fun Modifier.tvClick(
    fi: FocusInfo = rememberFocus(),
    shape: Shape = RoundedCornerShape(12.dp),
    scale: Float = 1.06f,
    border: Boolean = true,
    onClick: () -> Unit
): Modifier {
    val src = remember { MutableInteractionSource() }
    val hov by src.collectIsHoveredAsState()
    SideEffect { fi.hovered = hov }
    val s = if (fi.on) scale else 1f
    return this
        .hoverable(src)
        .onFocusChanged { fi.focused = it.isFocused }
        .graphicsLayer { scaleX = s; scaleY = s }
        .then(if (border && fi.focused) Modifier.border(3.dp, Color.White, shape) else if (border && hov) Modifier.border(2.dp, Color(0xAAFFFFFF), shape) else Modifier)
        .clickable(interactionSource = src, indication = null, onClick = onClick)
}

/** botão de TV: focado = fundo branco com texto escuro (sempre legível) */
@Composable
fun TvButton(label: String, bg: Color, fontSize: TextUnit = 13.sp, selected: Boolean = false, shape: Shape = RoundedCornerShape(50), modifier: Modifier = Modifier, onClick: () -> Unit) {
    val fi = rememberFocus()
    val back = if (fi.on) Color.White else if (selected) Pink else bg
    val fg = if (fi.on) Dark else Color.White
    Text(
        label, color = fg, fontSize = fontSize, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
        modifier = modifier.tvClick(fi, shape, 1.05f, border = false, onClick = onClick).background(back, shape).padding(horizontal = 18.dp, vertical = 10.dp)
    )
}

/** rolagem com mouse: roda do mouse e arrastar com o botão pressionado */
fun Modifier.mouseScroll(state: ScrollableState, horizontal: Boolean = false): Modifier = this.pointerInput(state, horizontal) {
    awaitPointerEventScope {
        var last: Offset? = null
        var moved = 0f
        while (true) {
            val ev = awaitPointerEvent(PointerEventPass.Main)
            val ch = ev.changes.firstOrNull() ?: continue
            if (ch.type != PointerType.Mouse) { last = null; continue }
            when (ev.type) {
                PointerEventType.Scroll -> if (!ch.isConsumed) {
                    val d = ch.scrollDelta
                    val v = if (horizontal) (if (d.x != 0f) d.x else d.y) else d.y
                    if (v != 0f && (horizontal || d.y != 0f)) { state.dispatchRawDelta(v * 90f); ch.consume() }
                }
                PointerEventType.Press -> { last = ch.position; moved = 0f }
                PointerEventType.Move -> {
                    val l = last
                    if (l != null && ev.buttons.isPrimaryPressed) {
                        val dd = ch.position - l
                        val v = if (horizontal) dd.x else dd.y
                        moved += kotlin.math.abs(v)
                        if (moved > 12f) { state.dispatchRawDelta(-v); ch.consume() }
                        last = ch.position
                    }
                }
                PointerEventType.Release -> last = null
                else -> {}
            }
        }
    }
}

private fun tint(s: String): Color = Color.hsv(Math.floorMod(s.hashCode().toLong(), 360L).toFloat(), 0.35f, 0.28f)

/** título limpo para filmes/séries (sem [tags], (ano), 4K...) */
fun showTitle(it: Item): String = if (it.kind == 'l') it.title else Util.cleanT(it.title).ifEmpty { it.title }

// ---------------------------------------------------------------- app
@Composable
fun App(st: AppState) {
    Box(Modifier.fillMaxSize().background(Bg)) {
        Column(Modifier.fillMaxSize().padding(horizontal = 56.dp, vertical = 30.dp)) {
            Header(st)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                val d = st.detail
                if (d != null) DetailScreen(st, d) else Browse(st)
            }
            Footer()
        }
        val p = st.play
        if (p != null) PlayerScreen(st, p)
        if (st.showLists) ListsDialog(st)
        if (st.showSearch) SearchDialog(st)
        if (st.toastMsg.isNotEmpty()) {
            Box(Modifier.fillMaxSize().padding(bottom = 48.dp), contentAlignment = Alignment.BottomCenter) {
                Surface(shape = RoundedCornerShape(14.dp), color = Color(0xF01C2438)) {
                    Text(st.toastMsg, Modifier.padding(horizontal = 20.dp, vertical = 12.dp), color = Color.White, fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
fun Footer() {
    Row(Modifier.fillMaxWidth().padding(top = 6.dp).height(28.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Text("NTPlay", color = Cyan, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
        Text("  ·  © Natanael G Silva  ·  djnatan0@live.com", color = Color(0xCCFFFFFF), fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun Header(st: AppState) {
    val clock by produceState("") {
        while (true) { value = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()); delay(20000) }
    }
    Row(Modifier.fillMaxWidth().height(58.dp), verticalAlignment = Alignment.CenterVertically) {
        Image(painterResource(R.drawable.logo), null, Modifier.size(34.dp).clip(RoundedCornerShape(9.dp)))
        Spacer(Modifier.width(8.dp))
        Text("NTPlay", style = TextStyle(brush = Brush.horizontalGradient(listOf(Cyan, Purple)), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold))
        Spacer(Modifier.width(16.dp))
        Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            for (t in TABS) {
                val fi = rememberFocus()
                val sel = st.tab == t
                Text(
                    t, maxLines = 1, fontSize = 12.sp, fontWeight = if (sel || fi.on) FontWeight.ExtraBold else FontWeight.Medium,
                    color = if (fi.on) Dark else if (sel) Color.White else Color(0xCCFFFFFF),
                    modifier = Modifier.tvClick(fi, RoundedCornerShape(50), 1.05f, border = false) { st.selectTab(t) }
                        .background(if (fi.on) Color.White else if (sel) Pink else Color.Transparent, RoundedCornerShape(50))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }
        TvButton("🔍 Buscar", Btn, 12.sp) { st.showSearch = true }
        Spacer(Modifier.width(8.dp))
        TvButton("☰ Listas", Color(0xFFFF5A00), 12.sp) { st.showLists = true }
        Spacer(Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(clock, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text(if (st.busy) "⏳ " + st.status else st.status, color = Muted, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(170.dp), textAlign = TextAlign.End)
        }
    }
}

// ---------------------------------------------------------------- cartões
@Composable
fun PosterCard(st: AppState, item: Item, w: Dp, prog: Float = 0f, onRemove: (() -> Unit)? = null, onClick: () -> Unit) {
    if (item.kind == 'l') { LiveCard(st, item, w, onClick); return }
    val h = w * 1.5f
    @Suppress("UNUSED_VARIABLE") val lv = st.logoVer
    var img by remember(item.key, item.logo) { mutableStateOf(item.logo.ifEmpty { Meta.cached(st.store, item)?.img ?: "" }) }
    var tried by remember(item.key) { mutableStateOf(false) }
    LaunchedEffect(item.key, img) {
        if (img.isEmpty() && !tried && !item.adult) {
            tried = true
            delay(250) // só busca se o cartão continuar na tela
            val i = Meta.get(st.store, item)
            if (i != null && i.img.isNotEmpty()) img = i.img
        }
    }
    val fi = rememberFocus()
    Column(Modifier.width(w)) {
        Box(Modifier.tvClick(fi, RoundedCornerShape(12.dp), onClick = onClick).clip(RoundedCornerShape(12.dp)).background(tint(item.title)).height(h).fillMaxWidth()) {
            if (img.isNotEmpty()) {
                AsyncImage(
                    model = img, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
                    onError = { val c = Meta.cached(st.store, item)?.img ?: ""; img = if (c.isNotEmpty() && c != img) c else ""}
                )
            } else {
                Text(showTitle(item), Modifier.align(Alignment.Center).padding(10.dp), color = Color(0xDDFFFFFF), fontSize = 13.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 4, overflow = TextOverflow.Ellipsis)
            }
            if (st.favs.contains(item.key)) Text("★", Modifier.align(Alignment.TopEnd).padding(6.dp).background(Color(0x99000000), CircleShape).padding(horizontal = 6.dp, vertical = 2.dp), color = Color(0xFFFFC107), fontSize = 12.sp)
            if (prog > 0f) Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(4.dp).background(Color(0x66000000))) {
                Box(Modifier.fillMaxWidth(prog.coerceIn(0f, 1f)).fillMaxHeight().background(Pink))
            }
        }
        Text(showTitle(item), Modifier.padding(top = 6.dp, start = 2.dp), color = if (fi.on) Color.White else Color(0xDDFFFFFF), fontSize = 11.sp, fontWeight = if (fi.on) FontWeight.Bold else FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        val y = Util.yearOf(item.title)
        if (y.isNotEmpty() || item.kind == 's') Text(listOfNotNull(y.ifEmpty { null }, if (item.kind == 's') "Série" else null).joinToString(" · "), Modifier.padding(start = 2.dp), color = Muted, fontSize = 9.sp)
        if (onRemove != null) TvButton("✕ Remover", Btn, 9.sp, modifier = Modifier.padding(top = 4.dp), onClick = onRemove)
    }
}

@Composable
fun LiveCard(st: AppState, item: Item, w: Dp, onClick: () -> Unit) {
    @Suppress("UNUSED_VARIABLE") val lv = st.logoVer
    var img by remember(item.key, item.logo) { mutableStateOf(item.logo) }
    val fi = rememberFocus()
    Column(Modifier.width(w)) {
        Box(
            Modifier.tvClick(fi, RoundedCornerShape(12.dp), onClick = onClick).clip(RoundedCornerShape(12.dp))
                .background(Brush.linearGradient(listOf(CardC, Color(0xFF2E2E3B)))).aspectRatio(16f / 10f).fillMaxWidth()
        ) {
            if (img.isNotEmpty()) {
                AsyncImage(
                    model = img, contentDescription = null, contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(start = 18.dp, end = 18.dp, top = 22.dp, bottom = 12.dp),
                    onError = { val alt = Logos.find(item.title); img = if (alt != null && alt != img) alt else "" }
                )
            } else {
                Text(item.title, Modifier.align(Alignment.Center).padding(start = 10.dp, end = 10.dp, top = 18.dp), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            LiveBadge(Modifier.align(Alignment.TopStart).padding(6.dp))
            if (st.favs.contains(item.key)) Text("★", Modifier.align(Alignment.TopEnd).padding(6.dp), color = Color(0xFFFFC107), fontSize = 12.sp)
        }
        Text(item.title, Modifier.padding(top = 6.dp, start = 2.dp), color = if (fi.on) Color.White else Color(0xDDFFFFFF), fontSize = 11.sp, fontWeight = if (fi.on) FontWeight.Bold else FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun LiveBadge(m: Modifier) {
    Row(m.background(Color(0xCC000000), RoundedCornerShape(50)).padding(horizontal = 6.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(5.dp).background(Color(0xFFFF3B3B), CircleShape))
        Spacer(Modifier.width(4.dp))
        Text("AO VIVO", color = Color.White, fontSize = 7.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun ShelfRow(title: String, count: Int, onAll: (() -> Unit)?, content: LazyListScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(4.dp).height(16.dp).background(Pink, RoundedCornerShape(2.dp)))
            Spacer(Modifier.width(8.dp))
            Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.width(8.dp))
            Text("$count", color = Muted, fontSize = 11.sp)
            Spacer(Modifier.weight(1f))
            if (onAll != null) TvButton("Ver tudo ›", Btn, 11.sp, onClick = onAll)
        }
        val ls = rememberLazyListState()
        LazyRow(modifier = Modifier.mouseScroll(ls, true), state = ls, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), content = content)
    }
}

// ---------------------------------------------------------------- navegação
@Composable
fun Browse(st: AppState) {
    val lib = st.lib
    if (lib == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (st.busy) st.status else "Nenhuma lista carregada", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                if (!st.busy) Text("Adicione o link da sua lista M3U ou envie pelo celular com o QR code.", color = Muted, fontSize = 13.sp)
                Spacer(Modifier.height(18.dp))
                if (!st.busy) TvButton("☰ Carregar lista M3U", Color(0xFFFF5A00), 15.sp) { st.showLists = true }
            }
        }
        return
    }
    val tab = st.tab
    val groups = when (tab) { "TV AO VIVO" -> lib.liveGroups; "FILMES" -> lib.movieGroups; "SÉRIES" -> lib.serieGroups; else -> null }
    Row(Modifier.fillMaxSize().padding(top = 6.dp)) {
        if (groups != null) Sidebar(st, groups, when (tab) { "TV AO VIVO" -> lib.live.size; "FILMES" -> lib.movies.size; else -> lib.series.size }, tab)
        Box(Modifier.weight(1f).fillMaxHeight()) {
            if (groups != null) {
                if (st.group.isEmpty()) {
                    val ls = rememberLazyListState()
                    LazyColumn(Modifier.fillMaxSize().mouseScroll(ls), state = ls, contentPadding = PaddingValues(top = 4.dp, bottom = 20.dp)) {
                        items(groups.take(80), key = { it.first }) { g ->
                            ShelfRow(g.first, g.second.size, { st.group = g.first }) {
                                items(g.second.take(30), key = { it.key }) { it2 -> CardFor(st, it2, g.second) }
                            }
                        }
                    }
                } else {
                    val l = groups.firstOrNull { it.first == st.group }?.second ?: emptyList()
                    GridOf(st, l, st.group)
                }
            } else when (tab) {
                "INFANTIL" -> GridOf(st, lib.kids.take(600), "Infantil")
                "ANIMES" -> GridOf(st, lib.anime.take(600), "Animes")
                "FAVORITOS" -> GridOf(st, lib.every.filter { st.favs.contains(it.key) }, "Minha lista")
                else -> Recom(st, lib)
            }
        }
    }
}

@Composable
fun CardFor(st: AppState, item: Item, queue: List<Item>) {
    PosterCard(st, item, if (item.kind == 'l') 170.dp else 112.dp) {
        if (item.kind == 'l') st.playItem(item, null, queue) else { st.queue = queue; st.openDetail(item) }
    }
}

@Composable
fun GridOf(st: AppState, l: List<Item>, title: String) {
    if (l.isEmpty()) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Nada por aqui ainda.", color = Muted, fontSize = 14.sp) }; return }
    val live = l.firstOrNull()?.kind == 'l'
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(start = 16.dp, top = 6.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(4.dp).height(18.dp).background(Pink, RoundedCornerShape(2.dp)))
            Spacer(Modifier.width(8.dp))
            Text(title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.width(8.dp))
            Text("${l.size}", color = Muted, fontSize = 12.sp)
        }
        val gs = rememberLazyGridState()
        LazyVerticalGrid(
            columns = GridCells.Adaptive(if (live) 170.dp else 112.dp), modifier = Modifier.fillMaxSize().mouseScroll(gs), state = gs,
            contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(l, key = { it.key }) { it2 -> CardFor(st, it2, l) }
        }
    }
}

@Composable
fun Sidebar(st: AppState, groups: List<Pair<String, List<Item>>>, total: Int, tab: String) {
    Column(Modifier.width(196.dp).fillMaxHeight().background(Bg2, RoundedCornerShape(16.dp)).padding(vertical = 8.dp)) {
        Text("CATEGORIAS", Modifier.padding(start = 16.dp, top = 6.dp, bottom = 6.dp), color = Muted, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
        val ls = rememberLazyListState()
        LazyColumn(Modifier.weight(1f).mouseScroll(ls), state = ls, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            item { CatBtn("Todas", total, st.group.isEmpty()) { st.group = "" } }
            items(groups, key = { it.first }) { g -> CatBtn(g.first, g.second.size, st.group == g.first) { st.group = g.first } }
        }
    }
}

@Composable
fun CatBtn(name: String, n: Int, sel: Boolean, onClick: () -> Unit) {
    val fi = rememberFocus()
    val bg = if (fi.on) Color.White else if (sel) Pink else Color.Transparent
    val fg = if (fi.on) Dark else if (sel) Color.White else Color(0xCCFFFFFF)
    Row(
        Modifier.fillMaxWidth().tvClick(fi, RoundedCornerShape(10.dp), 1.03f, border = false, onClick = onClick).background(bg, RoundedCornerShape(10.dp)).padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(name, Modifier.weight(1f), color = fg, fontSize = 12.sp, fontWeight = if (sel || fi.on) FontWeight.Bold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("$n", color = if (fi.on) Dark else Muted, fontSize = 9.sp)
    }
}

// ---------------------------------------------------------------- recomendações (com destaque)
@Composable
fun Recom(st: AppState, lib: Library) {
    val ver = st.progVer
    val cw = remember(ver, lib) { st.continueList() }
    val favs = remember(st.favs, lib) { lib.every.filter { st.favs.contains(it.key) }.take(30) }
    val hero = remember(lib) {
        cw.firstOrNull { it.first.kind != 'l' }?.first
            ?: lib.movies.take(60).filter { it.logo.isNotEmpty() && !it.adult }.let { if (it.isEmpty()) null else it[(System.currentTimeMillis() / 60000 % it.size).toInt()] }
    }
    val ls = rememberLazyListState()
    LazyColumn(Modifier.fillMaxSize().mouseScroll(ls), state = ls, contentPadding = PaddingValues(bottom = 20.dp)) {
        if (hero != null) item { Hero(st, hero) }
        if (cw.isNotEmpty()) item {
            ShelfRow("Continuar assistindo", cw.size, null) {
                items(cw, key = { it.first.key }) { (it2, ep) ->
                    val p = st.progOf(ep)
                    PosterCard(st, it2, if (it2.kind == 'l') 170.dp else 112.dp, prog = if (p != null && p.d > 0) p.t.toFloat() / p.d else 0f, onRemove = { st.removeProg(it2.key) }) {
                        st.playItem(it2, ep)
                    }
                }
            }
        }
        if (favs.isNotEmpty()) item { ShelfRow("Minha lista", favs.size, null) { items(favs, key = { it.key }) { it2 -> CardFor(st, it2, favs) } } }
        if (lib.movies.isNotEmpty()) item { ShelfRow("Filmes", lib.movies.size, { st.selectTab("FILMES") }) { items(lib.movies.take(30), key = { it.key }) { it2 -> CardFor(st, it2, lib.movies) } } }
        if (lib.series.isNotEmpty()) item { ShelfRow("Séries", lib.series.size, { st.selectTab("SÉRIES") }) { items(lib.series.take(30), key = { it.key }) { it2 -> CardFor(st, it2, lib.series) } } }
        if (lib.live.isNotEmpty()) item { ShelfRow("TV ao vivo", lib.live.size, { st.selectTab("TV AO VIVO") }) { items(lib.live.take(30), key = { it.key }) { it2 -> CardFor(st, it2, lib.live) } } }
    }
}

@Composable
fun Hero(st: AppState, item: Item) {
    var info by remember(item.key) { mutableStateOf(Meta.cached(st.store, item)) }
    LaunchedEffect(item.key) { val i = Meta.get(st.store, item); if (i != null) info = i }
    val img = item.logo.ifEmpty { info?.img ?: "" }
    Box(Modifier.fillMaxWidth().height(240.dp).padding(horizontal = 16.dp, vertical = 8.dp).clip(RoundedCornerShape(18.dp)).background(tint(item.title))) {
        if (img.isNotEmpty()) AsyncImage(img, null, Modifier.fillMaxSize().graphicsLayer { alpha = 0.55f }, contentScale = ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Bg, Color(0xCC0D0D12), Color.Transparent))))
        Row(Modifier.fillMaxSize().padding(22.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("EM DESTAQUE", color = Cyan, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
                Text(showTitle(item), color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val meta = listOfNotNull(info?.y?.ifEmpty { null } ?: Util.yearOf(item.title).ifEmpty { null }, info?.r?.takeIf { it > 0 }?.let { "★ %.1f".format(it) }, item.group).joinToString("  •  ")
                Text(meta, color = Muted, fontSize = 11.sp, modifier = Modifier.padding(vertical = 4.dp))
                Text(info?.ov ?: "", color = Color(0xDDFFFFFF), fontSize = 12.sp, maxLines = 3, overflow = TextOverflow.Ellipsis, lineHeight = 17.sp, modifier = Modifier.fillMaxWidth(0.75f))
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TvButton("▶ Assistir", Pink, 13.sp) { st.playItem(item) }
                    TvButton("ⓘ Detalhes", Btn, 13.sp) { st.openDetail(item) }
                }
            }
            if (img.isNotEmpty()) AsyncImage(img, null, Modifier.height(200.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
        }
    }
}

// ---------------------------------------------------------------- detalhes
@Composable
fun DetailScreen(st: AppState, item: Item) {
    var info by remember(item.key) { mutableStateOf(Meta.cached(st.store, item)) }
    var searching by remember(item.key) { mutableStateOf(true) }
    LaunchedEffect(item.key) { val i = Meta.get(st.store, item, priority = true); if (i != null) info = i; searching = false }
    val poster = item.logo.ifEmpty { info?.img ?: "" }
    @Suppress("UNUSED_VARIABLE") val ev = st.epsVer
    val needEps = st.needsEps(item)
    val cont = st.startEp(item)
    val pr = st.resumeOf(cont)
    val serie = item.kind == 's'
    val label = when {
        needEps && st.epsLoading -> "⏳ Carregando episódios…"
        needEps -> "↻ Carregar episódios"
        serie -> if (st.progOf(cont) != null) "▶ Continuar T${cont.s} E${cont.e}" else "▶ Assistir T${cont.s} E${cont.e}"
        pr > 0 -> "▶ Continuar de " + Util.fmt(pr)
        else -> "▶ Assistir"
    }
    val yr = info?.y?.ifEmpty { null } ?: Util.yearOf(item.title).ifEmpty { null }
    val meta = listOfNotNull(yr, info?.r?.takeIf { it > 0 }?.let { "★ %.1f".format(it) }, item.group, if (serie && !needEps) "${item.ns} temporada" + (if (item.ns > 1) "s" else "") else null).joinToString("  •  ")
    val ov = info?.ov ?: ""
    val ovText = if (ov.isNotEmpty()) ov else if (searching) "Buscando sinopse…" else "Sinopse não disponível para este título."

    Box(Modifier.fillMaxSize().clip(RoundedCornerShape(18.dp))) {
        if (poster.isNotEmpty()) AsyncImage(poster, null, Modifier.fillMaxSize().graphicsLayer { alpha = 0.16f }, contentScale = ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x660D0D12), Bg))))
        Row(Modifier.fillMaxSize().padding(18.dp), horizontalArrangement = Arrangement.spacedBy(22.dp)) {
            // coluna de informações
            Column(if (serie) Modifier.weight(0.42f) else Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box(Modifier.width(if (serie) 120.dp else 170.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(14.dp)).background(tint(item.title))) {
                        if (poster.isNotEmpty()) AsyncImage(poster, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        else Text(showTitle(item), Modifier.align(Alignment.Center).padding(10.dp), color = Color(0xCCFFFFFF), fontSize = 14.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(showTitle(item), color = Color.White, fontSize = if (serie) 22.sp else 28.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(meta, color = Muted, fontSize = 12.sp, modifier = Modifier.padding(vertical = 6.dp))
                        if (!serie) Text(ovText, color = Color(0xE6FFFFFF), fontSize = 14.sp, maxLines = 7, overflow = TextOverflow.Ellipsis, lineHeight = 20.sp)
                    }
                }
                if (serie) Text(ovText, color = Color(0xE6FFFFFF), fontSize = 13.sp, maxLines = 6, overflow = TextOverflow.Ellipsis, lineHeight = 19.sp, modifier = Modifier.padding(top = 12.dp))
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TvButton(label, Pink, 13.sp) { if (needEps) { if (!st.epsLoading) st.loadEps(item) } else st.playItem(item, cont) }
                    TvButton(if (st.favs.contains(item.key)) "★ Na lista" else "☆ Minha lista", Btn, 13.sp) { st.toggleFav(item.key) }
                    TvButton("‹ Voltar", Btn, 13.sp) { st.detail = null }
                }
                if (needEps && st.epsErr.isNotEmpty()) Text(st.epsErr + " Toque em \"Carregar episódios\" para tentar de novo.", color = Color(0xFFFF9A9A), fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
            }
            // coluna de episódios (altura inteira)
            if (serie) Column(Modifier.weight(0.58f).fillMaxHeight().background(Color(0xAA17171F), RoundedCornerShape(16.dp)).padding(12.dp)) {
                Text("EPISÓDIOS", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))
                if (needEps) {
                    Text(if (st.epsLoading) "Carregando episódios…" else "Episódios ainda não carregados.", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(8.dp))
                } else {
                    val seasons = item.eps.map { it.s }.distinct().sorted()
                    val ls = rememberLazyListState()
                    LazyRow(Modifier.mouseScroll(ls, true), state = ls, horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 6.dp, horizontal = 4.dp)) {
                        items(seasons) { s -> TvButton("Temporada $s", Btn, 11.sp, selected = st.season == s) { st.season = s } }
                    }
                    val eps = item.eps.filter { it.s == st.season }
                    val ls2 = rememberLazyListState()
                    LazyColumn(Modifier.weight(1f).mouseScroll(ls2), state = ls2, contentPadding = PaddingValues(vertical = 6.dp, horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(eps, key = { it.url }) { e -> EpisodeRow(st, item, e) }
                    }
                }
            }
        }
    }
}

/** nome do episódio sem repetir o nome da série */
fun epTitle(item: Item, e: Ep): String {
    val c = Util.cleanT(e.name).replace(Regex("(?i)\\bS\\d{1,2}\\s?E\\d{1,3}\\b|\\b\\d{1,2}x\\d{1,3}\\b"), " ").replace(Regex("\\s+"), " ").trim(' ', '-', '–', '|', ':', '.')
    val base = Util.cleanT(item.title)
    val rest = if (base.isNotEmpty() && c.startsWith(base, ignoreCase = true)) c.substring(base.length).trim(' ', '-', '–', '|', ':', '.') else c
    return if (rest.isEmpty() || rest.equals(base, true)) "Episódio ${e.e}" else rest
}

@Composable
fun EpisodeRow(st: AppState, item: Item, e: Ep) {
    val p = st.progOf(e)
    val fi = rememberFocus()
    val fg = if (fi.on) Dark else Color.White
    Row(
        Modifier.fillMaxWidth().tvClick(fi, RoundedCornerShape(10.dp), 1.02f, border = false) { st.playItem(item, e) }
            .background(if (fi.on) Color.White else Color(0x14FFFFFF), RoundedCornerShape(10.dp)).padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("${e.e}", Modifier.width(34.dp), color = if (fi.on) Pink else Cyan, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
        Text(epTitle(item, e), Modifier.weight(1f), color = fg, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(if (p?.done == true) "✓ visto" else if (p != null && p.t > 0) Util.fmt(p.t) else "▶", color = if (fi.on) Dark else Muted, fontSize = 11.sp)
    }
}

// ---------------------------------------------------------------- busca
@Composable
fun SearchDialog(st: AppState) {
    var q by remember { mutableStateOf("") }
    var res by remember { mutableStateOf<List<Item>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(q) {
        if (q.trim().length < 2) { res = emptyList(); return@LaunchedEffect }
        busy = true; delay(350); res = st.search(q); busy = false
    }
    Dialog(onDismissRequest = { st.showSearch = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth(0.92f).fillMaxHeight(0.9f), shape = RoundedCornerShape(18.dp), color = PanelC) {
            Column(Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(q, { q = it }, Modifier.weight(1f), placeholder = { Text("Buscar canais, filmes e séries…") }, singleLine = true, colors = fieldColors())
                    Spacer(Modifier.width(10.dp))
                    TvButton("Fechar", Btn) { st.showSearch = false }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    when { q.trim().length < 2 -> "Digite pelo menos 2 letras."; busy -> "Buscando…"; res.isEmpty() -> "Nada encontrado."; else -> "${res.size} resultado(s)" },
                    color = Muted, fontSize = 12.sp
                )
                val gs = rememberLazyGridState()
                LazyVerticalGrid(columns = GridCells.Adaptive(112.dp), modifier = Modifier.mouseScroll(gs), state = gs, contentPadding = PaddingValues(6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(res, key = { it.key }) { it2 ->
                        PosterCard(st, it2, if (it2.kind == 'l') 150.dp else 112.dp) {
                            st.showSearch = false
                            if (it2.kind == 'l') st.playItem(it2, null, res) else { st.queue = res; st.openDetail(it2) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Cyan, unfocusedBorderColor = Color(0x44FFFFFF), focusedTextColor = Color.White, unfocusedTextColor = Color.White,
    cursorColor = Cyan, focusedPlaceholderColor = Muted, unfocusedPlaceholderColor = Muted
)

// ---------------------------------------------------------------- listas M3U + QR code
@Composable
fun ListsDialog(st: AppState) {
    val (topic, key) = remember { st.store.relay() }
    val link = Util.SEND_BASE + "#t=" + topic + "&k=" + key
    val qr = remember(link) { Qr.bitmap(link, 360).asImageBitmap() }
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    val lists = remember(st.listsVer, st.busy) { st.store.lists() }
    LaunchedEffect(topic) { Relay.listen(topic, key) { c, n -> st.onRelay(c, n) } }
    Dialog(onDismissRequest = { st.showLists = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth(0.9f).fillMaxHeight(0.9f), shape = RoundedCornerShape(18.dp), color = PanelC) {
            Row(Modifier.padding(20.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Column(Modifier.width(210.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Listas M3U", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                    Spacer(Modifier.height(10.dp))
                    Text("ENVIAR PELO CELULAR", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Image(qr, "QR code", Modifier.size(150.dp).background(Color.White, RoundedCornerShape(10.dp)).padding(8.dp))
                    Text("Aponte a câmera do celular, cole o link da lista e envie. Ela chega aqui sozinha.", color = Color(0xDDFFFFFF), fontSize = 10.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
                    Text("⏳ Aguardando o celular…", color = Cyan, fontSize = 10.sp, modifier = Modifier.padding(top = 6.dp))
                }
                val ls = rememberLazyListState()
                LazyColumn(Modifier.weight(1f).fillMaxHeight().mouseScroll(ls), state = ls, verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(end = 4.dp, bottom = 24.dp)) {
                    item { Text("MINHAS LISTAS", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                    if (lists.isEmpty()) item { Text("Nenhuma lista ainda.", color = Muted, fontSize = 12.sp) }
                    items(lists, key = { it.id }) { m ->
                        Row(Modifier.fillMaxWidth().background(Color(0x14FFFFFF), RoundedCornerShape(12.dp)).padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TvButton(if (m.active) "☑ Usar" else "☐ Usar", Btn, 11.sp) { st.toggleList(m.id) }
                            Column(Modifier.weight(1f)) {
                                Text(m.name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${m.count} itens", color = Muted, fontSize = 10.sp)
                            }
                            TvButton("↻ Atualizar", Btn, 11.sp) { st.refreshList(m) }
                            TvButton("🗑 Excluir", Color(0x55FF5A5A), 11.sp) { st.deleteList(m.id) }
                        }
                    }
                    item { Spacer(Modifier.height(4.dp)); Text("ADICIONAR NOVA LISTA", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                    item { OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), placeholder = { Text("Nome da lista (opcional)", fontSize = 12.sp) }, singleLine = true, textStyle = TextStyle(fontSize = 13.sp), colors = fieldColors()) }
                    item { OutlinedTextField(url, { url = it }, Modifier.fillMaxWidth(), placeholder = { Text("Link M3U  http://...", fontSize = 12.sp) }, singleLine = true, textStyle = TextStyle(fontSize = 13.sp), colors = fieldColors()) }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            TvButton(if (st.busy) "⏳ Baixando…" else "+ Adicionar lista", Color(0xFFFF5A00)) { if (!st.busy) st.addUrl(url, name) }
                            TvButton("Fechar", Btn) { st.showLists = false }
                        }
                    }
                    if (st.busy) item { Text(st.status, color = Cyan, fontSize = 11.sp) }
                    if (st.lastErr.isNotEmpty()) item { Text(st.lastErr, color = Color(0xFFFF8A8A), fontSize = 11.sp, lineHeight = 15.sp) }
                    item { Spacer(Modifier.height(4.dp)); Text("PREFERÊNCIAS", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                    item { TvButton((if (st.adult) "☑" else "☐") + " Mostrar conteúdo adulto (fica no fim da lista)", Btn, 12.sp, shape = RoundedCornerShape(10.dp)) { st.changeAdult(!st.adult) } }
                    item { TvButton((if (st.autoNext) "☑" else "☐") + " Próximo episódio automático", Btn, 12.sp, shape = RoundedCornerShape(10.dp)) { st.changeAutoNext(!st.autoNext) } }
                }
            }
        }
    }
}

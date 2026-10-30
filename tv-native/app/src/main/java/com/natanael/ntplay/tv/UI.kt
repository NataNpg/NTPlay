package com.natanael.ntplay.tv

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.focusable
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
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
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
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ---------------------------------------------------------------- tema
val Bg = Color(0xFF070B1A)
val Bg2 = Color(0xFF0E1430)
val PanelC = Color(0xFF111838)
val CardC = Color(0xFF182046)
val Pink = Color(0xFF8B5CF6)
val Cyan = Color(0xFF3CE0E6)
val Purple = Color(0xFFB27CFF)
val Muted = Color(0xFF9FB0D6)
val Dark = Color(0xFF070B1A)
val Btn = Color(0xFF222B57)
val FocusBg = Color(0xFF3B3F86)

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
    onLong: (() -> Unit)? = null,
    onClick: () -> Unit
): Modifier {
    val src = remember { MutableInteractionSource() }
    val longFired = remember { booleanArrayOf(false) }
    val hov by src.collectIsHoveredAsState()
    SideEffect { fi.hovered = hov }
    val s = if (fi.on) scale else 1f
    return this
        .hoverable(src)
        .onFocusChanged { fi.focused = it.isFocused }
        .then(if (onLong == null) Modifier else Modifier.onPreviewKeyEvent { e ->
            val k = e.key
            if (k == Key.Menu || k == Key.Bookmark) { if (e.type == KeyEventType.KeyDown) onLong(); return@onPreviewKeyEvent true }
            if (k != Key.DirectionCenter && k != Key.Enter && k != Key.NumPadEnter) return@onPreviewKeyEvent false
            val rep = e.nativeKeyEvent.repeatCount
            when {
                e.type == KeyEventType.KeyDown && rep == 1 -> { longFired[0] = true; onLong(); true }
                e.type == KeyEventType.KeyDown && rep > 1 -> true
                e.type == KeyEventType.KeyUp && longFired[0] -> { longFired[0] = false; true }
                else -> false
            }
        })
        .graphicsLayer { scaleX = s; scaleY = s }
        .then(if (border && fi.focused) Modifier.border(3.dp, Color.White, shape) else if (border && hov) Modifier.border(2.dp, Color(0xAAFFFFFF), shape) else Modifier)
        .clickable(interactionSource = src, indication = null, onClick = onClick)
}

/** botão de TV: focado = fundo branco com texto escuro (sempre legível) */
@Composable
fun TvButton(label: String, bg: Color, fontSize: TextUnit = 13.sp, selected: Boolean = false, shape: Shape = RoundedCornerShape(50), compact: Boolean = false, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val fi = rememberFocus()
    val base = if (selected) Pink else bg
    val back = if (fi.on && (base == Btn || base.alpha < 1f)) FocusBg else base
    Text(
        label, color = Color.White, fontSize = fontSize, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
        modifier = modifier.tvClick(fi, shape, 1.08f, border = true, onClick = onClick).background(back, shape)
            .padding(horizontal = if (compact) 10.dp else 16.dp, vertical = if (compact) 5.dp else 9.dp)
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
    var splash by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        delay(1200)
        var w = 0
        while (st.busy && !st.locked && w < 100) { delay(200); w++ } // espera a lista terminar de carregar (máx. ~20 s)
        w = 0
        while (st.trendBusy && !st.locked && w < 50) { delay(200); w++ } // e os recomendados + capas (máx. ~10 s), para não carregar de novo dentro do app
        splash = false
    }
    Box(Modifier.fillMaxSize().background(Bg)) {
        if (st.locked) LoginGate(st) else {
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
        if (st.groupDialog) GroupDialog(st)
        if (st.accountDialog) AccountDialog(st)
        }
        if (!st.locked && !splash && st.play == null && st.upd != null && (st.updForced || !st.updSnooze) && st.updStage != 1 && st.updStage != 0) UpdateDialog(st)
        if (splash) Splash(st)
        if (st.toastMsg.isNotEmpty()) {
            Box(Modifier.fillMaxSize().padding(bottom = 48.dp), contentAlignment = Alignment.BottomCenter) {
                Surface(shape = RoundedCornerShape(14.dp), color = Color(0xF0151C40)) {
                    Text(st.toastMsg, Modifier.padding(horizontal = 20.dp, vertical = 12.dp), color = Color.White, fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
fun Footer() {
    Row(Modifier.fillMaxWidth().padding(top = 4.dp).height(18.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Text("NTPlay", color = Cyan, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        Text("  ·  © Natanael G Silva  ·  djnatan0@live.com", color = Color(0x99FFFFFF), fontSize = 9.sp)
    }
}

@Composable
fun Header(st: AppState) {
    val clock by produceState("") {
        while (true) { value = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()); delay(20000) }
    }
    Row(Modifier.fillMaxWidth().height(46.dp), verticalAlignment = Alignment.CenterVertically) {
        Image(painterResource(R.drawable.logo), null, Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)))
        Spacer(Modifier.width(6.dp))
        Text("NTPlay", style = TextStyle(brush = Brush.horizontalGradient(listOf(Cyan, Purple)), fontSize = 17.sp, fontWeight = FontWeight.ExtraBold))
        Spacer(Modifier.width(12.dp))
        Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            for (t in TABS) {
                val fi = rememberFocus()
                val sel = st.tab == t
                Text(
                    t, maxLines = 1, fontSize = 10.sp, fontWeight = if (sel || fi.on) FontWeight.ExtraBold else FontWeight.Medium,
                    color = if (sel || fi.on) Color.White else Color(0xCCFFFFFF),
                    modifier = Modifier.tvClick(fi, RoundedCornerShape(50), 1.08f, border = true) { st.selectTab(t) }
                        .background(if (sel) Pink else if (fi.on) FocusBg else Color.Transparent, RoundedCornerShape(50))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
        TvButton("🔍 Buscar", Btn, 10.sp, compact = true) { st.showSearch = true }
        Spacer(Modifier.width(6.dp))
        TvButton("☰ Listas", Color(0xFF7C3AED), 10.sp, compact = true) { st.showLists = true }
        Spacer(Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(clock, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(if (st.busy) "⏳ " + st.status else st.status, color = Muted, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(170.dp), textAlign = TextAlign.End)
        }
    }
}

// ---------------------------------------------------------------- cartões
@Composable
fun PosterCard(st: AppState, item: Item, w: Dp, prog: Float = 0f, onRemove: (() -> Unit)? = null, onClick: () -> Unit) {
    if (item.kind == 'l') { LiveCard(st, item, w, onClick); return }
    val onLong = { st.toggleFav(item.key) }
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
        Box(Modifier.tvClick(fi, RoundedCornerShape(12.dp), onLong = onLong, onClick = onClick).clip(RoundedCornerShape(12.dp)).background(tint(item.title)).height(h).fillMaxWidth()) {
            if (img.isNotEmpty()) {
                AsyncImage(
                    model = img, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
                    onError = { val c = Meta.cached(st.store, item)?.img ?: ""; img = if (c.isNotEmpty() && c != img) c else ""}
                )
            } else {
                Text(showTitle(item), Modifier.align(Alignment.Center).padding(10.dp), color = Color(0xDDFFFFFF), fontSize = 13.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 4, overflow = TextOverflow.Ellipsis)
            }
            val tag = Util.audioTag(item)
            if (tag.isNotEmpty()) Text(tag, Modifier.align(Alignment.TopStart).padding(6.dp).background(if (tag == "DUB") Color(0xE68B5CF6) else Color(0xE63CE0E6), RoundedCornerShape(5.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
                color = if (tag == "DUB") Color.White else Color(0xFF05101F), fontSize = 9.sp, fontWeight = FontWeight.ExtraBold)
            if (st.favs.contains(item.key)) Text("★", Modifier.align(Alignment.TopEnd).padding(6.dp).background(Color(0x99000000), CircleShape).padding(horizontal = 6.dp, vertical = 2.dp), color = Color(0xFFFFC107), fontSize = 12.sp)
            if (prog > 0f) Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(4.dp).background(Color(0x66000000))) {
                Box(Modifier.fillMaxWidth(prog.coerceIn(0f, 1f)).fillMaxHeight().background(Pink))
            }
        }
        Text(showTitle(item), Modifier.padding(top = 6.dp, start = 2.dp), color = if (fi.on) Color.White else Color(0xDDFFFFFF), fontSize = 11.sp, fontWeight = if (fi.on) FontWeight.Bold else FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        val y = Util.yearOf(item.title)
        val q = Util.quality(item.title)
        val sub = listOfNotNull(y.ifEmpty { null }, if (item.kind == 's') "Série" else null, q.ifEmpty { null }).joinToString(" · ")
        if (sub.isNotEmpty()) Text(sub, Modifier.padding(start = 2.dp), color = Muted, fontSize = 9.sp, maxLines = 1)
        if (onRemove != null) {
            val rfi = rememberFocus()
            Text("✕", color = Color.White, fontSize = 7.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 3.dp).tvClick(rfi, CircleShape, 1.15f, onClick = onRemove).background(if (rfi.on) Pink else Btn, CircleShape).padding(horizontal = 6.dp, vertical = 2.dp))
        }
    }
}

@Composable
fun LiveCard(st: AppState, item: Item, w: Dp, onClick: () -> Unit) {
    @Suppress("UNUSED_VARIABLE") val lv = st.logoVer
    var img by remember(item.key, item.logo) { mutableStateOf(item.logo) }
    val fi = rememberFocus()
    Column(Modifier.width(w)) {
        Box(
            Modifier.tvClick(fi, RoundedCornerShape(12.dp), onLong = { st.toggleFav(item.key) }, onClick = onClick).clip(RoundedCornerShape(12.dp))
                .background(Brush.linearGradient(listOf(CardC, Color(0xFF243064)))).aspectRatio(16f / 10f).fillMaxWidth()
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
        @Suppress("UNUSED_VARIABLE") val ev = st.epgVer
        Epg.nowTitle(item)?.let { Text("▶ " + it, Modifier.padding(start = 2.dp), color = Cyan, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
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
fun savedGridState(st: AppState, key: String): LazyGridState {
    val saved = st.scroll[key] ?: (0 to 0)
    val state = rememberLazyGridState(saved.first, saved.second)
    DisposableEffect(key) { onDispose { st.scroll[key] = state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset } }
    return state
}

@Composable
fun savedListState(st: AppState, key: String): LazyListState {
    val saved = st.scroll[key] ?: (0 to 0)
    val state = rememberLazyListState(saved.first, saved.second)
    DisposableEffect(key) { onDispose { st.scroll[key] = state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset } }
    return state
}

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
                if (!st.busy) TvButton("☰ Carregar lista M3U", Color(0xFF7C3AED), 15.sp) { st.showLists = true }
            }
        }
        return
    }
    val tab = st.tab
    val groups = when (tab) { "TV AO VIVO" -> lib.liveGroups; "FILMES" -> lib.movieGroups; "SÉRIES" -> lib.serieGroups; else -> null }
    val yearBase: List<Item>? = when (tab) {
        "FILMES" -> if (st.group.isEmpty()) lib.movies else groups?.firstOrNull { it.first == st.group }?.second
        "SÉRIES" -> if (st.group.isEmpty()) lib.series else groups?.firstOrNull { it.first == st.group }?.second
        "ANIMES" -> lib.anime
        else -> null
    }
    val years = remember(lib, tab, st.group, yearBase) {
        if (yearBase == null) emptyList() else yearBase.groupingBy { yearOfItem(st, it) }.eachCount().filterKeys { it.isNotEmpty() }.toList().sortedByDescending { it.first }
    }
    Row(Modifier.fillMaxSize().padding(top = 6.dp)) {
        if (groups != null) Sidebar(st, groups, when (tab) { "TV AO VIVO" -> lib.live.size; "FILMES" -> lib.movies.size; else -> lib.series.size }, tab)
        Column(Modifier.weight(1f).fillMaxHeight()) {
            if (yearBase != null) Row(Modifier.fillMaxWidth().padding(end = 16.dp, bottom = 2.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                if (st.yearSel.isNotEmpty()) { TvButton("✕ Limpar", Btn, 10.sp, compact = true) { st.yearSel = "" }; Spacer(Modifier.width(8.dp)) }
                TvButton("📅 Ano: " + (st.yearSel.ifEmpty { "Todos" }), if (st.yearSel.isNotEmpty()) Pink else Btn, 10.sp, compact = true) { st.yearDialog = true }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (groups != null) {
                    if (st.yearSel.isNotEmpty() && yearBase != null) {
                        val f = remember(yearBase, st.yearSel) { yearBase.filter { yearOfItem(st, it) == st.yearSel } }
                        GridOf(st, f.take(900), (if (st.group.isEmpty()) tab else st.group) + " · " + st.yearSel)
                    } else if (st.group.isEmpty()) {
                        val ls = savedListState(st, "shelves:" + tab)
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
                    "FUTEBOL" -> FutebolScreen(st, lib)
                    "INFANTIL" -> GridOf(st, lib.kids.take(600), "Infantil")
                    "ANIMES" -> AnimeScreen(st, lib)
                    "FAVORITOS" -> FavoritesScreen(st, lib)
                    else -> Recom(st, lib)
                }
            }
        }
    }
    if (st.yearDialog) YearDialog(st, years)
}

fun yearOfItem(st: AppState, it: Item): String {
    val y = Util.yearOf(it.title)
    if (y.isNotEmpty()) return y
    val c = Meta.cached(st.store, it)?.y ?: ""
    return if (c.length == 4 && c.all { ch -> ch.isDigit() }) c else ""
}

@Composable
fun YearDialog(st: AppState, years: List<Pair<String, Int>>) {
    Dialog(onDismissRequest = { st.yearDialog = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth(0.7f).fillMaxHeight(0.8f), shape = RoundedCornerShape(18.dp), color = PanelC) {
            Column(Modifier.padding(20.dp)) {
                Text("Filtrar por ano de lançamento", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                Text(if (years.isEmpty()) "Nenhum título desta seção traz o ano no nome." else "Só aparecem anos que existem na sua lista.", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp, bottom = 10.dp))
                val gs = rememberLazyGridState()
                LazyVerticalGrid(columns = GridCells.Adaptive(110.dp), state = gs, modifier = Modifier.weight(1f).mouseScroll(gs), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { TvButton("Todos", Btn, 12.sp, selected = st.yearSel.isEmpty()) { st.yearSel = ""; st.yearDialog = false } }
                    items(years, key = { it.first }) { (y, n) ->
                        TvButton("$y  ($n)", Btn, 12.sp, selected = st.yearSel == y) { st.yearSel = y; st.yearDialog = false }
                    }
                }
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
        val gs = savedGridState(st, "grid:" + title)
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
    val bg = if (sel) Pink else if (fi.on) FocusBg else Color.Transparent
    val fg = if (sel || fi.on) Color.White else Color(0xCCFFFFFF)
    Row(
        Modifier.fillMaxWidth().tvClick(fi, RoundedCornerShape(10.dp), 1.03f, border = true, onClick = onClick).background(bg, RoundedCornerShape(10.dp)).padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(name, Modifier.weight(1f), color = fg, fontSize = 12.sp, fontWeight = if (sel || fi.on) FontWeight.Bold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("$n", color = if (sel || fi.on) Color(0xDDFFFFFF) else Muted, fontSize = 9.sp)
    }
}

// ---------------------------------------------------------------- recomendações (com destaque)
@Composable
fun Recom(st: AppState, lib: Library) {
    val ver = st.progVer
    val cw = remember(ver, lib) { st.continueList() }
    val favs = remember(st.favs, lib) { lib.every.filter { st.favs.contains(it.key) } }
    val favL = favs.filter { it.kind == 'l' }; val favM = favs.filter { it.kind == 'm' }; val favS = favs.filter { it.kind == 's' }
    val trend = remember(st.trend) { st.trend.map { Trending.Row(it.title, it.items.filter { x -> x.kind != 'l' }) }.filter { it.items.isNotEmpty() } }
    val heroes = remember(lib, trend) {
        val out = LinkedHashMap<String, Item>()
        for (r in trend) for (i in r.items.take(4)) if (i.kind != 'l') out.putIfAbsent(i.key, i)
        val rnd = java.util.Random(System.currentTimeMillis() / 3_600_000L)
        val mv = lib.movies.filter { it.logo.isNotEmpty() && !it.adult }
        val sr = lib.series.filter { it.logo.isNotEmpty() && !it.adult }
        for (k in 0 until 6) {
            if (mv.isNotEmpty()) mv[rnd.nextInt(minOf(mv.size, 400))].let { out.putIfAbsent(it.key, it) }
            if (sr.isNotEmpty()) sr[rnd.nextInt(minOf(sr.size, 400))].let { out.putIfAbsent(it.key, it) }
        }
        out.values.take(12)
    }
    val ls = rememberLazyListState()
    LazyColumn(Modifier.fillMaxSize().mouseScroll(ls), state = ls, contentPadding = PaddingValues(bottom = 20.dp)) {
        if (heroes.isNotEmpty()) item { HeroCarousel(st, heroes) }
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
        for (r in trend) item { ShelfRow(r.title, r.items.size, null) { items(r.items.take(40), key = { it.key }) { it2 -> CardFor(st, it2, r.items) } } }
        if (favL.isNotEmpty()) item { ShelfRow("★ Canais favoritos", favL.size, null) { items(favL, key = { it.key }) { it2 -> CardFor(st, it2, favL) } } }
        if (favM.isNotEmpty()) item { ShelfRow("★ Filmes favoritos", favM.size, null) { items(favM, key = { it.key }) { it2 -> CardFor(st, it2, favM) } } }
        if (favS.isNotEmpty()) item { ShelfRow("★ Séries favoritas", favS.size, null) { items(favS, key = { it.key }) { it2 -> CardFor(st, it2, favS) } } }
        if (favs.isEmpty()) item { Text("Dica: segure o botão OK (ou aperte Menu) em qualquer canal, filme ou série para adicionar aos favoritos.", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) }
        if (trend.isEmpty() && lib.movies.isNotEmpty()) item { ShelfRow("Filmes", lib.movies.size, { st.selectTab("FILMES") }) { items(lib.movies.take(30), key = { it.key }) { it2 -> CardFor(st, it2, lib.movies) } } }
        if (trend.isEmpty() && lib.series.isNotEmpty()) item { ShelfRow("Séries", lib.series.size, { st.selectTab("SÉRIES") }) { items(lib.series.take(30), key = { it.key }) { it2 -> CardFor(st, it2, lib.series) } } }
    }
}

@Composable
fun HeroCarousel(st: AppState, list: List<Item>) {
    var idx by remember(list) { mutableStateOf(0) }
    var hold by remember { mutableStateOf(false) }
    LaunchedEffect(list, hold, idx) { if (!hold) { delay(9000); idx = (idx + 1) % list.size } }
    Box(Modifier.onFocusChanged { hold = it.hasFocus }) {
        Hero(st, list[idx.coerceIn(0, list.size - 1)], idx, list.size, { idx = (idx - 1 + list.size) % list.size }, { idx = (idx + 1) % list.size })
    }
}

@Composable
fun Hero(st: AppState, item: Item, idx: Int = 0, total: Int = 1, onPrev: () -> Unit = {}, onNext: () -> Unit = {}) {
    var info by remember(item.key) { mutableStateOf(Meta.cached(st.store, item)) }
    LaunchedEffect(item.key) { val i = Meta.get(st.store, item); if (i != null) info = i }
    val img = item.logo.ifEmpty { info?.img ?: "" }
    Box(Modifier.fillMaxWidth().height(230.dp).padding(horizontal = 16.dp, vertical = 8.dp).clip(RoundedCornerShape(18.dp)).background(tint(item.title))) {
        if (img.isNotEmpty()) AsyncImage(img, null, Modifier.fillMaxSize().graphicsLayer { alpha = 0.55f }, contentScale = ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Bg, Color(0xCC070B1A), Color.Transparent))))
        Row(Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Center) {
                Text(if (item.kind == 's') "SÉRIE EM DESTAQUE" else "FILME EM DESTAQUE", color = Cyan, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
                Text(showTitle(item), color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val meta = listOfNotNull(info?.y?.ifEmpty { null } ?: Util.yearOf(item.title).ifEmpty { null }, info?.r?.takeIf { it > 0 }?.let { "★ %.1f".format(it) }, item.group).joinToString("  •  ")
                Text(meta, color = Muted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(vertical = 3.dp))
                Text(info?.ov ?: "", color = Color(0xDDFFFFFF), fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 15.sp, modifier = Modifier.fillMaxWidth(0.8f))
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    TvButton("▶ Assistir", Pink, 11.sp, compact = true) { st.playItem(item) }
                    TvButton("ⓘ Detalhes", Btn, 11.sp, compact = true) { st.openDetail(item) }
                    TvButton(if (st.favs.contains(item.key)) "★" else "☆", Btn, 11.sp, compact = true) { st.toggleFav(item.key) }
                    if (total > 1) {
                        Spacer(Modifier.width(10.dp))
                        TvButton("‹", Btn, 14.sp, compact = true, onClick = onPrev)
                        TvButton("›", Btn, 14.sp, compact = true, onClick = onNext)
                        Spacer(Modifier.width(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            for (i in 0 until total) Box(Modifier.size(if (i == idx) 18.dp else 6.dp, 6.dp).background(if (i == idx) Pink else Color(0x66FFFFFF), RoundedCornerShape(3.dp)))
                        }
                    }
                }
            }
            if (img.isNotEmpty()) AsyncImage(img, null, Modifier.height(190.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
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
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x66070B1A), Bg))))
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
                    TvButton("＋ Grupo", Btn, 13.sp) { st.openGroups(item) }
                    TvButton("‹ Voltar", Btn, 13.sp) { st.detail = null }
                }
                if (needEps && st.epsErr.isNotEmpty()) Text(st.epsErr + " Toque em \"Carregar episódios\" para tentar de novo.", color = Color(0xFFFF9A9A), fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
            }
            // coluna de episódios (altura inteira)
            if (serie) Column(Modifier.weight(0.58f).fillMaxHeight().background(Color(0xAA0E1430), RoundedCornerShape(16.dp)).padding(12.dp)) {
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
    val fg = Color.White
    Row(
        Modifier.fillMaxWidth().tvClick(fi, RoundedCornerShape(10.dp), 1.02f, border = true) { st.playItem(item, e) }
            .background(if (fi.on) FocusBg else Color(0x14FFFFFF), RoundedCornerShape(10.dp)).padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("${e.e}", Modifier.width(34.dp), color = Cyan, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
        Text(epTitle(item, e), Modifier.weight(1f), color = fg, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(if (p?.done == true) "✓ visto" else if (p != null && p.t > 0) Util.fmt(p.t) else "▶", color = Muted, fontSize = 11.sp)
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

@Composable
fun FavoritesScreen(st: AppState, lib: Library) {
    @Suppress("UNUSED_VARIABLE") val gv = st.groupsVer
    val names = st.groupNames()
    val favs = lib.every.filter { st.favs.contains(it.key) }
    val ls = savedListState(st, "favorites")
    LazyColumn(Modifier.fillMaxSize().mouseScroll(ls), state = ls, contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp)) {
        item {
            Row(Modifier.padding(start = 16.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(4.dp).height(18.dp).background(Pink, RoundedCornerShape(2.dp)))
                Spacer(Modifier.width(8.dp))
                Text("Minha lista e grupos", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.width(12.dp))
                TvButton("＋ Novo grupo", Color(0xFF2B6CB0), 11.sp, compact = true) { st.openGroups(null) }
            }
        }
        for (name in names) {
            val gitems = st.groupItems(name)
            item(key = "grp_" + name) {
                ShelfRow(name, gitems.size, null) {
                    if (gitems.isEmpty()) item { Text("Vazio — use o botão ＋ Grupo na tela do filme/série.", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(8.dp)) }
                    else items(gitems.take(40), key = { it.key }) { it2 -> CardFor(st, it2, gitems) }
                }
            }
        }
        item(key = "favshelf") {
            ShelfRow("★ Favoritos", favs.size, null) {
                if (favs.isEmpty()) item { Text("Toque e segure em um título para favoritar.", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(8.dp)) }
                else items(favs.take(40), key = { it.key }) { it2 -> CardFor(st, it2, favs) }
            }
        }
        if (names.isEmpty() && favs.isEmpty()) item { Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { Text("Crie um grupo ou favorite títulos.", color = Muted, fontSize = 14.sp) } }
    }
}

@Composable
fun GroupDialog(st: AppState) {
    val item = st.groupDialogItem
    @Suppress("UNUSED_VARIABLE") val gv = st.groupsVer
    val names = st.groupNames()
    var newName by remember { mutableStateOf("") }
    Dialog(onDismissRequest = { st.groupDialog = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth(0.62f).fillMaxHeight(0.82f), shape = RoundedCornerShape(18.dp), color = PanelC) {
            Column(Modifier.padding(20.dp)) {
                Text(if (item != null) "Adicionar a um grupo" else "Meus grupos personalizados", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                if (item != null) Text(showTitle(item), color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
                Spacer(Modifier.height(12.dp))
                val ls = rememberLazyListState()
                LazyColumn(Modifier.weight(1f).mouseScroll(ls), state = ls, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (names.isEmpty()) item { Text("Nenhum grupo ainda. Crie um abaixo.", color = Muted, fontSize = 12.sp) }
                    items(names, key = { it }) { n ->
                        Row(Modifier.fillMaxWidth().background(Color(0x14FFFFFF), RoundedCornerShape(10.dp)).padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text(n, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(st.groupCount(n).toString() + " títulos", color = Muted, fontSize = 10.sp)
                            }
                            if (item != null) TvButton(if (st.inGroup(n, item.key)) "☑ No grupo" else "☐ Adicionar", Btn, 10.sp, compact = true) { st.toggleInGroup(n, item.key) }
                            TvButton("🗑", Color(0xFF7A2E3A), 10.sp, compact = true) { st.deleteGroup(n) }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(newName, { newName = it }, Modifier.weight(1f), placeholder = { Text("Nome do novo grupo", fontSize = 12.sp) }, singleLine = true, textStyle = TextStyle(fontSize = 13.sp), colors = fieldColors())
                    TvButton("Criar", Color(0xFF2B6CB0), 12.sp) { if (newName.isNotBlank()) { val nm = newName.trim(); st.createGroup(nm); if (item != null && !st.inGroup(nm, item.key)) st.toggleInGroup(nm, item.key); newName = "" } }
                }
                Spacer(Modifier.height(8.dp))
                TvButton("Fechar", Btn) { st.groupDialog = false }
            }
        }
    }
}

// ---------------------------------------------------------------- listas M3U + QR code
@Composable
fun ListsDialog(st: AppState) {
    val (topic, key) = remember { st.store.relay() }
    val local = remember { LocalServer.start { c, n -> st.onRelay(c, n) } }
    DisposableEffect(Unit) { onDispose { LocalServer.stop() } }
    val link = local ?: (Util.SEND_BASE + "#t=" + topic + "&k=" + key)
    val qr = remember(link) { Qr.bitmap(link, 360).asImageBitmap() }
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var xHost by remember { mutableStateOf("") }
    var xUser by remember { mutableStateOf("") }
    var xPass by remember { mutableStateOf("") }
    val lists = remember(st.listsVer, st.busy) { st.store.lists() }
    LaunchedEffect(Unit) { st.refreshAccounts() }
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
                    Text(if (local != null) "Celular e TV no mesmo Wi‑Fi: aponte a câmera, cole o link da lista e envie. Funciona sem internet externa." else "Aponte a câmera do celular, cole o link da lista e envie. Ela chega aqui sozinha.", color = Color(0xDDFFFFFF), fontSize = 10.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
                    Text("⏳ Aguardando o celular…", color = Cyan, fontSize = 10.sp, modifier = Modifier.padding(top = 6.dp))
                    Spacer(Modifier.height(12.dp))
                    Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0x22FFFFFF)))
                    Spacer(Modifier.height(10.dp))
                    ContatoTeste(compact = true)
                }
                val ls = rememberLazyListState()
                LazyColumn(Modifier.weight(1f).fillMaxHeight().mouseScroll(ls), state = ls, verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(end = 4.dp, bottom = 24.dp)) {
                    item {
                        Column(Modifier.fillMaxWidth().background(Color(0x1A8B5CF6), RoundedCornerShape(12.dp)).padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text("☁ NUVEM  ·  conta " + st.syncUser, color = Cyan, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                            if (st.accessText().isNotEmpty()) Text("🗓 " + st.accessText(), color = Color(0xFF34D399), fontSize = 10.sp)
                            Text("Suas listas, favoritos, grupos e progresso ficam salvos na conta (criptografados) e aparecem em outro aparelho.", color = Muted, fontSize = 9.sp, lineHeight = 12.sp)
                            if (st.syncMsg.isNotEmpty()) Text(st.syncMsg, color = if (st.syncMsg.startsWith("☁")) Color(0xFF34D399) else Color(0xFFFFB4B4), fontSize = 10.sp)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TvButton(if (st.syncBusy) "⏳ Sincronizando…" else "↻ Sincronizar agora", Pink, 10.sp, compact = true) { if (!st.syncBusy) st.scope.launch { st.syncNow(true) } }
                                TvButton("Sair da conta", Color(0xFF7A2E3A), 10.sp, compact = true) { st.showLists = false; st.accountLogout() }
                            }
                        }
                    }
                    item { Text("MINHAS LISTAS", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                    item { st.lib?.let { lb -> Text("Ao vivo ${lb.live.size}  ·  Filmes ${lb.movies.size}  ·  Séries ${lb.series.size}", color = Cyan, fontSize = 10.sp, fontWeight = FontWeight.Bold) } }
                    if (lists.isEmpty()) item { Text("Nenhuma lista ainda.", color = Muted, fontSize = 12.sp) }
                    items(lists, key = { it.id }) { m ->
                        Row(Modifier.fillMaxWidth().background(Color(0x14FFFFFF), RoundedCornerShape(12.dp)).padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TvButton(if (m.active) "☑ Usar" else "☐ Usar", Btn, 10.sp, compact = true) { st.toggleList(m.id) }
                            Column(Modifier.weight(1f)) {
                                Text(m.name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                @Suppress("UNUSED_VARIABLE") val av = st.accVer
                                val acc = st.accountText(m.id)
                                Text("${m.count} itens" + if (acc.isNotEmpty()) "  ·  $acc" else "", color = if (acc.startsWith("⚠")) Color(0xFFFF8A8A) else Muted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            TvButton("↻ Atualizar", Btn, 9.sp, compact = true) { st.refreshList(m) }
                            TvButton("🗑 Excluir", Color(0xFF7A2E3A), 9.sp, compact = true) { st.deleteList(m.id) }
                        }
                    }
                    item { Spacer(Modifier.height(4.dp)); Text("ADICIONAR POR XTREAM CODES (servidor, usuário e senha)", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                    item { OutlinedTextField(xHost, { xHost = it }, Modifier.fillMaxWidth(), placeholder = { Text("Servidor  http://servidor.com:8080", fontSize = 12.sp) }, singleLine = true, textStyle = TextStyle(fontSize = 13.sp), colors = fieldColors()) }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(xUser, { xUser = it }, Modifier.weight(1f), placeholder = { Text("Usuário", fontSize = 12.sp) }, singleLine = true, textStyle = TextStyle(fontSize = 13.sp), colors = fieldColors())
                            OutlinedTextField(xPass, { xPass = it }, Modifier.weight(1f), placeholder = { Text("Senha", fontSize = 12.sp) }, singleLine = true, visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(), textStyle = TextStyle(fontSize = 13.sp), colors = fieldColors())
                        }
                    }
                    item { TvButton(if (st.busy) "⏳ Entrando…" else "🔑 Entrar e adicionar (Xtream)", Color(0xFF7C3AED), 12.sp) {
                        if (!st.busy) {
                            val h = xHost.trim().trimEnd('/').let { if (it.contains("://")) it else "http://$it" }
                            if (xHost.isBlank() || xUser.isBlank() || xPass.isBlank()) st.toast("Preencha servidor, usuário e senha.")
                            else st.addUrl(h + "/get.php?username=" + Net.enc(xUser.trim()) + "&password=" + Net.enc(xPass.trim()) + "&type=m3u_plus&output=ts", name.ifBlank { xUser.trim() })
                        }
                    } }
                    item { Spacer(Modifier.height(4.dp)); Text("ADICIONAR NOVA LISTA", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                    item { OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), placeholder = { Text("Nome da lista (opcional)", fontSize = 12.sp) }, singleLine = true, textStyle = TextStyle(fontSize = 13.sp), colors = fieldColors()) }
                    item { OutlinedTextField(url, { url = it }, Modifier.fillMaxWidth(), placeholder = { Text("Link M3U  http://...", fontSize = 12.sp) }, singleLine = true, textStyle = TextStyle(fontSize = 13.sp), colors = fieldColors()) }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            TvButton(if (st.busy) "⏳ Baixando…" else "+ Adicionar lista", Color(0xFF7C3AED)) { if (!st.busy) st.addUrl(url, name) }
                            TvButton("Fechar", Btn) { st.showLists = false }
                        }
                    }
                    if (st.busy) item { Text(st.status, color = Cyan, fontSize = 11.sp) }
                    if (st.lastErr.isNotEmpty()) item { Text(st.lastErr, color = Color(0xFFFF8A8A), fontSize = 11.sp, lineHeight = 15.sp) }
                    item { Spacer(Modifier.height(4.dp)); Text("PREFERÊNCIAS", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                    item { TvButton((if (st.adult) "☑" else "☐") + " Mostrar conteúdo adulto (fica no fim da lista)", Btn, 12.sp, shape = RoundedCornerShape(10.dp)) { st.changeAdult(!st.adult) } }
                    item { TvButton((if (st.autoNext) "☑" else "☐") + " Próximo episódio automático", Btn, 12.sp, shape = RoundedCornerShape(10.dp)) { st.changeAutoNext(!st.autoNext) } }
                    item { TvButton((if (st.subs) "☑" else "☐") + " Mostrar legendas (desmarcado = legendas desligadas em tudo)", Btn, 12.sp, shape = RoundedCornerShape(10.dp)) { st.changeSubs(!st.subs) } }
                    item { TvButton("Buffer de filmes/séries: " + when (st.bufferMode) { 1 -> "Grande (~25 s)"; 2 -> "Muito grande (~45 s)"; else -> "Padrão" }, Btn, 12.sp, shape = RoundedCornerShape(10.dp)) { st.changeBuffer((st.bufferMode + 1) % 3) } }
                    item { Spacer(Modifier.height(4.dp)); Text("COMPATIBILIDADE DE FONTE (XTREAM)", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                    item { TvButton((if (st.adultByName) "☑" else "☐") + " Detectar adulto pelo nome da categoria (quando o provedor não marca)", Btn, 12.sp, shape = RoundedCornerShape(10.dp)) { st.changeAdultByName(!st.adultByName) } }
                    item { TvButton((if (st.base64) "☑" else "☐") + " Decodificar nomes em Base64 (só se os nomes aparecerem embaralhados)", Btn, 12.sp, shape = RoundedCornerShape(10.dp)) { st.changeBase64(!st.base64) } }
                    item { Spacer(Modifier.height(4.dp)); Text("ATUALIZAÇÃO DAS LISTAS", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                    item { TvButton((if (st.updateOnStart) "☑" else "☐") + " Atualizar as listas ao abrir o app", Btn, 12.sp, shape = RoundedCornerShape(10.dp)) { st.changeUpdateOnStart(!st.updateOnStart) } }
                    item { TvButton("Atualização automática: " + when (st.updateHours) { 6 -> "a cada 6 h"; 12 -> "a cada 12 h"; 24 -> "a cada 24 h"; else -> "desligada" }, Btn, 12.sp, shape = RoundedCornerShape(10.dp)) { st.changeUpdateHours(when (st.updateHours) { 0 -> 6; 6 -> 12; 12 -> 24; else -> 0 }) } }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- abertura
@Composable
fun Splash(st: AppState) {
    Box(Modifier.fillMaxSize().background(Bg).focusable()) {
        Image(painterResource(R.drawable.splash), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Column(Modifier.align(Alignment.BottomCenter).padding(bottom = 36.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            androidx.compose.material3.CircularProgressIndicator(color = Cyan, strokeWidth = 3.dp, modifier = Modifier.size(26.dp))
            Spacer(Modifier.height(8.dp))
            Text(if (st.busy) st.status else "Abrindo…", color = Color(0xCCFFFFFF), fontSize = 12.sp)
        }
    }
}

// ---------------------------------------------------------------- animes (Crunchyroll / Funimation Now)
@Composable
fun AnimeScreen(st: AppState, lib: Library) {
    var src by remember { mutableStateOf("") }
    val cr = remember(lib) { lib.anime.filter { Util.animeSrc(it) == "CR" } }
    val fn = remember(lib) { lib.anime.filter { Util.animeSrc(it) == "FN" } }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(start = 16.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((k, lab, n, col) in listOf(
                Quad("", "Todos", lib.anime.size, Pink),
                Quad("CR", "Crunchyroll", cr.size, Color(0xFFF47521)),
                Quad("FN", "Funimation Now", fn.size, Color(0xFF6D28D9)))) {
                val fi = rememberFocus()
                Text("$lab  $n", fontSize = 12.sp, color = Color.White, fontWeight = if (src == k) FontWeight.Bold else FontWeight.Medium,
                    modifier = Modifier.tvClick(fi, RoundedCornerShape(50)) { src = k }
                        .background(if (src == k) col else if (fi.on) FocusBg else Color(0x22FFFFFF), RoundedCornerShape(50)).padding(horizontal = 14.dp, vertical = 6.dp))
            }
        }
        val l0 = when (src) { "CR" -> cr; "FN" -> fn; else -> lib.anime }
        val l = if (st.yearSel.isNotEmpty()) l0.filter { yearOfItem(st, it) == st.yearSel } else l0.take(600)
        val name = when (src) { "CR" -> "Crunchyroll"; "FN" -> "Funimation Now"; else -> "Animes" }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (l.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Nenhum conteúdo de $name encontrado nas suas listas.", color = Muted, fontSize = 14.sp)
            } else GridOf(st, l, name)
        }
    }
}

private data class Quad(val a: String, val b: String, val c: Int, val d: Color)

// ---------------------------------------------------------------- conta / nuvem
@Composable
fun AccountDialog(st: AppState) {
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    Dialog(onDismissRequest = { st.accountDialog = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth(0.55f).wrapContentHeight(), shape = RoundedCornerShape(18.dp), color = PanelC) {
            Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("☁ Conta NTPlay", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                Text("Guarda suas listas, favoritos, grupos e progresso na nuvem para usar em outro aparelho. Seus dados são criptografados no aparelho: nem o servidor consegue ler. Se esquecer a senha, não há como recuperar os dados da nuvem.", color = Muted, fontSize = 10.sp, lineHeight = 14.sp)
                if (st.syncUser.isNotEmpty()) {
                    Text("Conectado como  " + st.syncUser, color = Cyan, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    if (st.accessText().isNotEmpty()) Text("🗓 " + st.accessText(), color = Color(0xFF34D399), fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TvButton(if (st.syncBusy) "⏳ Sincronizando…" else "↻ Sincronizar agora", Pink, 12.sp) { if (!st.syncBusy) st.scope.launch { st.syncNow(true) } }
                        TvButton("Sair", Color(0xFF7A2E3A), 12.sp) { st.accountLogout() }
                    }
                } else {
                    OutlinedTextField(user, { user = it }, Modifier.fillMaxWidth(), placeholder = { Text("Usuário", fontSize = 12.sp) }, singleLine = true, textStyle = TextStyle(fontSize = 13.sp), colors = fieldColors())
                    OutlinedTextField(pass, { pass = it }, Modifier.fillMaxWidth(), placeholder = { Text("Senha (mín. 6)", fontSize = 12.sp) }, singleLine = true, visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(), textStyle = TextStyle(fontSize = 13.sp), colors = fieldColors())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TvButton(if (st.syncBusy) "⏳…" else "Entrar", Pink, 12.sp) { st.accountLogin(user, pass) }
                    }
                }
                if (st.syncMsg.isNotEmpty()) Text(st.syncMsg, color = if (st.syncMsg.startsWith("☁") || st.syncMsg.startsWith("Conta") || st.syncMsg.startsWith("Entrou")) Color(0xFF34D399) else Color(0xFFFFB4B4), fontSize = 11.sp)
                TvButton("Fechar", Btn, 11.sp, compact = true) { st.accountDialog = false }
            }
        }
    }
}

// ---------------------------------------------------------------- tela de entrada (acesso por usuário e senha)
@Composable
fun LoginGate(st: AppState) {
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    Box(Modifier.fillMaxSize().background(Bg)) {
        Image(painterResource(R.drawable.splash), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alpha = 0.35f)
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Surface(Modifier.width(380.dp), shape = RoundedCornerShape(20.dp), color = Color(0xEE0E1430)) {
                Column(Modifier.padding(26.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Image(painterResource(R.drawable.logo), null, Modifier.size(64.dp).clip(RoundedCornerShape(16.dp)))
                    Text("Entrar no NTPlay", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                    OutlinedTextField(user, { user = it }, Modifier.fillMaxWidth(), placeholder = { Text("Usuário", fontSize = 13.sp) }, singleLine = true, textStyle = TextStyle(fontSize = 14.sp), colors = fieldColors())
                    OutlinedTextField(pass, { pass = it }, Modifier.fillMaxWidth(), placeholder = { Text("Senha", fontSize = 13.sp) }, singleLine = true, visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(), textStyle = TextStyle(fontSize = 14.sp), colors = fieldColors())
                    TvButton(if (st.syncBusy) "⏳ Entrando…" else "Entrar", Pink, 14.sp) { st.accountLogin(user, pass) }
                    if (st.accessMsg.isNotEmpty()) Text(st.accessMsg, color = if (st.accessMsg == "Entrando…") Cyan else Color(0xFFFFB4B4), fontSize = 12.sp, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(2.dp))
                    ContatoTeste()
                }
            }
        }
    }
}

/** QR code + telefone para pedir teste do aplicativo ou da lista. Usado na entrada e em ☰ Listas. */
@Composable
fun ContatoTeste(compact: Boolean = false) {
    val wa = remember { Qr.bitmap("https://wa.me/5542920028569", 240).asImageBitmap() }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Image(wa, "QR code do WhatsApp", Modifier.size(if (compact) 62.dp else 78.dp).background(Color.White, RoundedCornerShape(8.dp)).padding(4.dp))
        Column {
            Text("Peça seu teste", color = Color.White, fontSize = if (compact) 11.sp else 12.sp, fontWeight = FontWeight.Bold)
            Text("do aplicativo ou da lista", color = Muted, fontSize = if (compact) 9.sp else 10.sp)
            Text("(42) 92002-8569", color = Cyan, fontSize = if (compact) 12.sp else 13.sp, fontWeight = FontWeight.ExtraBold)
            Text("Aponte a câmera do celular", color = Muted, fontSize = 9.sp)
        }
    }
}

// ---------------------------------------------------------------- atualização do aplicativo
@Composable
fun UpdateDialog(st: AppState) {
    val i = st.upd ?: return
    val forced = st.updForced
    Dialog(onDismissRequest = { if (!forced) st.updSnooze = true }, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = !forced, dismissOnClickOutside = false)) {
        Surface(Modifier.width(420.dp), shape = RoundedCornerShape(18.dp), color = PanelC) {
            Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("⬆ Nova versão " + i.name, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                if (st.updStage == 2) {
                    Text(if (forced) "Esta versão do NTPlay não é mais aceita. Instale a atualização para continuar." else "A atualização já foi baixada e está pronta. Toque em Instalar; o Android vai pedir uma confirmação.", color = Muted, fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TvButton("Instalar agora", Pink, 13.sp) { st.installUpdate() }
                        if (!forced) TvButton("Depois", Btn, 12.sp) { st.updSnooze = true }
                    }
                } else {
                    Text("Não consegui baixar a atualização agora. Confira a internet.", color = Color(0xFFFFB4B4), fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TvButton("Tentar de novo", Pink, 13.sp) { st.checkUpdate() }
                        if (!forced) TvButton("Depois", Btn, 12.sp) { st.updSnooze = true }
                    }
                }
            }
        }
    }
}

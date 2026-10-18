package com.natanael.ntplay.tv

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

val Bg = Color(0xFF08142E)
val PanelC = Color(0xFF0C2352)
val CardC = Color(0xFF0A1931)
val Pink = Color(0xFFE93A7D)
val Cyan = Color(0xFF22D3EE)
val Purple = Color(0xFFA855F7)

@Composable
fun NtTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(primary = Pink, background = Bg, surface = PanelC), content = content)
}

@Composable
fun Modifier.tvFocus(shape: Shape = RoundedCornerShape(10.dp)): Modifier {
    var f by remember { mutableStateOf(false) }
    val src = remember { MutableInteractionSource() }
    val hov by src.collectIsHoveredAsState()
    return this.hoverable(src).onFocusChanged { f = it.isFocused }
        .then(if (f) Modifier.border(3.dp, Cyan, shape) else if (hov) Modifier.border(2.dp, Color(0xAA22D3EE), shape) else Modifier)
}

private fun tint(s: String): Color = Color.hsv((s.hashCode().toLong().let { Math.floorMod(it, 360L) }).toFloat(), 0.45f, 0.32f)

// ---------------------------------------------------------------- app
@Composable
fun App(st: AppState) {
    Box(Modifier.fillMaxSize().background(Bg)) {
        Column(Modifier.fillMaxSize()) {
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
            Box(Modifier.fillMaxSize().padding(bottom = 36.dp), contentAlignment = Alignment.BottomCenter) {
                Surface(shape = RoundedCornerShape(12.dp), color = Color(0xEE1B2A4A)) {
                    Text(st.toastMsg, Modifier.padding(horizontal = 18.dp, vertical = 10.dp), color = Color.White, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
fun Footer() {
    Box(Modifier.fillMaxWidth().height(22.dp).background(Color(0xFF081426)), contentAlignment = Alignment.Center) {
        Text("NTPlay · © Natanael G Silva · djnatan0@live.com", color = Color(0x99FFFFFF), fontSize = 10.sp)
    }
}

@Composable
fun Header(st: AppState) {
    val clock by produceState("") {
        while (true) { value = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()); delay(20000) }
    }
    Row(Modifier.fillMaxWidth().height(54.dp).background(Color(0xFF0A1931)).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Image(painterResource(R.drawable.logo), null, Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)))
        Spacer(Modifier.width(8.dp))
        Text("NTPlay", style = TextStyle(brush = Brush.horizontalGradient(listOf(Cyan, Purple)), fontSize = 19.sp, fontWeight = FontWeight.ExtraBold))
        Spacer(Modifier.width(14.dp))
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
            for (t in TABS) {
                val sel = st.tab == t
                Text(
                    t, color = if (sel) Pink else Color(0xDDFFFFFF), fontSize = 11.sp, fontWeight = if (sel) FontWeight.ExtraBold else FontWeight.Medium, maxLines = 1,
                    modifier = Modifier.tvFocus(RoundedCornerShape(8.dp)).clickable { st.selectTab(t) }.padding(horizontal = 8.dp, vertical = 8.dp)
                )
            }
        }
        Text("Buscar", color = Color.White, fontSize = 12.sp, modifier = Modifier.tvFocus(RoundedCornerShape(8.dp)).background(Color(0x22FFFFFF), RoundedCornerShape(8.dp)).clickable { st.showSearch = true }.padding(horizontal = 12.dp, vertical = 8.dp))
        Spacer(Modifier.width(8.dp))
        Text("☰ Listas M3U", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.tvFocus(RoundedCornerShape(8.dp)).background(Color(0xFFFF5A00), RoundedCornerShape(8.dp)).clickable { st.showLists = true }.padding(horizontal = 12.dp, vertical = 8.dp))
        Spacer(Modifier.width(10.dp))
        Text(if (st.busy) "⏳ " + st.status else st.status, color = Color(0x99FFFFFF), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(150.dp))
        Text(clock, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

// ---------------------------------------------------------------- cartões
@Composable
fun PosterCard(st: AppState, item: Item, w: Dp, prog: Float = 0f, onRemove: (() -> Unit)? = null, onClick: () -> Unit) {
    val wide = item.kind == 'l'
    val h = if (wide) w * 0.58f else w * 1.5f
    var img by remember(item.key) { mutableStateOf(item.logo.ifEmpty { st.store.info(item.key)?.img ?: "" }) }
    var tried by remember(item.key) { mutableStateOf(false) }
    LaunchedEffect(item.key, img) {
        if (img.isEmpty() && !tried && !wide && !item.adult) {
            tried = true
            val i = Meta.get(st.store, item)
            if (i != null && i.img.isNotEmpty()) img = i.img
        }
    }
    Column(Modifier.width(w)) {
        Box(
            Modifier.tvFocus().clip(RoundedCornerShape(10.dp)).background(if (wide) CardC else tint(item.title)).height(h).fillMaxWidth().clickable(onClick = onClick)
        ) {
            if (img.isNotEmpty()) {
                AsyncImage(
                    model = img, contentDescription = null, contentScale = if (wide) ContentScale.Fit else ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().padding(if (wide) 10.dp else 0.dp),
                    onError = { if (!tried && !wide) { img = "" } else if (img == item.logo) img = "" }
                )
            } else {
                Text(item.title.take(1).uppercase(), Modifier.align(Alignment.Center), color = Color(0x55FFFFFF), fontSize = 30.sp, fontWeight = FontWeight.Black)
            }
            if (wide) Text("AO VIVO", Modifier.align(Alignment.TopStart).padding(6.dp).background(Color(0xCC000000), RoundedCornerShape(4.dp)).padding(horizontal = 5.dp, vertical = 1.dp), color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
            if (!wide) Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(h * 0.45f).background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xEE000000)))))
            if (!wide) Text(item.title, Modifier.align(Alignment.BottomStart).padding(horizontal = 6.dp, vertical = 6.dp), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (st.favs.contains(item.key)) Text("★", Modifier.align(Alignment.TopEnd).padding(6.dp), color = Color(0xFFFFC107), fontSize = 14.sp)
            if (prog > 0f) Box(Modifier.align(Alignment.BottomStart).fillMaxWidth(prog.coerceIn(0f, 1f)).height(3.dp).background(Pink))
        }
        if (wide) Text(item.title, Modifier.padding(top = 4.dp, start = 2.dp), color = Color.White, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (onRemove != null) Text("✕ Remover", Modifier.padding(top = 4.dp).tvFocus(RoundedCornerShape(6.dp)).clickable(onClick = onRemove).padding(horizontal = 6.dp, vertical = 3.dp), color = Color(0xCCFFFFFF), fontSize = 9.sp)
    }
}

@Composable
fun ShelfRow(title: String, count: Int, onAll: (() -> Unit)?, content: LazyListScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title.uppercase(), color = Pink, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.width(8.dp))
            Text("$count", color = Color(0x88FFFFFF), fontSize = 10.sp)
            Spacer(Modifier.weight(1f))
            if (onAll != null) Text("Ver tudo ›", color = Color(0xCCFFFFFF), fontSize = 11.sp, modifier = Modifier.tvFocus(RoundedCornerShape(6.dp)).clickable(onClick = onAll).padding(horizontal = 8.dp, vertical = 4.dp))
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

// ---------------------------------------------------------------- navegação / listas
@Composable
fun Browse(st: AppState) {
    val lib = st.lib
    if (lib == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (st.busy) st.status else "Nenhuma lista carregada.", color = Color.White, fontSize = 16.sp)
                Spacer(Modifier.height(14.dp))
                if (!st.busy) Text("Carregar lista M3U", Modifier.tvFocus(RoundedCornerShape(10.dp)).background(Color(0xFFFF5A00), RoundedCornerShape(10.dp)).clickable { st.showLists = true }.padding(horizontal = 22.dp, vertical = 12.dp), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
        return
    }
    val tab = st.tab
    val groups = when (tab) { "TV AO VIVO" -> lib.liveGroups; "FILMES" -> lib.movieGroups; "SÉRIES" -> lib.serieGroups; else -> null }
    Row(Modifier.fillMaxSize()) {
        if (groups != null) Sidebar(st, groups, when (tab) { "TV AO VIVO" -> lib.live.size; "FILMES" -> lib.movies.size; else -> lib.series.size }, tab)
        Box(Modifier.weight(1f).fillMaxHeight()) {
            if (groups != null) {
                if (st.group.isEmpty()) {
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 8.dp, bottom = 20.dp)) {
                        items(groups.take(60), key = { it.first }) { g ->
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
                "INFANTIL" -> GridOf(st, lib.kids.take(400), "Infantil")
                "ANIMES" -> GridOf(st, lib.anime.take(400), "Animes")
                "FAVORITOS" -> GridOf(st, lib.every.filter { st.favs.contains(it.key) }, "Favoritos")
                else -> Recom(st, lib)
            }
        }
    }
}

@Composable
fun CardFor(st: AppState, item: Item, queue: List<Item>) {
    PosterCard(st, item, if (item.kind == 'l') 150.dp else 104.dp) {
        if (item.kind == 'l') st.playItem(item, null, queue) else { st.queue = queue; st.openDetail(item) }
    }
}

@Composable
fun GridOf(st: AppState, l: List<Item>, title: String) {
    if (l.isEmpty()) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Nada por aqui ainda.", color = Color(0xAAFFFFFF)) }; return }
    val live = l.firstOrNull()?.kind == 'l'
    Column(Modifier.fillMaxSize()) {
        Text(title.uppercase() + "  ·  ${l.size}", Modifier.padding(16.dp, 10.dp, 16.dp, 4.dp), color = Pink, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
        LazyVerticalGrid(
            columns = GridCells.Adaptive(if (live) 150.dp else 104.dp), modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(l, key = { it.key }) { it2 -> CardFor(st, it2, l) }
        }
    }
}

@Composable
fun Sidebar(st: AppState, groups: List<Pair<String, List<Item>>>, total: Int, tab: String) {
    Column(Modifier.width(210.dp).fillMaxHeight().background(PanelC)) {
        Text((if (tab == "TV AO VIVO") "TV AO VIVO" else tab) + " › CATEGORIAS", Modifier.padding(14.dp, 14.dp, 14.dp, 8.dp), color = Color(0xCCFFFFFF), fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)) {
            item { CatBtn("Todas", total, st.group.isEmpty()) { st.group = "" } }
            items(groups, key = { it.first }) { g -> CatBtn(g.first, g.second.size, st.group == g.first) { st.group = g.first } }
        }
    }
}

@Composable
fun CatBtn(name: String, n: Int, sel: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 2.dp).tvFocus(RoundedCornerShape(10.dp)).clip(RoundedCornerShape(10.dp))
            .background(if (sel) Pink else Color.Transparent).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(name, Modifier.weight(1f), color = if (sel) Color.White else Color(0xB3FFFFFF), fontSize = 12.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("$n", color = Color(0xAAFFFFFF), fontSize = 9.sp, modifier = Modifier.background(Color(0x33000000), RoundedCornerShape(99.dp)).padding(horizontal = 6.dp, vertical = 1.dp))
    }
}

@Composable
fun Recom(st: AppState, lib: Library) {
    val ver = st.progVer
    val cw = remember(ver, lib) { st.continueList() }
    val favs = remember(st.favs, lib) { lib.every.filter { st.favs.contains(it.key) }.take(30) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 10.dp, bottom = 20.dp)) {
        if (cw.isNotEmpty()) item {
            ShelfRow("Continuar assistindo", cw.size, null) {
                items(cw, key = { it.first.key }) { (it2, ep) ->
                    val p = st.progOf(ep)
                    PosterCard(st, it2, if (it2.kind == 'l') 150.dp else 104.dp, prog = if (p != null && p.d > 0) p.t.toFloat() / p.d else 0f, onRemove = { st.removeProg(it2.key) }) {
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

// ---------------------------------------------------------------- detalhes
@Composable
fun DetailScreen(st: AppState, item: Item) {
    var info by remember(item.key) { mutableStateOf(st.store.info(item.key)) }
    LaunchedEffect(item.key) { val i = Meta.get(st.store, item); if (i != null) info = i }
    val poster = item.logo.ifEmpty { info?.img ?: "" }
    @Suppress("UNUSED_VARIABLE") val ev = st.epsVer
    val loadingEps = st.needsEps(item)
    val cont = st.startEp(item)
    val pr = st.resumeOf(cont)
    val label = if (item.kind == 's') (if (st.progOf(cont) != null) "▶ Continuar T${cont.s} E${cont.e}" else "▶ Assistir T1 E1") else if (pr > 0) "▶ Continuar de " + Util.fmt(pr) else "▶ Assistir"
    Row(Modifier.fillMaxSize().padding(20.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        Box(Modifier.width(150.dp).height(225.dp).clip(RoundedCornerShape(12.dp)).background(tint(item.title))) {
            if (poster.isNotEmpty()) AsyncImage(poster, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else Text(item.title.take(1).uppercase(), Modifier.align(Alignment.Center), color = Color(0x66FFFFFF), fontSize = 40.sp, fontWeight = FontWeight.Black)
        }
        Column(Modifier.weight(1f)) {
            Text(item.title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val meta = listOfNotNull(info?.y?.ifEmpty { null }, info?.r?.takeIf { it > 0 }?.let { "★ $it" }, item.group, if (item.kind == 's') "${item.ns} temp." else null).joinToString("  •  ")
            Text(meta, color = Color(0xBBFFFFFF), fontSize = 11.sp, modifier = Modifier.padding(vertical = 6.dp))
            val ov = info?.ov ?: ""
            Text(if (ov.isNotEmpty()) ov else if (info == null) "Buscando sinopse…" else "Sinopse não encontrada.", color = Color(0xDDFFFFFF), fontSize = 12.sp, maxLines = 6, overflow = TextOverflow.Ellipsis, lineHeight = 17.sp)
            Row(Modifier.padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BigBtn(if (loadingEps) "⏳ Carregando episódios…" else label, Pink) { if (!loadingEps) st.playItem(item, cont) }
                BigBtn(if (st.favs.contains(item.key)) "★ Na minha lista" else "☆ Minha lista", Color(0x33FFFFFF)) { st.toggleFav(item.key) }
                BigBtn("‹ Voltar", Color(0x33FFFFFF)) { st.detail = null }
            }
            if (item.kind == 's') {
                val seasons = if (loadingEps) emptyList() else item.eps.map { it.s }.distinct().sorted()
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
                    items(seasons) { s ->
                        Text("Temporada $s", Modifier.tvFocus(RoundedCornerShape(99.dp)).background(if (st.season == s) Pink else Color(0x22FFFFFF), RoundedCornerShape(99.dp)).clickable { st.season = s }.padding(horizontal = 12.dp, vertical = 6.dp), color = Color.White, fontSize = 11.sp)
                    }
                }
                val eps = if (loadingEps) emptyList() else item.eps.filter { it.s == st.season }
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(eps, key = { it.url }) { e ->
                        val p = st.progOf(e)
                        Row(Modifier.fillMaxWidth().tvFocus(RoundedCornerShape(8.dp)).clip(RoundedCornerShape(8.dp)).background(Color(0x14FFFFFF)).clickable { st.playItem(item, e) }.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("${e.e}", Modifier.width(30.dp), color = Pink, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text(e.name, Modifier.weight(1f), color = Color.White, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(if (p?.done == true) "✓" else if (p != null && p.t > 0) Util.fmt(p.t) else "▶", color = Color(0xCCFFFFFF), fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BigBtn(label: String, bg: Color, onClick: () -> Unit) {
    Text(label, Modifier.tvFocus(RoundedCornerShape(10.dp)).background(bg, RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
}

// ---------------------------------------------------------------- busca
@Composable
fun SearchDialog(st: AppState) {
    var q by remember { mutableStateOf("") }
    var res by remember { mutableStateOf<List<Item>>(emptyList()) }
    LaunchedEffect(q) { delay(300); res = st.search(q) }
    Dialog(onDismissRequest = { st.showSearch = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth(0.92f).fillMaxHeight(0.92f), shape = RoundedCornerShape(16.dp), color = PanelC) {
            Column(Modifier.padding(16.dp)) {
                OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth(), placeholder = { Text("Buscar canais, filmes e séries…") }, singleLine = true)
                Spacer(Modifier.height(8.dp))
                if (q.length < 2) Text("Digite pelo menos 2 letras.", color = Color(0x99FFFFFF), fontSize = 12.sp)
                else if (res.isEmpty()) Text("Nada encontrado.", color = Color(0x99FFFFFF), fontSize = 12.sp)
                LazyVerticalGrid(columns = GridCells.Adaptive(104.dp), contentPadding = PaddingValues(4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(res, key = { it.key }) { it2 ->
                        PosterCard(st, it2, if (it2.kind == 'l') 150.dp else 104.dp) {
                            st.showSearch = false
                            if (it2.kind == 'l') st.playItem(it2, null, res) else { st.queue = res; st.openDetail(it2) }
                        }
                    }
                }
            }
        }
    }
}

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
        Surface(Modifier.fillMaxWidth(0.88f).fillMaxHeight(0.84f), shape = RoundedCornerShape(16.dp), color = PanelC) {
            Row(Modifier.padding(18.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                // coluna esquerda: QR sempre visivel
                Column(Modifier.width(210.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Listas M3U", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                    Spacer(Modifier.height(8.dp))
                    Text("ENVIAR PELO CELULAR", color = Color(0xAAFFFFFF), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Image(qr, "QR code", Modifier.size(170.dp).background(Color.White).padding(6.dp))
                    Text("Escaneie com a câmera do celular, cole o link da lista e envie. Ela chega aqui sozinha.", color = Color(0xDDFFFFFF), fontSize = 10.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
                    Text("⏳ Aguardando o celular…", color = Cyan, fontSize = 10.sp, modifier = Modifier.padding(top = 6.dp))
                }
                // coluna direita: listas, adicionar e preferencias (rola)
                LazyColumn(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(end = 4.dp)) {
                    item { Text("MINHAS LISTAS", color = Color(0xAAFFFFFF), fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                    if (lists.isEmpty()) item { Text("Nenhuma lista ainda.", color = Color(0x99FFFFFF), fontSize = 12.sp) }
                    items(lists, key = { it.id }) { m ->
                        Row(Modifier.fillMaxWidth().background(Color(0x14FFFFFF), RoundedCornerShape(10.dp)).padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(if (m.active) "☑ Usar" else "☐ Usar", Modifier.tvFocus(RoundedCornerShape(6.dp)).clickable { st.toggleList(m.id) }.padding(6.dp), color = Color.White, fontSize = 12.sp)
                            Column(Modifier.weight(1f)) {
                                Text(m.name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${m.count} itens", color = Color(0x88FFFFFF), fontSize = 10.sp)
                            }
                            Text("↻ Atualizar", Modifier.tvFocus(RoundedCornerShape(6.dp)).clickable { st.refreshList(m) }.padding(6.dp), color = Color.White, fontSize = 11.sp)
                            Text("🗑 Excluir", Modifier.tvFocus(RoundedCornerShape(6.dp)).clickable { st.deleteList(m.id) }.padding(6.dp), color = Color(0xFFFF8A8A), fontSize = 11.sp)
                        }
                    }
                    item { Text("ADICIONAR NOVA LISTA", color = Color(0xAAFFFFFF), fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                    item { OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), placeholder = { Text("Nome da lista (opcional)", fontSize = 12.sp) }, singleLine = true, textStyle = TextStyle(fontSize = 13.sp)) }
                    item { OutlinedTextField(url, { url = it }, Modifier.fillMaxWidth(), placeholder = { Text("Link M3U  http://...", fontSize = 12.sp) }, singleLine = true, textStyle = TextStyle(fontSize = 13.sp)) }
                    item { BigBtn(if (st.busy) "⏳ Baixando…" else "+ Adicionar lista", Color(0xFFFF5A00)) { if (!st.busy) st.addUrl(url, name) } }
                    if (st.busy) item { Text(st.status, color = Cyan, fontSize = 11.sp) }
                    if (st.lastErr.isNotEmpty()) item { Text(st.lastErr, color = Color(0xFFFF8A8A), fontSize = 11.sp, lineHeight = 15.sp) }
                    item { Text("PREFERÊNCIAS", color = Color(0xAAFFFFFF), fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                    item { Text((if (st.adult) "☑" else "☐") + " Mostrar conteúdo adulto (fica no fim da lista)", Modifier.tvFocus(RoundedCornerShape(6.dp)).clickable { st.changeAdult(!st.adult) }.padding(8.dp), color = Color.White, fontSize = 12.sp) }
                    item { Text((if (st.autoNext) "☑" else "☐") + " Próximo episódio automático", Modifier.tvFocus(RoundedCornerShape(6.dp)).clickable { st.changeAutoNext(!st.autoNext) }.padding(8.dp), color = Color.White, fontSize = 12.sp) }
                    item { BigBtn("Fechar", Color(0x33FFFFFF)) { st.showLists = false } }
                }
            }
        }
    }
}

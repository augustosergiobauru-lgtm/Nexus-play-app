package app.opentv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val LiveGold = Color(0xFFE5C465)
private val LivePanel = Color(0xFF141414)

/** Three fixed panes, with scrolling confined to categories and channel results. */
@Composable
internal fun NexusLiveScreen(
    categories: List<NexusCatalog.Category>, categoryId: String?,
    onCategory: (String?) -> Unit, query: String, onQuery: (String) -> Unit,
    channels: List<NexusCatalog.Item>, favorites: Set<String>, onFavorite: (NexusCatalog.Item) -> Unit,
    userId: String, session: NexusAuth.Session, catalog: NexusCatalog, adultUnlocked: Boolean, selectedId: String?, title: String?, busy: Boolean,
    error: String?, loading: Boolean, more: Boolean, onMore: () -> Unit,
    onPlay: (NexusCatalog.Item) -> Unit, onBack: () -> Unit,
    sortOrder: String, onSort: (String) -> Unit, onParental: () -> Unit,
    expanded: Boolean, onExpand: () -> Unit, video: @Composable () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val history = remember(userId) { NexusWatchHistory(context, userId) }
    var recentIds by remember(userId) { mutableStateOf(emptyList<String>()) }
    var recentChannels by remember(userId) { mutableStateOf(emptyList<NexusCatalog.Item>()) }
    var recentLoading by remember(userId) { mutableStateOf(false) }
    var recentError by remember(userId) { mutableStateOf<String?>(null) }
    var filter by remember { mutableStateOf("all") }
    var grid by remember { mutableStateOf(true) }
    var sortMenu by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var clock by remember { mutableStateOf("") }
    LaunchedEffect(userId, selectedId) {
        while (true) {
            recentIds = history.recent().map { it.id }
            clock = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
            kotlinx.coroutines.delay(1000)
        }
    }
    DisposableEffect(session, recentIds, filter) {
        val alive = java.util.concurrent.atomic.AtomicBoolean(true)
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        var call: okhttp3.Call? = null
        recentError = null
        recentLoading = false
        if (filter == "recent") {
            recentChannels = emptyList()
            if (recentIds.isNotEmpty()) {
                recentLoading = true
                // Fetch history by identity, independent of the current catalog page or category.
                call = catalog.page(session, contentType = "live", ids = recentIds, pageSize = 100) { result ->
                    handler.post {
                        if (alive.get()) {
                            recentLoading = false
                            result.fold(onSuccess = { recentChannels = it },
                                onFailure = { recentError = "Não foi possível carregar os assistidos recentemente." })
                        }
                    }
                }
            }
        }
        onDispose { alive.set(false); call?.cancel() }
    }
    BackHandler { if (expanded) onExpand() else onBack() }
    MaterialTheme(colorScheme = darkColorScheme(primary = LiveGold, onPrimary = Color.Black, surface = LivePanel, background = Color.Black)) {
        if (expanded) {
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                video()
                IconButton(onClick = onExpand, modifier = Modifier.align(Alignment.TopStart)) {
                    Icon(Icons.Default.ArrowBack, "Voltar ao mini player", tint = LiveGold)
                }
                IconButton(onClick = onExpand, modifier = Modifier.align(Alignment.TopEnd)) {
                    Icon(Icons.Default.FullscreenExit, "Sair da tela cheia", tint = LiveGold)
                }
            }
        } else Column(Modifier.fillMaxSize().background(Color(0xFF080808)).safeDrawingPadding()) {
            Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                NexusBrandMark(Modifier.size(40.dp))
                Text("TV AO VIVO", Modifier.padding(horizontal = 12.dp), color = LiveGold, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text("/  " + when (filter) {
                    "favorites" -> "Favoritos"
                    "recent" -> "Assistidos recentemente"
                    else -> categories.firstOrNull { it.id == categoryId }?.name ?: "Todos os canais"
                }, Modifier.weight(1f), color = Color.White, fontSize = 20.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Voltar", tint = Color.White) }
            }
            Row(Modifier.fillMaxWidth().weight(1f)) {
                LazyColumn(Modifier.weight(0.27f).fillMaxHeight().background(LivePanel).padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    NexusSearchField(query, onQuery)
                    Box {
                        OutlinedButton(onClick = { sortMenu = true }, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(8.dp)) {
                            Icon(Icons.Default.Sort, null, Modifier.size(16.dp))
                            Text("Ordem: " + when (sortOrder) { "az" -> "A–Z"; "za" -> "Z–A"; else -> "Padrão da lista" }, fontSize = 12.sp)
                        }
                        DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                            listOf("default" to "Padrão da lista", "az" to "A–Z", "za" to "Z–A").forEach { (value, label) ->
                                DropdownMenuItem(text = { Text(label) }, onClick = { onSort(value); sortMenu = false })
                            }
                        }
                    }
                    TextButton(onClick = onParental) {
                        Icon(Icons.Default.Lock, null, Modifier.size(16.dp))
                        Text("Controle parental", fontSize = 11.sp)
                    }
                    }
                    }
                        item { LiveCategory("FAVORITOS", filter == "favorites") { filter = "favorites"; onCategory(null) } }
                        item { LiveCategory("ASSISTIDOS RECENTEMENTE", filter == "recent") { filter = "recent"; onCategory(null) } }
                        item { LiveCategory("TODOS OS CANAIS", filter == "all" && categoryId == null) { filter = "all"; onCategory(null) } }
                        items(categories, key = { it.id }) { category ->
                            LiveCategory(category.name, filter == "all" && categoryId == category.id) { filter = "all"; onCategory(category.id) }
                        }
                }
                Column(Modifier.weight(0.43f).fillMaxHeight().padding(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        IconToggleButton(checked = grid, onCheckedChange = { grid = true }, modifier = Modifier.background(if (grid) Color(0xFF3B3019) else LivePanel, RoundedCornerShape(8.dp))) {
                            Icon(Icons.Default.GridView, "Exibir em grade", tint = if (grid) LiveGold else Color.LightGray)
                        }
                        IconToggleButton(checked = !grid, onCheckedChange = { grid = false }, modifier = Modifier.background(if (!grid) Color(0xFF3B3019) else LivePanel, RoundedCornerShape(8.dp))) {
                            Icon(Icons.Default.ViewList, "Exibir em lista", tint = if (!grid) LiveGold else Color.LightGray)
                        }
                        TextButton(onClick = { editing = !editing }, modifier = Modifier.weight(1f).background(LivePanel, RoundedCornerShape(8.dp))) {
                            Text(if (editing) "Concluir" else "Editar favoritos", color = if (editing) LiveGold else Color.White, fontSize = 12.sp)
                        }
                    }
                    val source = if (filter == "recent") recentChannels.filter { item ->
                        (adultUnlocked || !item.adult) && item.title.contains(query.trim(), ignoreCase = true)
                    } else channels
                    val visible = source.filter { when (filter) { "favorites" -> it.id in favorites; "recent" -> it.id in recentIds; else -> true } }
                        .let { list -> if (filter == "recent") list.sortedBy { recentIds.indexOf(it.id) } else list }
                    val resultsLoading = if (filter == "recent") recentLoading else loading
                    if (resultsLoading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = LiveGold)
                    if (grid) {
                        LazyVerticalGrid(columns = GridCells.Fixed(4), modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(visible, key = { it.id }) { channel ->
                                LiveChannel(channel, channel.id == selectedId, channel.id in favorites, false,
                                    { if (editing) onFavorite(channel) else onPlay(channel) }, { onFavorite(channel) })
                            }
                            item { if (filter != "recent" && more && !loading) TextButton(onClick = onMore) { Text("Mais", fontSize = 11.sp) } }
                        }
                    } else LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(visible, key = { it.id }) { channel ->
                            LiveChannel(channel, channel.id == selectedId, channel.id in favorites, true,
                                { if (editing) onFavorite(channel) else onPlay(channel) }, { onFavorite(channel) })
                        }
                        item { if (filter != "recent" && more && !loading) TextButton(onClick = onMore) { Text("Carregar mais") } }
                    }
                    if (filter == "recent" && recentError != null) Text(recentError!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    if (visible.isEmpty() && !resultsLoading && recentError == null) Text("Nenhum canal encontrado.", color = Color.LightGray, fontSize = 12.sp)
                }
                Column(Modifier.weight(0.30f).fillMaxHeight().background(Color(0xFF0C0C0C)).padding(10.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(clock, color = Color.White, fontSize = 14.sp, modifier = Modifier.padding(top = 12.dp))
                    Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp)).background(Color.Black), contentAlignment = Alignment.Center) {
                        video()
                        if (title == null) Text("Selecione um canal", color = Color.Gray, fontSize = 12.sp)
                        if (title != null) IconButton(onClick = onExpand, modifier = Modifier.align(Alignment.BottomEnd).size(32.dp)) { Icon(Icons.Default.Fullscreen, "Tela cheia", tint = Color.White, modifier = Modifier.size(18.dp)) }
                        if (busy) CircularProgressIndicator(Modifier.size(24.dp), color = LiveGold)
                    }
                    title?.let { Text(it, color = LiveGold, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
                }
            }
        }
    }
}

@Composable
private fun LiveCategory(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(label, Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(if (selected) Color(0xFF332A16) else Color.Transparent)
        .clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 12.dp), color = if (selected) LiveGold else Color.LightGray,
        fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

@Composable
private fun LiveChannel(item: NexusCatalog.Item, selected: Boolean, favorite: Boolean, list: Boolean, onPlay: () -> Unit, onFavorite: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    val modifier = Modifier.fillMaxWidth().clip(shape).background(if (selected) Color(0xFF332A16) else LivePanel)
        .border(1.dp, if (selected) LiveGold else Color(0xFF35312A), shape).clickable(onClick = onPlay)
    if (list) Row(modifier.padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
        LiveLogo(item, Modifier.size(44.dp))
        Text(item.title, Modifier.weight(1f).padding(8.dp), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp)
        LiveFavorite(favorite, onFavorite)
    } else Column(modifier) {
        Box(Modifier.fillMaxWidth().aspectRatio(1.1f).background(Color(0xFF24211A))) {
            LiveLogo(item, Modifier.fillMaxSize().padding(8.dp))
            Box(Modifier.align(Alignment.TopEnd)) { LiveFavorite(favorite, onFavorite) }
        }
        Text(item.title, Modifier.fillMaxWidth().height(44.dp).padding(6.dp), color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 11.sp)
    }
}

@Composable
private fun LiveFavorite(favorite: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(32.dp)) {
        Icon(if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, if (favorite) "Remover favorito" else "Favoritar", tint = if (favorite) LiveGold else Color.LightGray, modifier = Modifier.size(19.dp))
    }
}

@Composable
private fun LiveLogo(item: NexusCatalog.Item, modifier: Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        if (item.logoUrl.isNullOrBlank()) Icon(Icons.Default.LiveTv, null, tint = LiveGold)
        else coil.compose.AsyncImage(model = item.logoUrl, contentDescription = item.title, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
    }
}

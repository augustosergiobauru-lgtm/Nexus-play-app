package app.opentv

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** V1 catalog and playback; each item is authorized by the server before it opens. */
@Composable
fun NexusHomeScreen(session: NexusAuth.Session, onExit: () -> Unit) {
    var demo by remember { mutableStateOf(false) }
    var hlsDemo by remember { mutableStateOf(false) }
    if (demo) {
        androidx.activity.compose.BackHandler { demo = false }
        NexusPlaybackScreen(
            session = session,
            item = NexusCatalog.Item(if (hlsDemo) "public-demo-hls" else "public-demo-bbb", "Big Buck Bunny — © Blender Foundation", "movie", null),
            authorizedUrl = if (hlsDemo) "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8" else "https://storage.googleapis.com/exoplayer-test-media-0/BigBuckBunny_320x180.mp4",
            onClose = { demo = false }
        )
        return
    }
    val gold = Color(0xFFE5C465)
    val tabs = listOf("Início", "Ao Vivo", "Filmes", "Séries", "Mais")
    var selected by remember { mutableStateOf(0) }
    val catalog = remember { NexusSupabase.catalog() }
    val handler = remember { android.os.Handler(android.os.Looper.getMainLooper()) }
    var items by remember(session) { mutableStateOf(emptyList<NexusCatalog.Item>()) }
    var liveCategories by remember { mutableStateOf(emptyList<NexusCatalog.Category>()) }
    var liveCategoryId by remember { mutableStateOf<String?>(null) }
    var categoryMenu by remember { mutableStateOf(false) }
    var categoryError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(session) {
        catalog.liveCategories(session) { result ->
            handler.post {
                result.fold(
                    onSuccess = { liveCategories = it; categoryError = null },
                    onFailure = { categoryError = "Não foi possível carregar as categorias de canais." }
                )
            }
        }
    }
    var query by remember { mutableStateOf("") }
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember(session.userId) { context.getSharedPreferences("nexus_favorites_" + session.userId, android.content.Context.MODE_PRIVATE) }
    var favorites by remember(session.userId) { mutableStateOf(prefs.getStringSet("ids", emptySet())?.toSet() ?: emptySet()) }
    var favoritesOnly by remember { mutableStateOf(false) }
    var recentOnly by remember { mutableStateOf(false) }
    val watchHistory = remember(session.userId) { NexusWatchHistory(context, session.userId) }
    var recentIds by remember(session.userId) { mutableStateOf(watchHistory.recent().map { it.id }.toSet()) }
    var seriesDetail by remember(session.userId) { mutableStateOf<NexusCatalog.Item?>(null) }

    val playbackAccess = remember { NexusSupabase.playbackAccess() }
    var playingItem by remember(session.userId) { mutableStateOf<NexusCatalog.Item?>(null) }
    var playbackUrl by remember(session.userId) { mutableStateOf<String?>(null) }
    var playbackBusy by remember { mutableStateOf(false) }
    var playbackError by remember { mutableStateOf<String?>(null) }
    var playbackCall by remember { mutableStateOf<okhttp3.Call?>(null) }

    var loading by remember { mutableStateOf(false) }
    var catalogGeneration by remember { mutableStateOf(0) }
    var more by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val active = remember { java.util.concurrent.atomic.AtomicBoolean(true) }
    DisposableEffect(Unit) {
        active.set(true)
        onDispose { active.set(false); playbackCall?.cancel() }
    }
    fun play(item: NexusCatalog.Item) {
        if (playbackBusy) return
        playbackBusy = true
        playbackError = null
        try {
            val installation = NexusInstallation.identifier(context)
            playbackCall = playbackAccess.resolve(session, item.id, installation) { result ->
                handler.post {
                    if (active.get()) {
                        playbackBusy = false
                        result.fold(onSuccess = { url ->
                            playbackUrl = url
                            playingItem = item
                        }, onFailure = {
                            playbackError = it.message ?: "Não foi possível abrir o conteúdo."
                        })
                    }
                }
            }
        } catch (_: Exception) {
            playbackBusy = false
            playbackError = "Não foi possível identificar ou autorizar este aparelho."
        }
    }
    fun loadPage() {
        if (loading || !more) return
        loading = true
        error = null
        val generation = catalogGeneration
        val contentType = when (selected) { 1 -> "live"; 2 -> "movie"; 3 -> "series"; else -> null }
        catalog.page(session, items.size, contentType = contentType, query = query, categoryId = if (selected == 1) liveCategoryId else null, itemIds = when { favoritesOnly -> favorites; recentOnly -> recentIds; else -> null }) { result ->
            handler.post {
                if (active.get()) {
                    if (generation != catalogGeneration) return@post
                    loading = false
                    result.fold(onSuccess = { page ->
                        items = items + page
                        more = page.size == 100
                    }, onFailure = { error = "Não foi possível carregar o catálogo." })
                }
            }
        }
    }
    LaunchedEffect(session, selected, query, liveCategoryId, favoritesOnly, recentOnly, if (favoritesOnly) favorites else emptySet<String>()) {
        catalogGeneration++
        items = emptyList()
        more = true
        loading = false
        error = null
        if (query.isNotBlank()) kotlinx.coroutines.delay(300)
        if (selected != 4) loadPage()
    }
    androidx.activity.compose.BackHandler(enabled = seriesDetail != null) { seriesDetail = null }
    val currentPlayback = playingItem
    val currentUrl = playbackUrl
    if (currentPlayback != null && currentUrl != null) {
        androidx.activity.compose.BackHandler { playingItem = null; playbackUrl = null; recentIds = watchHistory.recent().map { it.id }.toSet() }
        NexusPlaybackScreen(session, currentPlayback, currentUrl) {
            playingItem = null
            playbackUrl = null
            recentIds = watchHistory.recent().map { it.id }.toSet()
        }
        return
    }
    MaterialTheme(colorScheme = darkColorScheme(
        primary = gold, onPrimary = Color.Black,
        background = Color(0xFF080808), surface = Color(0xFF171717)
    )) {
        Scaffold(
            containerColor = Color(0xFF080808),
            bottomBar = {
                NavigationBar(containerColor = Color(0xFF101010)) {
                    tabs.forEachIndexed { index, name ->
                        NavigationBarItem(
                            selected = selected == index,
                            onClick = { selected = index; seriesDetail = null; favoritesOnly = false; recentOnly = false; query = "" },
                            icon = { Icon(listOf(Icons.Default.Home, Icons.Default.LiveTv, Icons.Default.Movie, Icons.Default.VideoLibrary, Icons.Default.MoreHoriz)[index], contentDescription = name) },
                            label = { Text(name, fontSize = 11.sp) },
                            colors = NavigationBarItemDefaults.colors(selectedIconColor = gold, selectedTextColor = gold, indicatorColor = Color(0xFF332A16), unselectedIconColor = Color.Gray, unselectedTextColor = Color.Gray)
                        )
                    }
                }
            }
        ) { padding ->
            Column(
                Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp, vertical = 6.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val detail = seriesDetail
                if (detail != null) {
                    TextButton(onClick = { seriesDetail = null }) { Text("‹ Voltar", color = gold) }
                    Box(Modifier.fillMaxWidth().height(220.dp).background(Brush.verticalGradient(listOf(Color(0xFF332A16), Color(0xFF101010))), RoundedCornerShape(14.dp)).padding(18.dp), contentAlignment = Alignment.BottomStart) {
                        Text(detail.title, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    }
                    Text("Série", color = gold)
                    OutlinedButton(onClick = {
                        favorites = if (detail.id in favorites) favorites - detail.id else favorites + detail.id
                        prefs.edit().putStringSet("ids", favorites.toSet()).apply()
                    }) { Text(if (detail.id in favorites) "★ Na minha lista" else "☆ Minha lista") }
                    Text("Episódios", color = gold, fontWeight = FontWeight.Bold)
                    Text("Temporadas e episódios ainda não disponíveis no catálogo.", color = Color.LightGray)
                    Text("Sobre", color = gold, fontWeight = FontWeight.Bold)
                    Text("Sinopse não disponível.", color = Color.LightGray)
                    return@Column
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    NexusBrandMark(Modifier.size(28.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("NEXUS PLAY", color = gold, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    IconButton(onClick = { selected = 4 }) { Icon(Icons.Default.Settings, contentDescription = "Configurações", tint = gold) }
                }
                if (playbackBusy) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = gold)
                    Text("Autorizando reprodução...", color = Color.LightGray)
                }
                playbackError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (selected != 0) Text(tabs[selected], style = MaterialTheme.typography.headlineSmall)
                if (selected == 0) {
                    Text("O que vamos assistir?", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text("Seu entretenimento em um só lugar", color = Color.Gray, fontSize = 13.sp)
                    listOf(listOf(1, 2), listOf(3, 4)).forEach { shortcuts ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            shortcuts.forEach { shortcut ->
                                val label = when (shortcut) { 1 -> "Ao Vivo"; 2 -> "Filmes"; 3 -> "Séries"; else -> "Favoritos" }
                                val symbol = when (shortcut) { 1 -> Icons.Default.LiveTv; 2 -> Icons.Default.Movie; 3 -> Icons.Default.VideoLibrary; else -> Icons.Default.Star }
                                Column(Modifier.weight(1f).height(112.dp)
                                    .background(Brush.linearGradient(listOf(Color(0xFF302717), Color(0xFF151515))), RoundedCornerShape(16.dp))
                                    .clickable { selected = if (shortcut == 4) 1 else shortcut; favoritesOnly = shortcut == 4; recentOnly = false; query = "" }
                                    .padding(16.dp), verticalArrangement = Arrangement.SpaceBetween) {
                                    Icon(symbol, contentDescription = null, tint = gold, modifier = Modifier.size(30.dp))
                                    Text(label, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                                }
                            }
                        }
                    }
                }
                val sections = when (selected) {
                    0 -> listOf("Continuar assistindo", "Lançamentos em Filmes", "Ao vivo", "Séries")
                    1 -> listOf("Categorias", "Favoritos", "Recentes")
                    2 -> listOf("Categorias de filmes", "Favoritos")
                    3 -> listOf("Categorias de séries", "Favoritos")
                    else -> listOf("Minha conta", "Dispositivos")
                }
                if (selected in 1..3) {
                    OutlinedTextField(value = query, onValueChange = { query = it },
                        placeholder = { Text("Buscar no catálogo") }, leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }, singleLine = true, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp))
                    if (selected == 1) {
                        Box {
                            OutlinedButton(onClick = { categoryMenu = true }, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(liveCategories.firstOrNull { it.id == liveCategoryId }?.name ?: "Todas as categorias", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            DropdownMenu(expanded = categoryMenu, onDismissRequest = { categoryMenu = false },
                                modifier = Modifier.heightIn(max = 420.dp)) {
                                DropdownMenuItem(text = { Text("Todas as categorias") },
                                    onClick = { liveCategoryId = null; categoryMenu = false })
                                liveCategories.forEach { category ->
                                    DropdownMenuItem(text = { Text(category.name) },
                                        onClick = { liveCategoryId = category.id; categoryMenu = false })
                                }
                            }
                        }
                        categoryError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    }
                    val accepted = when (selected) {
                        1 -> setOf("live", "channel", "tv")
                        2 -> setOf("movie", "film", "vod")
                        else -> setOf("series", "episode")
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = !favoritesOnly && !recentOnly, onClick = { favoritesOnly = false; recentOnly = false }, label = { Text("Todos") }, colors = nexusChipColors(gold))
                        FilterChip(selected = favoritesOnly, onClick = { favoritesOnly = true; recentOnly = false }, label = { Text("Favoritos") }, leadingIcon = { Icon(Icons.Default.Star, null, Modifier.size(16.dp)) }, colors = nexusChipColors(gold))
                        FilterChip(selected = recentOnly, onClick = { recentOnly = true; favoritesOnly = false }, label = { Text("Recentes") }, colors = nexusChipColors(gold))
                    }
                    if (selected == 1) {
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            liveCategories.forEach { category ->
                                FilterChip(selected = liveCategoryId == category.id,
                                    onClick = { liveCategoryId = if (liveCategoryId == category.id) null else category.id },
                                    label = { Text(category.name, maxLines = 1) }, colors = nexusChipColors(gold))
                            }
                        }
                    }
                    val visible = items.filter { (!favoritesOnly || it.id in favorites) && it.type.lowercase() in accepted && it.title.contains(query, ignoreCase = true) }
                    visible.forEach { item ->
                        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF151515))) {
                            Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).clickable(enabled = !playbackBusy) { play(item) }.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                Box(Modifier.size(36.dp).background(Color(0xFF302717), RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                                    if (!item.logoUrl.isNullOrBlank()) coil.compose.AsyncImage(model = item.logoUrl, contentDescription = null, modifier = Modifier.size(32.dp))
                                    else Icon(if (selected == 1) Icons.Default.LiveTv else Icons.Default.PlayArrow, contentDescription = null, tint = gold, modifier = Modifier.size(22.dp))
                                }
                                Text(item.title, Modifier.weight(1f).padding(horizontal = 10.dp), color = Color.White, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                IconButton(onClick = {
                                    favorites = if (item.id in favorites) favorites - item.id else favorites + item.id
                                    prefs.edit().putStringSet("ids", favorites.toSet()).apply()
                                }) { Icon(if (item.id in favorites) Icons.Default.Star else Icons.Default.StarBorder, contentDescription = if (item.id in favorites) "Remover dos favoritos" else "Adicionar aos favoritos", tint = gold, modifier = Modifier.size(22.dp)) }
                            }
                        }
                    }
                    if (visible.isEmpty() && !loading) Text("Nenhum conteúdo encontrado. Tente outra categoria ou busca.")
                    if (loading) CircularProgressIndicator()
                    error?.let { Text(it) }
                    if (more && !loading) Button(onClick = { loadPage() }) { Text("Carregar mais") }
                 } else if (selected == 0) {
                    sections.forEach { title ->
                        Text(title, style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
                        val types = when(title) {
                            "Ao vivo" -> setOf("live", "channel", "tv")
                            "Lançamentos em Filmes" -> setOf("movie", "film", "vod")
                            "Séries" -> setOf("series", "episode")
                            else -> emptySet()
                        }
                        val entries = items.filter { it.type.lowercase() in types }.take(10)
                        if (entries.isEmpty()) Text(if (types.isEmpty()) "Nenhuma reprodução recente" else "Conteúdo em breve", color = Color.Gray, fontSize = 13.sp)
                        else Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            entries.forEach { entry ->
                                Box(Modifier.width(116.dp).height(146.dp).clickable(enabled = !playbackBusy) { play(entry) }.background(Color(0xFF1C1A16), RoundedCornerShape(10.dp)).padding(10.dp), contentAlignment = Alignment.BottomStart) {
                                    Text(entry.title, color = Color.White, maxLines = 3)
                                }
                            }
                        }
                    }
                    if (loading) CircularProgressIndicator()
                    error?.let { Text(it) }
                } else {
                    Text("Minha conta", color = gold)
                    Text("Dispositivo autorizado", color = Color.LightGray)
                    HorizontalDivider()
                    Text("Diagnóstico do player", color = gold)
                    OutlinedButton(onClick = { hlsDemo = false; demo = true }) { Text("Testar vídeo MP4") }
                    OutlinedButton(onClick = { hlsDemo = true; demo = true }) { Text("Testar vídeo HLS") }
                    TextButton(onClick = onExit) { Text("Sair") }
                }
            }
        }
    }
}

@Composable
private fun nexusChipColors(gold: Color) = FilterChipDefaults.filterChipColors(
    selectedContainerColor = Color(0xFF332A16), selectedLabelColor = gold,
    selectedLeadingIconColor = gold, labelColor = Color.LightGray
)

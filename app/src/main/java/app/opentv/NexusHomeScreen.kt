package app.opentv

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

/** Empty-state UI preview; no catalog entitlement or playback is implied. */
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
    var query by remember { mutableStateOf("") }
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember(session.userId) { context.getSharedPreferences("nexus_favorites_" + session.userId, android.content.Context.MODE_PRIVATE) }
    var favorites by remember(session.userId) { mutableStateOf(prefs.getStringSet("ids", emptySet())?.toSet() ?: emptySet()) }
    var favoritesOnly by remember { mutableStateOf(false) }
    var seriesDetail by remember(session.userId) { mutableStateOf<NexusCatalog.Item?>(null) }

    var loading by remember { mutableStateOf(false) }
    var more by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val active = remember { java.util.concurrent.atomic.AtomicBoolean(true) }
    DisposableEffect(Unit) {
        active.set(true)
        onDispose { active.set(false) }
    }
    fun loadPage() {
        if (loading || !more) return
        loading = true
        error = null
        catalog.page(session, items.size) { result ->
            handler.post {
                if (active.get()) {
                    loading = false
                    result.fold(onSuccess = { page ->
                        items = items + page
                        more = page.size == 100
                    }, onFailure = { error = "Não foi possível carregar o catálogo." })
                }
            }
        }
    }
    LaunchedEffect(session) { loadPage() }
    androidx.activity.compose.BackHandler(enabled = seriesDetail != null) { seriesDetail = null }
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
                            onClick = { selected = index; seriesDetail = null; favoritesOnly = false; query = "" },
                            icon = { Text(listOf("⌂", "▣", "▶", "▤", "⋯")[index], fontSize = 20.sp) },
                            label = { Text(name, fontSize = 11.sp) },
                            colors = NavigationBarItemDefaults.colors(selectedIconColor = gold, selectedTextColor = gold, indicatorColor = Color(0xFF332A16), unselectedIconColor = Color.Gray, unselectedTextColor = Color.Gray)
                        )
                    }
                }
            }
        ) { padding ->
            Column(
                Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp, vertical = 12.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(18.dp)
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
                    NexusBrandMark(Modifier.size(36.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("NEXUS PLAY", color = gold, fontSize = 23.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    TextButton(onClick = { selected = 4 }) { Text("☰", color = gold, fontSize = 22.sp) }
                }
                if (selected != 0) Text(tabs[selected], style = MaterialTheme.typography.headlineSmall)
                if (selected == 0) {
                    Box(Modifier.fillMaxWidth().height(210.dp).background(Brush.verticalGradient(listOf(Color(0xFF302717), Color(0xFF101010))), RoundedCornerShape(16.dp)).padding(18.dp), contentAlignment = Alignment.BottomStart) {
                        Column { Text("DESTAQUES", color = gold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text(items.firstOrNull()?.title ?: "Seu entretenimento em um só lugar", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                            Text(if (items.isEmpty()) "Catálogo aguardando conteúdo" else "Explore o catálogo", color = Color.LightGray)
                        }
                    }
                }
                if (selected == 0) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Ao Vivo", "Filmes", "Séries", "Favoritos").forEachIndexed { index, label ->
                            Column(Modifier.weight(1f).background(Color(0xFF191919), RoundedCornerShape(12.dp))
                                .clickable { selected = if (index == 3) 1 else index + 1; favoritesOnly = index == 3; query = "" }
                                .padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(listOf("▣", "▶", "▤", "★")[index], color = gold, fontSize = 25.sp)
                                Spacer(Modifier.height(6.dp))
                                Text(label, color = Color.White, fontSize = 11.sp)
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
                        label = { Text("Pesquisar") }, singleLine = true, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth())
                    val accepted = when (selected) {
                        1 -> setOf("live", "channel", "tv")
                        2 -> setOf("movie", "film", "vod")
                        else -> setOf("series", "episode")
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = !favoritesOnly, onClick = { favoritesOnly = false }, label = { Text("Todos") })
                        FilterChip(selected = favoritesOnly, onClick = { favoritesOnly = true }, label = { Text("Favoritos") })
                    }
                    val visible = items.filter { (!favoritesOnly || it.id in favorites) && it.type.lowercase() in accepted && it.title.contains(query, ignoreCase = true) }
                    visible.forEach { item ->
                        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF151515))) {
                            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                Box(Modifier.size(44.dp).background(Color(0xFF302717), RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                                    Text(if (selected == 1) "▣" else "▶", color = gold, fontSize = 22.sp)
                                }
                                Text(item.title, Modifier.weight(1f).clickable(enabled = selected == 3) { seriesDetail = item }.padding(12.dp), color = Color.White)
                                TextButton(onClick = {
                                    favorites = if (item.id in favorites) favorites - item.id else favorites + item.id
                                    prefs.edit().putStringSet("ids", favorites.toSet()).apply()
                                }) { Text(if (item.id in favorites) "★" else "☆", color = gold, fontSize = 24.sp) }
                            }
                        }
                    }
                    if (visible.isEmpty() && !loading) Text("Nenhum título encontrado nesta página.")
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
                                Box(Modifier.width(116.dp).height(146.dp).background(Color(0xFF1C1A16), RoundedCornerShape(10.dp)).padding(10.dp), contentAlignment = Alignment.BottomStart) {
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

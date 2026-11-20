package app.opentv

import androidx.compose.foundation.background
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
            item = NexusCatalog.Item(if (hlsDemo) "public-demo-hls" else "public-demo-bbb", "Big Buck Bunny — demonstração", "movie", null),
            authorizedUrl = if (hlsDemo) "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8" else "https://storage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
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
    MaterialTheme(colorScheme = darkColorScheme(
        primary = gold, onPrimary = Color.Black,
        background = Color(0xFF080808), surface = Color(0xFF171717)
    )) {
        Scaffold(
            containerColor = Color(0xFF080808),
            bottomBar = {
                NavigationBar(containerColor = Color(0xFF171717)) {
                    tabs.forEachIndexed { index, name ->
                        NavigationBarItem(
                            selected = selected == index,
                            onClick = { selected = index },
                            icon = { Text(listOf("⌂", "▣", "▶", "▤", "⋯")[index], fontSize = 20.sp) },
                            label = { Text(name) }
                        )
                    }
                }
            }
        ) { padding ->
            Column(
                Modifier.fillMaxSize().padding(padding).padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Text("NEXUS PLAY", color = gold, fontSize = 28.sp)
                Text("Prévia de desenvolvimento", color = Color.LightGray)
                Text(tabs[selected], style = MaterialTheme.typography.headlineMedium)
                if (selected == 0) {
                    Button(onClick = { hlsDemo = false; demo = true }) { Text("Testar vídeo MP4") }
                    Button(onClick = { hlsDemo = true; demo = true }) { Text("Testar vídeo HLS") }
                }
                val sections = when (selected) {
                    0 -> listOf("Destaques", "Continuar assistindo", "Ao vivo", "Filmes", "Séries")
                    1 -> listOf("Categorias", "Favoritos", "Recentes")
                    2 -> listOf("Categorias de filmes", "Favoritos")
                    3 -> listOf("Categorias de séries", "Favoritos")
                    else -> listOf("Minha conta", "Dispositivos")
                }
                if (selected in 1..3) {
                    OutlinedTextField(value = query, onValueChange = { query = it },
                        label = { Text("Pesquisar títulos carregados") }, modifier = Modifier.fillMaxWidth())
                    val accepted = when (selected) {
                        1 -> setOf("live", "channel", "tv")
                        2 -> setOf("movie", "film", "vod")
                        else -> setOf("series", "episode")
                    }
                    Row { TextButton(onClick = { favoritesOnly = !favoritesOnly }) { Text(if (favoritesOnly) "Mostrar todos" else "Mostrar favoritos") } }
                    val visible = items.filter { (!favoritesOnly || it.id in favorites) && it.type.lowercase() in accepted && it.title.contains(query, ignoreCase = true) }
                    visible.forEach { item ->
                        Card(Modifier.fillMaxWidth()) {
                            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                Text(item.title, Modifier.weight(1f).padding(8.dp))
                                TextButton(onClick = {
                                    favorites = if (item.id in favorites) favorites - item.id else favorites + item.id
                                    prefs.edit().putStringSet("ids", favorites.toSet()).apply()
                                }) { Text(if (item.id in favorites) "★" else "☆") }
                            }
                        }
                    }
                    if (visible.isEmpty() && !loading) Text("Nenhum título encontrado nesta página.")
                    if (loading) CircularProgressIndicator()
                    error?.let { Text(it) }
                    if (more && !loading) Button(onClick = { loadPage() }) { Text("Carregar mais") }
                } else sections.forEach { title ->
                    Text(title, style = MaterialTheme.typography.titleLarge, color = gold)
                    Card(Modifier.fillMaxWidth()) {
                        Text("Nenhum conteúdo disponível nesta prévia.", Modifier.padding(18.dp))
                    }
                }
                if (selected == 4) Button(onClick = onExit) { Text("Sair") }
            }
        }
    }
}

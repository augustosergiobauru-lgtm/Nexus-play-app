package app.opentv

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
fun NexusHomeScreen(
    session: NexusAuth.Session,
    onExit: () -> Unit,
    catalog: NexusCatalog = remember { NexusSupabase.catalog() }
) {
    val gold = Color(0xFFE5C465)
    val tabs = listOf("Início", "Ao Vivo", "Filmes", "Séries", "Mais")
    var selected by remember { mutableStateOf(0) }
    val handler = remember { android.os.Handler(android.os.Looper.getMainLooper()) }
    var items by remember(session) { mutableStateOf(emptyList<NexusCatalog.Item>()) }
    var homeItems by remember(session.userId) { mutableStateOf(emptyList<NexusCatalog.Item>()) }
    var homeFailed by remember { mutableStateOf(false) }
    var homeLoading by remember { mutableStateOf(true) }
    var homeRetry by remember { mutableStateOf(0) }
    DisposableEffect(session, homeRetry) {
        homeFailed = false
        homeLoading = true
        val alive = java.util.concurrent.atomic.AtomicBoolean(true)
        var pending = 2
        listOf("movie", "series").forEach { type ->
            catalog.page(session, contentType = type, recentlyAdded = true, pageSize = 48, seriesShows = type == "series") { result ->
                handler.post {
                    if (alive.get()) {
                        result.onSuccess { page -> homeItems = homeItems.filterNot { it.type == type } + page }
                            .onFailure { homeFailed = true }
                        pending--
                        homeLoading = pending > 0
                    }
                }
            }
        }
        onDispose { alive.set(false) }
    }
    var liveCategories by remember { mutableStateOf(emptyList<NexusCatalog.Category>()) }
    var movieCategories by remember { mutableStateOf(emptyList<NexusCatalog.Category>()) }
    var movieCategory by remember { mutableStateOf<String?>(null) }
    var movieSort by remember { mutableStateOf("recent") }
    var movieFavorites by remember { mutableStateOf(false) }
    DisposableEffect(session, selected) {
        val alive = java.util.concurrent.atomic.AtomicBoolean(true)
        movieCategory = null
        movieCategories = emptyList()
        if (selected in 2..3) catalog.categories(session, if (selected == 3) "series" else "movie") { result ->
            handler.post { if (alive.get()) result.onSuccess { movieCategories = it } }
        }
        onDispose { alive.set(false) }
    }
    var liveSort by remember { mutableStateOf("default") }
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
    val parental = remember(session.userId) { NexusParental(context, session.userId) }
    var adultUnlocked by remember(session.userId) { mutableStateOf(false) }
    var parentalDialog by remember { mutableStateOf(false) }
    val prefs = remember(session.userId) { context.getSharedPreferences("nexus_favorites_" + session.userId, android.content.Context.MODE_PRIVATE) }
    var favorites by remember(session.userId) { mutableStateOf(prefs.getStringSet("ids", emptySet())?.toSet() ?: emptySet()) }
    var favoritesOnly by remember { mutableStateOf(false) }
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
    var liveExpanded by remember { mutableStateOf(false) }
    var requestedLiveId by remember { mutableStateOf<String?>(null) }
    var playbackGeneration by remember { mutableStateOf(0) }
    fun allowed(item: NexusCatalog.Item): Boolean = adultUnlocked || !item.adult && !NexusContentPolicy.isAdult(item.title, liveCategories.firstOrNull { it.id == item.categoryId }?.name.orEmpty())
    fun lockAdult() {
        adultUnlocked = false
        liveCategoryId = null
        seriesDetail = null
        // Cancel pending authorization as well as playback, so locking cannot race a response.
        playbackGeneration++
        playbackCall?.cancel()
        playbackBusy = false
        playingItem = null
        playbackUrl = null
        requestedLiveId = null
        liveExpanded = false
    }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, session.userId) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) { lockAdult(); parentalDialog = false }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    if (parentalDialog) NexusParentalDialog(parental, adultUnlocked,
        onUnlock = { adultUnlocked = true }, onLock = { lockAdult() }, onDismiss = { parentalDialog = false })
    fun play(item: NexusCatalog.Item) {
        if (!allowed(item)) { parentalDialog = true; return }
        val live = item.type.lowercase() in setOf("live", "channel", "tv")
        if (live) {
            selected = 1
            if (requestedLiveId == item.id) {
                liveExpanded = true
                return
            }
            requestedLiveId = item.id
            liveExpanded = false
        } else {
            requestedLiveId = null
        }
        playbackCall?.cancel()
        val generation = ++playbackGeneration
        playbackBusy = true
        playbackError = null
        try {
            val installation = NexusInstallation.identifier(context)
            playbackCall = playbackAccess.resolve(session, item.id, installation) { result ->
                handler.post {
                    if (active.get() && generation == playbackGeneration) {
                        playbackBusy = false
                        result.fold(onSuccess = { url ->
                            playbackUrl = url
                            playingItem = item
                        }, onFailure = {
                            requestedLiveId = null
                            liveExpanded = false
                            playbackError = it.message ?: "Não foi possível abrir o conteúdo."
                        })
                    }
                }
            }
        } catch (_: Exception) {
            requestedLiveId = null
            liveExpanded = false
            playbackBusy = false
            playbackError = "Não foi possível identificar ou autorizar este aparelho."
        }
    }
    var catalogCall by remember { mutableStateOf<okhttp3.Call?>(null) }
    DisposableEffect(Unit) { onDispose { catalogCall?.cancel() } }
    fun loadPage() {
        if (loading || !more) return
        loading = true
        error = null
        val generation = catalogGeneration
        val contentType = when (selected) { 1 -> "live"; 2 -> "movie"; 3 -> "series"; else -> null }
        catalogCall = catalog.page(session, items.size, contentType = contentType, query = query, seriesShows = selected == 3, categoryId = if (selected == 1) liveCategoryId else if (selected in 2..3) movieCategory else null, recentlyAdded = selected in 2..3 && movieSort == "recent", sortOrder = if (selected == 1) liveSort else if (selected in 2..3) movieSort else "az") { result ->
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
    LaunchedEffect(session, selected, query, liveCategoryId, liveSort, movieCategory, movieSort) {
        catalogGeneration++
        catalogCall?.cancel()
        items = emptyList()
        more = true
        loading = false
        error = null
        if (query.isNotBlank()) kotlinx.coroutines.delay(300)
        if (selected in 1..3) loadPage()
    }
    var episodeItems by remember(session.userId) { mutableStateOf(emptyList<NexusCatalog.Item>()) }
    var episodesLoading by remember { mutableStateOf(false) }
    var episodesError by remember { mutableStateOf<String?>(null) }
    var episodesRetry by remember { mutableStateOf(0) }
    DisposableEffect(session, seriesDetail?.id, episodesRetry) {
        val alive = java.util.concurrent.atomic.AtomicBoolean(true)
        var episodeCall: okhttp3.Call? = null
        episodeItems = emptyList()
        episodesError = null
        val show = seriesDetail
        episodesLoading = show != null
        if (show != null) {
            fun next(offset: Int) {
                if (!alive.get()) return
                // Fetch only this show's metadata; grouping never waits for the whole library.
                episodeCall = catalog.page(session, offset, contentType = "series", query = show.title, sortOrder = "az", seriesTitle = show.title) { result ->
                    handler.post {
                        if (alive.get()) result.fold(onSuccess = { page ->
                            episodeItems = (episodeItems + page.filter { NexusSeries.key(it.title) == NexusSeries.key(show.title) }).distinctBy { it.id }
                            if (page.size == 100) next(offset + page.size) else episodesLoading = false
                        }, onFailure = {
                            episodesLoading = false
                            episodesError = "Não foi possível carregar os episódios. Tente novamente."
                        })
                    }
                }
            }
            next(0)
        }
        onDispose { alive.set(false); episodeCall?.cancel() }
    }
    androidx.activity.compose.BackHandler(enabled = seriesDetail != null) { seriesDetail = null }
    val currentPlayback = playingItem
    val currentUrl = playbackUrl
    val livePlayback = currentPlayback?.type?.lowercase() in setOf("live", "channel", "tv")
    if (currentPlayback != null && currentUrl != null && !livePlayback) {
        androidx.activity.compose.BackHandler { playingItem = null; playbackUrl = null }
        NexusPlaybackScreen(session, currentPlayback, currentUrl) {
            playingItem = null
            playbackUrl = null
        }
        return
    }
    // Move the same player composition between preview and fullscreen without reopening the stream.
    val liveVideo = remember(currentPlayback?.id, currentUrl) {
        movableContentOf {
            if (currentPlayback != null && currentUrl != null && livePlayback) {
                val history = remember(session.userId) { NexusWatchHistory(context, session.userId) }
                NexusPlayer(currentUrl, Modifier.fillMaxSize(), isLive = true,
                    onProgress = { history.record(currentPlayback.id, currentPlayback.title, it) })
            }
        }
    }
    val showLive = selected == 1 && livePlayback && currentUrl != null
    LaunchedEffect(selected) {
        if (selected != 1 && requestedLiveId != null) {
            playbackGeneration++
            playbackCall?.cancel()
            playbackBusy = false
            playingItem = null
            playbackUrl = null
            requestedLiveId = null
            liveExpanded = false
        }
    }
    androidx.activity.compose.BackHandler(enabled = showLive && liveExpanded) { liveExpanded = false }
    NexusLiveImmersive(showLive && liveExpanded)
    if (selected == 1) {
        NexusLiveScreen(
            categories = liveCategories.filter { adultUnlocked || !NexusContentPolicy.isAdult(it.name) }, categoryId = liveCategoryId,
            onCategory = { liveCategoryId = it }, query = query, onQuery = { query = it },
            channels = items.filter { allowed(it) }, favorites = favorites,
            onFavorite = { item ->
                favorites = if (item.id in favorites) favorites - item.id else favorites + item.id
                prefs.edit().putStringSet("ids", favorites.toSet()).apply()
            }, userId = session.userId, selectedId = requestedLiveId,
            title = currentPlayback?.title, busy = playbackBusy,
            error = playbackError ?: error ?: categoryError, loading = loading, more = more,
            onMore = { loadPage() }, onPlay = { play(it) },
            onBack = { selected = 0; query = "" }, expanded = showLive && liveExpanded,
            sortOrder = liveSort, onSort = { liveSort = it }, onParental = { parentalDialog = true },
            onExpand = { liveExpanded = !liveExpanded }, video = { liveVideo() }
        )
        return
    }
    if (selected == 3 && seriesDetail != null) {
        NexusSeriesDetailScreen(
            show = seriesDetail!!,
            episodes = NexusSeries.episodes(episodeItems, seriesDetail!!.title).filter { allowed(it.item) },
            loading = episodesLoading, busy = playbackBusy, error = playbackError ?: episodesError,
            onBack = { seriesDetail = null }, onPlay = { play(it) }, onRetry = { playbackError = null; episodesRetry++ }
        )
        return
    }
    if (selected in 2..3) {
        androidx.activity.compose.BackHandler { selected = 0; query = "" }
        NexusMoviesScreen(
            contentLabel = if (selected == 3) "SÉRIES" else "FILMES",
            categories = movieCategories.filter { adultUnlocked || !NexusContentPolicy.isAdult(it.name) },
            categoryId = movieCategory, onCategory = { movieCategory = it; movieFavorites = false },
            items = remember(items, adultUnlocked, favorites, movieFavorites, selected) {
                val visible = items.filter { allowed(it) }
                if (selected == 3) NexusSeries.shows(visible).filter { !movieFavorites || it.id in favorites }
                else visible.filter { !movieFavorites || it.id in favorites }
            },
            query = query, onQuery = { query = it }, sort = movieSort, onSort = { movieSort = it },
            favoritesOnly = movieFavorites, onFavorites = { movieFavorites = !movieFavorites },
            favorites = favorites, onFavorite = { item ->
                favorites = if (item.id in favorites) favorites - item.id else favorites + item.id
                prefs.edit().putStringSet("ids", favorites.toSet()).apply()
            }, loading = loading, more = more, error = playbackError ?: error,
            busy = playbackBusy, onMore = { loadPage() }, onPlay = { if (selected == 3) seriesDetail = it else play(it) },
            onBack = { selected = 0; query = "" }
        )
        return
    }
    if (selected == 4) {
        androidx.activity.compose.BackHandler { selected = 0 }
        NexusSettingsScreen(session, onParental = { parentalDialog = true }, onExit = onExit, onBack = { selected = 0 })
        return
    }
    Row(Modifier.fillMaxSize().background(Color.Black).safeDrawingPadding()) {
        if (!showLive || !liveExpanded) Box(if (showLive) Modifier.weight(1f).fillMaxHeight() else Modifier.fillMaxSize()) {
    MaterialTheme(colorScheme = darkColorScheme(
        primary = gold, onPrimary = Color.Black,
        background = Color(0xFF080808), surface = Color(0xFF171717)
    )) {
        val configuration = androidx.compose.ui.platform.LocalConfiguration.current
        val compactHome = selected == 0 && seriesDetail == null && configuration.screenWidthDp > configuration.screenHeightDp
        Scaffold(
            containerColor = Color(0xFF080808),
            bottomBar = {
                NavigationBar(containerColor = Color(0xFF101010), modifier = Modifier.height(64.dp)) {
                    tabs.forEachIndexed { index, name ->
                        NavigationBarItem(
                            selected = selected == index,
                            onClick = { selected = index; seriesDetail = null; favoritesOnly = false; query = "" },
                            icon = { Icon(listOf(Icons.Filled.Home, Icons.Filled.LiveTv, Icons.Filled.Movie, Icons.Filled.VideoLibrary, Icons.Filled.MoreHoriz)[index], contentDescription = name) },
                            label = { Text(name, fontSize = 11.sp) },
                            colors = NavigationBarItemDefaults.colors(selectedIconColor = gold, selectedTextColor = gold, indicatorColor = Color(0xFF332A16), unselectedIconColor = Color.Gray, unselectedTextColor = Color.Gray)
                        )
                    }
                }
            }
        ) { padding ->
            Column(
                Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp, vertical = 6.dp)
                    .then(if (compactHome) Modifier else Modifier.verticalScroll(rememberScrollState())),
                verticalArrangement = Arrangement.spacedBy(if (compactHome) 4.dp else 10.dp)
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
                    Text(detail.synopsis ?: "Sinopse não disponível no catálogo.", color = Color.LightGray)
                    return@Column
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    NexusBrandMark(Modifier.size(36.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("NEXUS PLAY", color = gold, fontSize = 23.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    IconButton(onClick = { selected = 4 }, modifier = Modifier.size(36.dp)) { Icon(Icons.Filled.Menu, "Menu", tint = gold) }
                }
                if (playbackBusy) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = gold)
                    Text("Autorizando reprodução...", color = Color.LightGray)
                }
                playbackError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (selected != 0) Text(tabs[selected], style = MaterialTheme.typography.headlineSmall)
                if (selected == 0) {
                    NexusHomeDashboard(
                        items = homeItems.filter { it.type != "series" && allowed(it) } + NexusSeries.shows(homeItems.filter { it.type == "series" && allowed(it) }), loading = homeLoading, failed = homeFailed,
                        busy = playbackBusy, onRetry = { homeRetry++ },
                        onOpen = { entry ->
                            if (entry.type == "series") { selected = 3; seriesDetail = entry } else play(entry)
                        },
                        onBrowse = { selected = it },
                        modifier = if (compactHome) Modifier.fillMaxWidth().weight(1f) else Modifier.fillMaxWidth()
                    )
                }
                val sections = when (selected) {
                    0 -> listOf("Filmes • Novidades", "Séries • Novidades")
                    1 -> listOf("Categorias", "Favoritos", "Recentes")
                    2 -> listOf("Categorias de filmes", "Favoritos")
                    3 -> listOf("Categorias de séries", "Favoritos")
                    else -> listOf("Minha conta", "Dispositivos")
                }
                if (selected in 1..3) {
                    NexusSearchField(query, { query = it })
                    if (selected == 1) {
                        Box {
                            OutlinedButton(onClick = { categoryMenu = true }, modifier = Modifier.fillMaxWidth()) {
                                Text(liveCategories.firstOrNull { it.id == liveCategoryId }?.name ?: "Todas as categorias ▾")
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
                        FilterChip(selected = !favoritesOnly, onClick = { favoritesOnly = false }, label = { Text("Todos") })
                        FilterChip(selected = favoritesOnly, onClick = { favoritesOnly = true }, label = { Text("Favoritos") })
                    }
                    val visible = items.filter { allowed(it) && (!favoritesOnly || it.id in favorites) && it.type.lowercase() in accepted && it.title.contains(query, ignoreCase = true) }
                    visible.forEach { item ->
                        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(containerColor = if (selected == 1 && requestedLiveId == item.id) Color(0xFF332A16) else Color(0xFF151515))) {
                            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                Row(Modifier.weight(1f).clickable(enabled = selected == 1 || !playbackBusy) { play(item) }, verticalAlignment = Alignment.CenterVertically) {
                                    NexusCatalogArtwork(item, Modifier.size(52.dp), contain = selected == 1)
                                    Text(item.title, Modifier.weight(1f).padding(12.dp), color = Color.White)
                                }
                                TextButton(onClick = {
                                    favorites = if (item.id in favorites) favorites - item.id else favorites + item.id
                                    prefs.edit().putStringSet("ids", favorites.toSet()).apply()
                                }) { Text(if (item.id in favorites) "★" else "☆", color = gold, fontSize = 24.sp) }
                            }
                        }
                    }
                    if (visible.isEmpty() && !loading) Text("Nenhum resultado nesta página. Use a pesquisa, escolha uma categoria ou carregue mais.")
                    if (loading) CircularProgressIndicator()
                    error?.let { Text(it) }
                    if (more && !loading) Button(onClick = { loadPage() }) { Text("Carregar mais") }
                 } else if (selected == 0) {
                    // Home sections are rendered together by NexusHomeDashboard.
                } else {
                    NexusSettingsScreen(session, onParental = { parentalDialog = true }, onExit = onExit, onBack = { selected = 0 })
                }
            }
        }
    }
        }
        if (showLive) {
            Column(if (liveExpanded) Modifier.fillMaxSize() else Modifier.fillMaxHeight().weight(1f).padding(8.dp)) {
                Box(Modifier.fillMaxWidth().weight(1f).background(Color.Black)) {
                    liveVideo()
                    if (liveExpanded) {
                        Surface(Modifier.align(Alignment.TopStart).padding(8.dp), color = Color.Black.copy(alpha = .7f)) {
                            IconButton(onClick = { liveExpanded = false }) {
                                Icon(androidx.compose.material.icons.Icons.Filled.ArrowBack, "Voltar ao mini player", tint = gold)
                            }
                        }
                    }
                    Surface(Modifier.align(Alignment.TopEnd).padding(8.dp), color = Color.Black.copy(alpha = .7f)) {
                        IconButton(onClick = { liveExpanded = !liveExpanded }) {
                            androidx.compose.material3.Icon(
                                if (liveExpanded) androidx.compose.material.icons.Icons.Filled.FullscreenExit
                                else androidx.compose.material.icons.Icons.Filled.Fullscreen,
                                if (liveExpanded) "Voltar ao mini player" else "Tela cheia", tint = gold)
                        }
                    }
                }
                if (!liveExpanded) {
                    Text(currentPlayback?.title.orEmpty(), color = gold, modifier = Modifier.padding(8.dp))
                    Text("Toque novamente no canal para tela cheia", color = Color.LightGray, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun NexusLiveImmersive(fullscreen: Boolean) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val activity = remember(context) {
        var owner = context
        while (owner is android.content.ContextWrapper && owner !is android.app.Activity) owner = owner.baseContext
        owner as? android.app.Activity
    }
    DisposableEffect(activity, fullscreen) {
        val window = activity?.window
        val controller = window?.let { androidx.core.view.WindowCompat.getInsetsController(it, it.decorView) }
        val behavior = controller?.systemBarsBehavior
        val insets = window?.decorView?.let { androidx.core.view.ViewCompat.getRootWindowInsets(it) }
        if (fullscreen) {
            controller?.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller?.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            if (fullscreen && controller != null) {
                if (behavior != null) controller.systemBarsBehavior = behavior
                for (type in listOf(androidx.core.view.WindowInsetsCompat.Type.statusBars(), androidx.core.view.WindowInsetsCompat.Type.navigationBars())) {
                    if (insets?.isVisible(type) != false) controller.show(type) else controller.hide(type)
                }
            }
        }
    }
}


@Composable
internal fun NexusCatalogArtwork(item: NexusCatalog.Item, modifier: Modifier, contain: Boolean) {
    Box(modifier.clip(RoundedCornerShape(10.dp)).background(Color(0xFF202020)), contentAlignment = Alignment.Center) {
        Icon(if (contain) Icons.Filled.LiveTv else Icons.Filled.Movie, contentDescription = null,
            tint = Color(0xFFE5C465), modifier = Modifier.size(28.dp))
        if (!item.logoUrl.isNullOrBlank()) {
            coil.compose.AsyncImage(model = item.logoUrl, contentDescription = item.title,
                contentScale = if (contain) ContentScale.Fit else ContentScale.Crop,
                modifier = Modifier.fillMaxSize().then(if (contain) Modifier.padding(6.dp) else Modifier))
        }
    }
}

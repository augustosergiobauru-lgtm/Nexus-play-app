package app.opentv

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.*
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
import coil.compose.AsyncImage

private val MovieGold = Color(0xFFE5C465)

@Composable
fun NexusMoviesScreen(
    contentLabel: String = "FILMES",
    categories: List<NexusCatalog.Category>, categoryId: String?, onCategory: (String?) -> Unit,
    items: List<NexusCatalog.Item>, query: String, onQuery: (String) -> Unit,
    sort: String, onSort: (String) -> Unit, favoritesOnly: Boolean, onFavorites: () -> Unit,
    favorites: Set<String>, onFavorite: (NexusCatalog.Item) -> Unit,
    loading: Boolean, more: Boolean, error: String?, busy: Boolean,
    onMore: () -> Unit, onPlay: (NexusCatalog.Item) -> Unit, onBack: () -> Unit
) {
    var selectedId by remember { mutableStateOf<String?>(null) }
    val selected = items.firstOrNull { it.id == selectedId } ?: items.firstOrNull()
    val grid = rememberLazyGridState()
    LaunchedEffect(categoryId, query, sort, favoritesOnly) { grid.scrollToItem(0); selectedId = null }
    val nearEnd by remember { derivedStateOf { (grid.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1) >= grid.layoutInfo.totalItemsCount - 9 } }
    LaunchedEffect(nearEnd, loading, more, items.size) { if (contentLabel != "SÉRIES" && nearEnd && more && !loading && error == null) onMore() }
    val heading = if (favoritesOnly) "Favoritos" else categories.firstOrNull { it.id == categoryId }?.name ?: if (sort == "recent") "Adicionados recentemente" else "Tudo"
    MaterialTheme(colorScheme = darkColorScheme(primary = MovieGold, onPrimary = Color.Black, surface = Color(0xFF191919))) {
        BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xFF080808)).safeDrawingPadding()) {
            val landscape = maxWidth > maxHeight
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                    NexusBrandMark(Modifier.size(38.dp))
                    Text(contentLabel, color = MovieGold, fontSize = 14.sp, modifier = Modifier.padding(start = 12.dp))
                    Text(" /  $heading", color = Color.White, fontSize = 19.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    OutlinedIconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Voltar", tint = MovieGold) }
                }
                Row(Modifier.weight(1f)) {
                    Column(Modifier.fillMaxHeight().width(if (landscape) 190.dp else 125.dp).background(Color(0xFF151515)).padding(10.dp)) {
                        NexusSearchField(query, onQuery)
                        var menu by remember { mutableStateOf(false) }
                        Box {
                            OutlinedButton(onClick = { menu = true }, contentPadding = PaddingValues(7.dp), modifier = Modifier.fillMaxWidth()) {
                                Text("Ordem: " + when(sort) { "az" -> "A–Z"; "za" -> "Z–A"; "default" -> "Padrão"; else -> "Recentes" }, fontSize = 11.sp)
                            }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                listOf("default" to "Padrão", "recent" to "Recentes", "az" to "A–Z", "za" to "Z–A").forEach { (key, label) ->
                                    DropdownMenuItem(text = { Text(label) }, onClick = { menu = false; onSort(key) })
                                }
                            }
                        }
                        LazyColumn(Modifier.weight(1f)) {
                            item { MovieCategory("ADICIONADOS RECENTEMENTE", categoryId == null && sort == "recent" && !favoritesOnly) { onCategory(null); onSort("recent") } }
                            item { MovieCategory("TUDO", categoryId == null && sort != "recent" && !favoritesOnly) { onCategory(null); onSort("default") } }
                            item { MovieCategory("FAVORITOS", favoritesOnly, onFavorites) }
                            items(categories, key = { it.id }) { category -> MovieCategory(category.name, category.id == categoryId && !favoritesOnly) { onCategory(category.id) } }
                        }
                    }
                    Column(Modifier.weight(1f).fillMaxHeight()) {
                        if (loading || busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = MovieGold)
                        error?.let { Text(it, color = Color(0xFFFFB4AB), modifier = Modifier.padding(8.dp)); TextButton(onClick = onMore) { Text("Tentar novamente") } }
                        if (items.isEmpty() && !loading && error == null) Text("Nenhum conteúdo encontrado.", color = Color.LightGray, modifier = Modifier.padding(16.dp))
                        LazyVerticalGrid(columns = if (landscape) GridCells.Fixed(4) else GridCells.Adaptive(100.dp), state = grid, contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(18.dp), verticalArrangement = Arrangement.spacedBy(18.dp), modifier = Modifier.weight(1f)) {
                            items(items, key = { it.id }) { movie ->
                                Column(Modifier.clickable { if (contentLabel == "SÉRIES" || selectedId == movie.id) onPlay(movie) else selectedId = movie.id }) {
                                    MoviePoster(movie, Modifier.fillMaxWidth().aspectRatio(0.61f), selected?.id == movie.id)
                                    Text(movie.title, color = Color.White, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                                    if (!landscape && selectedId == movie.id) {
                                        TextButton(onClick = { onPlay(movie) }, enabled = !busy) { Text(if (contentLabel == "SÉRIES") "Temporadas e episódios" else "Assistir") }
                                        TextButton(onClick = { onFavorite(movie) }) { Text(if (movie.id in favorites) "★ Favorito" else "☆ Favoritar") }
                                    }
                                }
                            }
                            if (more) item(span = { GridItemSpan(maxLineSpan) }) { TextButton(onClick = onMore, enabled = !loading) { Text(if (loading) "Carregando…" else "Carregar mais") } }
                        }
                    }
                    if (landscape) Column(Modifier.width(205.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(12.dp)) {
                        selected?.let { movie ->
                            MoviePoster(movie, Modifier.fillMaxWidth().aspectRatio(0.67f))
                            Text(movie.title, color = MovieGold, fontSize = 18.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 14.dp))
                            Button(onClick = { onPlay(movie) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.PlayArrow, null); Text(if (contentLabel == "SÉRIES") "Temporadas e episódios" else "Assistir") }
                            OutlinedButton(onClick = { onFavorite(movie) }, modifier = Modifier.fillMaxWidth()) { Text(if (movie.id in favorites) "★ Favorito" else "☆ Favoritar") }
                            movie.synopsis?.let { Text(it, color = Color.LightGray, fontSize = 13.sp) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MovieCategory(label: String, active: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(3.dp).height(15.dp).background(if (active) MovieGold else Color.Transparent))
        Text(label, color = if (active) MovieGold else Color.LightGray, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun MoviePoster(movie: NexusCatalog.Item, modifier: Modifier, selected: Boolean = false) {
    Box(modifier.clip(RoundedCornerShape(9.dp)).background(Color(0xFF242018)).then(if (selected) Modifier.border(2.dp, MovieGold, RoundedCornerShape(9.dp)) else Modifier), contentAlignment = Alignment.Center) {
        Icon(Icons.Default.Movie, null, tint = MovieGold.copy(alpha = 0.4f), modifier = Modifier.size(34.dp))
        AsyncImage(model = movie.logoUrl, contentDescription = movie.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    }
}

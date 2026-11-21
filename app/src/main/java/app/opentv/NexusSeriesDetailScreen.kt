package app.opentv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

@Composable
fun NexusSeriesDetailScreen(
    show: NexusCatalog.Item, episodes: List<NexusSeries.Episode>, loading: Boolean, busy: Boolean,
    error: String?, onBack: () -> Unit, onPlay: (NexusCatalog.Item) -> Unit, onRetry: () -> Unit
) {
    val gold = Color(0xFFE5C465)
    val seasons = remember(episodes) { episodes.map { it.season }.distinct() }
    var chosenSeason by remember(show.id) { mutableStateOf<Int?>(null) }
    val season = chosenSeason?.takeIf { it in seasons } ?: seasons.firstOrNull()
    var seasonMenu by remember { mutableStateOf(false) }
    BackHandler(onBack = onBack)
    MaterialTheme(colorScheme = darkColorScheme(primary = gold, onPrimary = Color.Black, surface = Color(0xFF191919))) {
        Box(Modifier.fillMaxSize().background(Color(0xFF080808))) {
        AsyncImage(model = show.logoUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = .94f), Color.Black.copy(alpha = .82f)))))
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Voltar às séries", tint = gold) }
                Text(show.title, color = gold, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            Row(Modifier.weight(1f).padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                AsyncImage(model = show.logoUrl, contentDescription = "Capa de ${show.title}", contentScale = ContentScale.Fit,
                    modifier = Modifier.width(150.dp).fillMaxHeight().clip(RoundedCornerShape(12.dp)).background(Color(0xFF242018)))
                Column(Modifier.weight(1f)) {
                    if (loading || busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = gold)
                    error?.let {
                        Text(it, color = Color(0xFFFFB4AB))
                        TextButton(onClick = onRetry, enabled = !loading && !busy) { Text("Tentar novamente") }
                    }
                    Box {
                        OutlinedButton(onClick = { seasonMenu = true }, enabled = seasons.isNotEmpty()) {
                            Text(season?.let { "Temporada $it ▾" } ?: "Temporadas")
                        }
                        DropdownMenu(expanded = seasonMenu, onDismissRequest = { seasonMenu = false }, modifier = Modifier.heightIn(max = 280.dp)) {
                            seasons.forEach { value ->
                                DropdownMenuItem(text = { Text("Temporada $value") }, onClick = { chosenSeason = value; seasonMenu = false })
                            }
                        }
                    }
                    if (episodes.isEmpty() && !loading && error == null) Text("Nenhum episódio disponível.", color = Color.LightGray)
                    key(show.id, season) {
                        LazyColumn(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(episodes.filter { it.season == season }, key = { it.item.id }) { episode ->
                                OutlinedButton(onClick = { onPlay(episode.item) }, enabled = !busy, shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(8.dp), colors = ButtonDefaults.outlinedButtonColors(containerColor = Color(0xFF171717).copy(alpha = .85f))) {
                                    Box(Modifier.width(112.dp).height(64.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF242018)), contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.PlayArrow, null, tint = gold)
                                        AsyncImage(model = episode.item.logoUrl ?: show.logoUrl, contentDescription = null,
                                            contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                                    }
                                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                        Text(if (episode.number > 0) "Episódio ${episode.number}" else episode.item.title,
                                            color = Color.White, fontWeight = FontWeight.Bold)
                                        val synopsis = episode.item.synopsis
                                        Text(synopsis ?: "Sinopse não disponível", color = Color.LightGray,
                                            style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    }
                                    Icon(Icons.Default.PlayArrow, "Assistir", tint = gold)
                                }
                            }
                        }
                    }
                }
            }
        }
        }
    }
}

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
        Column(Modifier.fillMaxSize().background(Color(0xFF080808)).safeDrawingPadding().padding(16.dp)) {
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
                                    modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(14.dp)) {
                                    Icon(Icons.Default.PlayArrow, null, tint = gold)
                                    Text(if (episode.number > 0) "Episódio ${episode.number}" else episode.item.title,
                                        modifier = Modifier.weight(1f).padding(start = 10.dp), color = Color.White)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

package app.opentv

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** Three bounded metadata queries, never download the full library to search. */
@Composable
fun NexusGlobalSearch(session: NexusAuth.Session, catalog: NexusCatalog, query: String,
    adultUnlocked: Boolean, onOpen: (NexusCatalog.Item) -> Unit, modifier: Modifier = Modifier) {
    var results by remember(session.userId) { mutableStateOf(emptyList<NexusCatalog.Item>()) }
    var loading by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var retry by remember { mutableStateOf(0) }
    val handler = remember { android.os.Handler(android.os.Looper.getMainLooper()) }
    LaunchedEffect(session, query, retry) {
        results = emptyList(); failed = false; loading = query.isNotBlank()
        if (query.isBlank()) return@LaunchedEffect
        delay(350)
        val collected = mutableMapOf<String, List<NexusCatalog.Item>>()
        val calls = mutableListOf<okhttp3.Call>()
        var alive = true
        try {
            listOf("movie", "series", "live").forEach { type ->
                catalog.page(session, contentType = type, query = query, pageSize = 40, seriesShows = type == "series") { result ->
                    handler.post {
                        if (alive) {
                            result.fold(onSuccess = { rows -> collected[type] = if (type == "series") NexusSeries.shows(rows) else rows },
                                onFailure = { failed = true; collected[type] = emptyList() })
                            results = listOf("movie", "series", "live").flatMap { collected[it].orEmpty() }
                            loading = collected.size < 3
                        }
                    }
                }?.let { calls.add(it) }
            }
            kotlinx.coroutines.awaitCancellation()
        } finally { alive = false; calls.forEach { it.cancel() } }
    }
    Column(modifier.fillMaxWidth()) {
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Color(0xFFE5C465))
        if (failed) TextButton(onClick = { retry++ }) { Text("Falha em parte da busca. Tentar novamente") }
        val visible = remember(results, adultUnlocked) { results.filter { adultUnlocked || !it.adult && !NexusContentPolicy.isAdult(it.title) } }
        if (!loading && visible.isEmpty() && !failed) Text("Nenhum conteúdo encontrado.", color = Color.LightGray)
        LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false)) {
            items(visible, key = { it.type + ":" + it.id }) { item ->
                Row(Modifier.fillMaxWidth().clickable { onOpen(item) }.padding(8.dp)) {
                    NexusCatalogArtwork(item, Modifier.size(48.dp), contain = item.type == "live")
                    Column(Modifier.padding(start = 12.dp)) {
                        Text(item.title, color = Color.White)
                        Text(when(item.type) { "series" -> "Série"; "movie" -> "Filme"; else -> "Canal ao vivo" }, color = Color(0xFFE5C465))
                    }
                }
            }
        }
    }
}

package app.opentv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/** Development component, not yet connected to catalog authorization. */
@Composable
fun NexusPlaybackScreen(session: NexusAuth.Session, item: NexusCatalog.Item, authorizedUrl: String, onClose: () -> Unit) {
    val context = LocalContext.current
    val history = remember(session.userId) { NexusWatchHistory(context, session.userId) }
    val position = remember(item.id, session.userId) {
        if (item.type.lowercase() in setOf("live", "channel", "tv")) 0L
        else history.recent().firstOrNull { it.id == item.id }?.positionMs ?: 0L
    }
    var expanded by remember(item.id) { mutableStateOf(false) }
    BackHandler(enabled = expanded) { expanded = false }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Reduzir" else "Expandir") }
            TextButton(onClick = onClose) { Text("Fechar") }
        }
        NexusPlayer(
            authorizedUrl = authorizedUrl,
            modifier = if (expanded) Modifier.fillMaxWidth().weight(1f) else Modifier.fillMaxWidth().aspectRatio(16f / 9f),
            resumePositionMs = position,
            onProgress = { progress -> history.record(item.id, item.title, progress) }
        )
        if (!expanded) Text(item.title, Modifier.padding(16.dp), style = MaterialTheme.typography.titleLarge)
    }
}

package app.opentv

import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.ui.Alignment
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.ui.text.style.TextOverflow
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
    Column(Modifier.fillMaxSize().background(Color.Black).safeDrawingPadding()) {
        if (!expanded) Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Filled.ArrowBack, contentDescription = "Voltar", tint = Color(0xFFE5C465)) }
            Text(item.title, Modifier.weight(1f), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            IconButton(onClick = { expanded = true }) { Icon(Icons.Filled.Fullscreen, contentDescription = "Expandir", tint = Color(0xFFE5C465)) }
        }
        Box(if (expanded) Modifier.fillMaxWidth().weight(1f) else Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
        NexusPlayer(
            authorizedUrl = authorizedUrl,
            modifier = Modifier.fillMaxSize(),
            resumePositionMs = position,
            onProgress = { progress -> history.record(item.id, item.title, progress) }
        )
        Surface(modifier = Modifier.align(Alignment.BottomEnd).padding(end = 8.dp, bottom = 48.dp), color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.65f)) {
            IconButton(onClick = { expanded = !expanded }) {
                Icon(if (expanded) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen, contentDescription = if (expanded) "Sair da tela cheia" else "Tela cheia", tint = Color(0xFFE5C465))
            }
        }
        }
        if (!expanded) Text(if (item.type.lowercase() in setOf("live", "channel", "tv")) "AO VIVO" else "NEXUS PLAY", Modifier.padding(16.dp), color = Color(0xFFE5C465), style = MaterialTheme.typography.labelLarge)
    }
}

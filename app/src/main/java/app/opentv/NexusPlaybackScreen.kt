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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/** Playback for a stream already authorized by the catalog. */
@Composable
fun NexusPlaybackScreen(session: NexusAuth.Session, item: NexusCatalog.Item, authorizedUrl: String, onClose: () -> Unit) {
    val context = LocalContext.current
    val history = remember(session.userId) { NexusWatchHistory(context, session.userId) }
    val position = remember(item.id, session.userId) {
        if (item.type.lowercase() in setOf("live", "channel", "tv")) 0L
        else history.recent().firstOrNull { it.id == item.id }?.positionMs ?: 0L
    }
    var expanded by rememberSaveable(item.id) { mutableStateOf(false) }
    val activity = remember(context) {
        var owner = context
        while (owner is android.content.ContextWrapper && owner !is android.app.Activity) {
            owner = owner.baseContext
        }
        owner as? android.app.Activity
    }
    DisposableEffect(activity, expanded) {
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        val previousBehavior = controller?.systemBarsBehavior
        val previousInsets = window?.decorView?.let { androidx.core.view.ViewCompat.getRootWindowInsets(it) }
        if (expanded) {
            controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller?.hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            if (expanded && controller != null) {
                if (previousBehavior != null) controller.systemBarsBehavior = previousBehavior
                for (type in listOf(WindowInsetsCompat.Type.statusBars(), WindowInsetsCompat.Type.navigationBars())) {
                    if (previousInsets?.isVisible(type) != false) controller.show(type)
                    else controller.hide(type)
                }
            }
        }
    }
    BackHandler {
        if (expanded) expanded = false else onClose()
    }
    val gold = Color(0xFFE5C465)
    Column(Modifier.fillMaxSize().background(Color.Black).safeDrawingPadding()) {
        if (!expanded) Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Voltar", tint = gold)
            }
            Text(item.title, Modifier.weight(1f), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(if (expanded) Modifier.fillMaxWidth().weight(1f) else Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
            NexusPlayer(
                authorizedUrl = authorizedUrl,
                modifier = Modifier.fillMaxSize(),
                resumePositionMs = position,
                isLive = item.type.lowercase() in setOf("live", "channel", "tv"),
                onProgress = { progress -> history.record(item.id, item.title, progress) }
            )
            // These controls stay above the video, including in immersive fullscreen.
            if (expanded) Surface(
                modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
                color = Color.Black.copy(alpha = 0.65f),
                shape = androidx.compose.foundation.shape.CircleShape
            ) {
                IconButton(onClick = { expanded = false }) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Voltar da tela cheia", tint = gold)
                }
            }
            Surface(
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                color = Color.Black.copy(alpha = 0.65f),
                shape = androidx.compose.foundation.shape.CircleShape
            ) {
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        if (expanded) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                        contentDescription = if (expanded) "Sair da tela cheia" else "Tela cheia",
                        tint = gold
                    )
                }
            }
        }
        if (!expanded) Text(
            if (item.type.lowercase() in setOf("live", "channel", "tv")) "AO VIVO" else "NEXUS PLAY",
            Modifier.padding(16.dp), color = gold, style = MaterialTheme.typography.labelLarge
        )
    }
}

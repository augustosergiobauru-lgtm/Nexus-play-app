package app.opentv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/** Fit the original video inside the available bounds; fullscreen is always a user action. */
@Composable
fun NexusPlaybackScreen(session: NexusAuth.Session, item: NexusCatalog.Item, authorizedUrl: String, onClose: () -> Unit) {
    val context = LocalContext.current
    val history = remember(session.userId) { NexusWatchHistory(context, session.userId) }
    val position = remember(item.id, session.userId) {
        if (item.type.lowercase() in setOf("live", "channel", "tv")) 0L
        else history.recent().firstOrNull { it.id == item.id }?.positionMs ?: 0L
    }
    var expanded by remember(item.id) { mutableStateOf(false) }
    val activity = remember(context) {
        var owner = context
        while (owner is android.content.ContextWrapper && owner !is android.app.Activity) owner = owner.baseContext
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
                    if (previousInsets?.isVisible(type) != false) controller.show(type) else controller.hide(type)
                }
            }
        }
    }
    BackHandler(onBack = onClose)
    Box(Modifier.fillMaxSize().background(Color.Black).then(if (expanded) Modifier else Modifier.safeDrawingPadding())) {
        NexusPlayer(
            authorizedUrl = authorizedUrl, modifier = Modifier.fillMaxSize(), resumePositionMs = position,
            isLive = item.type.lowercase() in setOf("live", "channel", "tv"),
            onProgress = { progress -> history.record(item.id, item.title, progress) },
            title = item.title, onBack = onClose, fullscreen = expanded,
            onToggleFullscreen = { expanded = !expanded }
        )
    }
}

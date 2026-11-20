package app.opentv

import android.net.Uri
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

/** Only mount after server-side playback authorization. */
@Composable
fun NexusPlayer(authorizedUrl: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val uri = remember(authorizedUrl) { Uri.parse(authorizedUrl) }
    if (uri.scheme != "https" || uri.host.isNullOrBlank()) return
    val player = remember(context, authorizedUrl) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            prepare()
            playWhenReady = false
        }
    }
    DisposableEffect(player) {
        onDispose { player.release() }
    }
    AndroidView(
        modifier = modifier,
        factory = { PlayerView(it).apply { this.player = player } },
        update = { it.player = player }
    )
}

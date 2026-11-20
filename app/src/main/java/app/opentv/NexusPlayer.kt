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
fun NexusPlayer(authorizedUrl: String, modifier: Modifier = Modifier, resumePositionMs: Long = 0L, onProgress: (Long) -> Unit = {}) {
    val context = LocalContext.current
    val uri = remember(authorizedUrl) { Uri.parse(authorizedUrl) }
    if (uri.scheme != "https" || uri.host.isNullOrBlank()) return
    val progressCallback by rememberUpdatedState(onProgress)
    val player = remember(context, authorizedUrl) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            if (resumePositionMs > 0L) seekTo(resumePositionMs)
            prepare()
            playWhenReady = false
        }
    }
    DisposableEffect(player) {
        var started = false
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        val tick = object : Runnable {
            override fun run() {
                if (player.isPlaying) {
                    started = true
                    progressCallback(player.currentPosition.coerceAtLeast(0L))
                }
                handler.postDelayed(this, 5000L)
            }
        }
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) started = true
                if (started) progressCallback(player.currentPosition.coerceAtLeast(0L))
            }
        }
        player.addListener(listener)
        handler.post(tick)
        onDispose {
            handler.removeCallbacks(tick)
            player.removeListener(listener)
            if (started) progressCallback(player.currentPosition.coerceAtLeast(0L))
            player.release()
        }
    }
    AndroidView(
        modifier = modifier,
        factory = { PlayerView(it).apply { this.player = player } },
        update = { it.player = player }
    )
}

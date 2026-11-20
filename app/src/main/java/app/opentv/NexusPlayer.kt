package app.opentv

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
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
    var error by remember(player) { mutableStateOf<String?>(null) }
    var buffering by remember(player) { mutableStateOf(player.playbackState == Player.STATE_BUFFERING) }
    var retries by remember(player) { mutableStateOf(0) }
    LaunchedEffect(error) {
        if (error != null && retries < 2 && player.playerError?.errorCode in setOf(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED, PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT)) {
            kotlinx.coroutines.delay(1500L * (retries + 1))
            retries++
            error = null
            player.prepare()
            player.play()
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
            override fun onPlaybackStateChanged(playbackState: Int) {
                buffering = playbackState == Player.STATE_BUFFERING
            }
            override fun onPlayerError(failure: PlaybackException) {
                buffering = false
                error = failure.errorCodeName
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) started = true
                if (started) progressCallback(player.currentPosition.coerceAtLeast(0L))
            }
        }
        player.addListener(listener)
        buffering = player.playbackState == Player.STATE_BUFFERING
        error = player.playerError?.errorCodeName
        handler.post(tick)
        onDispose {
            handler.removeCallbacks(tick)
            player.removeListener(listener)
            if (started) progressCallback(player.currentPosition.coerceAtLeast(0L))
            player.release()
        }
    }
    Box(modifier) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { PlayerView(it).apply { this.player = player } },
            update = { it.player = player }
        )
        if (buffering && error == null) {
            CircularProgressIndicator(Modifier.align(Alignment.Center))
        }
        error?.let { code ->
            Surface(Modifier.align(Alignment.Center).padding(8.dp)) {
                Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(when (code) {
                        "ERROR_CODE_IO_BAD_HTTP_STATUS" -> "O servidor recusou a reprodução. Verifique a disponibilidade do vídeo."
                        "ERROR_CODE_IO_NETWORK_CONNECTION_FAILED", "ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT" -> "Falha de conexão. Verifique sua internet."
                        else -> "Falha na reprodução: $code"
                    })
                    Button(onClick = {
                        retries = 0
                        error = null
                        player.prepare()
                        player.play()
                    }) { Text("Tentar novamente") }
                }
            }
        }
    }
}

package app.opentv

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.opentv.player.NexusPlayback

/** Mounted only after the existing subscription and device authorization gate. */
@Composable
fun NexusPlayer(authorizedUrl: String, modifier: Modifier = Modifier, resumePositionMs: Long = 0L,
                isLive: Boolean = false, onProgress: (Long) -> Unit = {},
                title: String = "", onBack: (() -> Unit)? = null, fullscreen: Boolean = false,
                onToggleFullscreen: (() -> Unit)? = null) {
    val context = LocalContext.current
    val uri = remember(authorizedUrl) { Uri.parse(authorizedUrl) }
    if (uri.scheme !in setOf("http", "https") || uri.host.isNullOrBlank()) return
    val progressCallback by rememberUpdatedState(onProgress)
    val session = remember(context, authorizedUrl, isLive) {
        NexusPlayback(context, authorizedUrl, if (context.getSharedPreferences("nexus_playback_options", android.content.Context.MODE_PRIVATE).getBoolean("resume", true)) resumePositionMs else 0L, isLive) { progressCallback(it) }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(session, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> session.setForeground(false)
                Lifecycle.Event.ON_START -> session.setForeground(true)
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        session.setForeground(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
        onDispose { lifecycle.removeObserver(observer); session.release() }
    }
    // Apply to the shared Compose view so this covers both engines and mini/fullscreen.
    val playbackView = LocalView.current
    val keepAwake = session.error == null && (session.playing || session.buffering)
    DisposableEffect(playbackView, keepAwake) {
        val previous = playbackView.keepScreenOn
        playbackView.keepScreenOn = previous || keepAwake
        onDispose { playbackView.keepScreenOn = previous }
    }
    var panel by remember { mutableStateOf<String?>(null) }
    var controls by remember(authorizedUrl) { mutableStateOf(true) }
    var interaction by remember { mutableStateOf(0) }
    var scrub by remember { mutableStateOf<Float?>(null) }
    LaunchedEffect(controls, interaction, panel, scrub != null) {
        if (controls && panel == null && scrub == null) {
            kotlinx.coroutines.delay(2500)
            controls = false
        }
    }
    val gold = Color(0xFFE5C465)
    MaterialTheme(colorScheme = darkColorScheme(primary = gold, onPrimary = Color.Black, surface = Color(0xFF171717))) {
        Box(modifier.background(Color.Black)) {
            key(session, session.surfaceRevision, session.engine) {
                AndroidView(modifier = Modifier.fillMaxSize(), factory = { session.surface() })
            }
            if (session.buffering && session.error == null) CircularProgressIndicator(Modifier.align(Alignment.Center), color = gold)
            Box(Modifier.fillMaxSize().testTag("video-touch-area").clickable { controls = !controls; interaction++ })
            if (controls && onBack != null) Row(
                Modifier.align(Alignment.TopCenter).fillMaxWidth().background(Color.Black.copy(alpha = .55f)).padding(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Voltar ao catálogo", tint = gold) }
                Text(title, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color.White)
                if (onToggleFullscreen != null) IconButton(onClick = { onToggleFullscreen(); interaction++ }) {
                    Icon(if (fullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                        if (fullscreen) "Sair da tela cheia" else "Tela cheia", tint = gold)
                }
            }
            if (controls) Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color.Black.copy(alpha = .25f))) {
                if (session.seekable && session.duration > 0) {
                    Slider(value = scrub ?: session.position.toFloat().coerceIn(0f, session.duration.toFloat()),
                        onValueChange = { scrub = it; interaction++ },
                        onValueChangeFinished = { scrub?.let { session.seek(it.toLong()) }; scrub = null },
                        valueRange = 0f..session.duration.toFloat(), modifier = Modifier.fillMaxWidth().height(22.dp).padding(horizontal = 12.dp))
                }
                Row(Modifier.fillMaxWidth().then(if (isLive) Modifier.padding(end = 36.dp) else Modifier), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(modifier = Modifier.size(32.dp), onClick = { session.togglePlay(); interaction++ }, enabled = session.error == null) {
                        Icon(if (session.playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                            if (session.playing) "Pausar" else "Reproduzir", tint = gold, modifier = Modifier.size(18.dp))
                    }
                    if (!isLive) IconButton(modifier = Modifier.size(32.dp), onClick = { interaction++; session.seek(session.position - 15_000L) }, enabled = session.seekable) { Icon(Icons.Default.FastRewind, "Voltar 15 segundos", Modifier.size(18.dp)) }
                    if (!isLive) IconButton(modifier = Modifier.size(32.dp), onClick = { interaction++; session.seek(session.position + 15_000L) }, enabled = session.seekable) { Icon(Icons.Default.FastForward, "Avançar 15 segundos", Modifier.size(18.dp)) }
                    Text(if (isLive) "AO VIVO" else timeLabel(session.position), Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
                    IconButton(modifier = Modifier.size(40.dp), onClick = { panel = "Volume"; interaction++ }) { Icon(if (session.muted) Icons.Default.VolumeOff else Icons.Default.VolumeUp, "Volume", Modifier.size(20.dp)) }
                    IconButton(modifier = Modifier.size(40.dp), onClick = { panel = "Qualidade"; interaction++ }) { Icon(Icons.Default.HighQuality, "Qualidade do vídeo", Modifier.size(20.dp)) }
                    IconButton(modifier = Modifier.size(32.dp), onClick = { panel = "Opções"; interaction++ }) { Icon(Icons.Default.Settings, "Opções de reprodução", Modifier.size(18.dp)) }
                }
            }
            session.error?.let { message ->
                Surface(Modifier.align(Alignment.Center).padding(12.dp)) {
                    Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(message)
                        Button(onClick = session::retry) { Text("Tentar novamente") }
                    }
                }
            }
        }
        panel?.let { title ->
            AlertDialog(onDismissRequest = { panel = null }, title = { Text(title) },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        when (title) {
                            "Volume" -> {
                                Text("Volume: ${(session.volume * 100).toInt()}%")
                                Slider(value = session.volume, onValueChange = session::changeVolume, valueRange = 0f..1f,
                                    modifier = Modifier.testTag("playback-volume"))
                                TextButton(onClick = session::toggleMute) { Text(if (session.muted) "Ativar som" else "Silenciar") }
                            }
                            "Qualidade" -> {
                                Text("Atual: ${session.qualityLabel}")
                                TextButton(onClick = { session.selectVideoTrack(-1); panel = null }) { Text("Automática") }
                                session.videoTracks.forEach { track ->
                                    TextButton(onClick = { session.selectVideoTrack(track.id); panel = null }) { Text(track.label) }
                                }
                                if (session.videoTracks.size <= 1) Text("A resolução depende da fonte. Este vídeo não oferece outras qualidades.")
                            }
                            "Áudio", "Legendas" -> {
                                val text = title == "Legendas"
                                val tracks = if (text) session.subtitles else session.audioTracks
                                if (text) TextButton(onClick = { session.selectTrack(-1, true); panel = null }) { Text("Desativar legendas") }
                                tracks.forEach { track -> TextButton(onClick = { session.selectTrack(track.id, text); panel = null }) { Text(track.label) } }
                                if (tracks.isEmpty()) Text("Nenhuma faixa disponível nesta transmissão.")
                            }
                            "Velocidade" -> listOf(.5f, 1f, 1.5f, 2f).forEach { rate ->
                                TextButton(onClick = { session.changeSpeed(rate); panel = null }) { Text("${rate}x") }
                            }
                            else -> {
                                TextButton(onClick = { panel = "Qualidade" }) { Text("Qualidade") }
                                TextButton(onClick = { panel = "Volume" }) { Text("Volume") }
                                TextButton(onClick = { panel = "Áudio" }) { Text("Áudio") }
                                TextButton(onClick = { panel = "Legendas" }) { Text("Legendas") }
                                TextButton(onClick = { panel = "Velocidade" }) { Text("Velocidade: ${session.speed}x") }
                            }
                        }
                    }
                }, confirmButton = { TextButton(onClick = { panel = null }) { Text("Fechar") } })
        }
    }
}

private fun timeLabel(ms: Long): String {
    val seconds = ms / 1000
    return "%d:%02d".format(seconds / 60, seconds % 60)
}

package app.opentv.player

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout

/** Main-thread session. Both surfaces and all controls share this one owner. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal class NexusPlayback(
    private val context: Context,
    private val url: String,
    startPosition: Long,
    private val live: Boolean,
    private val onProgress: (Long) -> Unit,
) {
    data class Track(val id: Int, val label: String)
    private val handler = Handler(Looper.getMainLooper())
    private var policy = EngineFallback()
    var surfaceRevision by mutableStateOf(0)
        private set
    var engine by mutableStateOf(EngineFallback.Engine.EXO)
        private set
    var videoRendered by mutableStateOf(false)
        private set
    var buffering by mutableStateOf(true)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var playing by mutableStateOf(false)
        private set
    var position by mutableStateOf(startPosition.coerceAtLeast(0L))
        private set
    var duration by mutableStateOf(0L)
        private set
    var seekable by mutableStateOf(false)
        private set
    var muted by mutableStateOf(false)
        private set
    var speed by mutableStateOf(1f)
        private set
    var audioTracks by mutableStateOf(emptyList<Track>())
        private set
    var subtitles by mutableStateOf(emptyList<Track>())
        private set
    private var desiredPlay = true
    private var released = false
    private var foreground = true
    private var generation = 0
    private var exo: ExoPlayer? = null
    private var lib: LibVLC? = null
    private var vlc: MediaPlayer? = null
    private var vlcLayout: VLCVideoLayout? = null
    private var pendingSeek: Long? = null
    private var waitingSince = System.currentTimeMillis()
    private var lastSample = -1L
    private var hasPlayed = false
    private val tick = object : Runnable {
        override fun run() {
            if (released) return
            sample()
            val now = System.currentTimeMillis()
            if (foreground && desiredPlay && error == null) {
                if (playing && position != lastSample) waitingSince = now
                if (now - waitingSince > START_TIMEOUT_MS) fail("A transmissão não respondeu a tempo.")
            } else waitingSince = now
            lastSample = position
            handler.postDelayed(this, 500L)
        }
    }

    init {
        require(Uri.parse(url).let { it.scheme == "https" && !it.host.isNullOrBlank() })
        startExo()
        handler.post(tick)
    }

    private fun startExo() {
        videoRendered = false
        val token = ++generation
        buffering = true
        waitingSince = System.currentTimeMillis()
        exo = ExoPlayer.Builder(context).build().also { p ->
            p.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    if (released || token != generation) return
                    buffering = state == Player.STATE_BUFFERING
                    if (state == Player.STATE_READY) waitingSince = System.currentTimeMillis()
                    if (state == Player.STATE_ENDED) { desiredPlay = false; playing = false }
                }
                override fun onRenderedFirstFrame() {
                    if (!released && token == generation) videoRendered = true
                }
                override fun onIsPlayingChanged(value: Boolean) {
                    if (released || token != generation) return
                    playing = value
                    if (value) hasPlayed = true
                }
                override fun onPlayerError(failure: PlaybackException) {
                    if (released || token != generation) return
                    val status = generateSequence<Throwable>(failure) { it.cause }
                        .filterIsInstance<androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException>()
                        .firstOrNull()?.responseCode
                    fail(if (status != null) "O servidor recusou a reprodução (HTTP $status)." else "Não foi possível reproduzir esta transmissão.")
                }
            })
            p.volume = if (muted) 0f else 1f
            p.setPlaybackSpeed(speed)
            p.setMediaItem(MediaItem.fromUri(url))
            if (!live && position > 0) p.seekTo(position)
            p.playWhenReady = desiredPlay && foreground
            p.prepare()
        }
    }

    /** Public to the package so instrumentation can exercise the actual error path. */
    internal fun fail(message: String) {
        if (released) return
        sample()
        if (!policy.next()) {
            error = message
            buffering = false
            playing = false
            desiredPlay = false
            vlc?.pause()
            return
        }
        ++generation // Ignore callbacks from the engine being torn down.
        exo?.release()
        exo = null
        playing = false
        buffering = true
        audioTracks = emptyList()
        subtitles = emptyList()
        videoRendered = false
        engine = policy.engine
        waitingSince = System.currentTimeMillis()
        // Do not open media until AndroidView has attached its video surface.
    }

    fun surface(): View = if (engine == EngineFallback.Engine.EXO) {
        PlayerView(context).apply { player = exo; useController = false }
    } else {
        VLCVideoLayout(context).also { layout ->
            vlcLayout = layout
            try {
                val instance = LibVLC(context.applicationContext, arrayListOf("--network-caching=1500"))
                lib = instance
                val p = MediaPlayer(instance)
                vlc = p
                p.setVolume(if (muted) 0 else 100)
                val token = generation
                p.setUseOrientationFromBounds(true)
                p.attachViews(layout, null, true, false)
                p.setEventListener { event ->
                    if (!released && generation == token) when (event.type) {
                        MediaPlayer.Event.Playing -> {
                            buffering = false
                            hasPlayed = true
                            pendingSeek?.let { if (!live && p.isSeekable) { p.setTime(it); pendingSeek = null } }
                            p.setRate(speed)
                            p.setVolume(if (muted) 0 else 100)
                            if (!desiredPlay || !foreground) p.pause()
                            playing = desiredPlay && foreground
                            waitingSince = System.currentTimeMillis()
                        }
                        MediaPlayer.Event.Vout -> videoRendered = event.voutCount > 0
                        MediaPlayer.Event.Paused -> playing = false
                        MediaPlayer.Event.Buffering -> buffering = event.buffering < 100f
                        MediaPlayer.Event.EndReached -> { playing = false; desiredPlay = false; buffering = false }
                        MediaPlayer.Event.EncounteredError -> fail("A transmissão falhou nos dois motores. Tente novamente.")
                    }
                }
                pendingSeek = if (live) null else position
                val media = Media(instance, Uri.parse(url))
                try {
                    media.setHWDecoderEnabled(true, false)
                    media.addOption(":http-user-agent=NexusPlay/1.0 (Android)")
                    p.media = media
                } finally { media.release() }
                // VLC must start to discover tracks before it can honour an initial pause.
                p.play()
            } catch (_: Exception) { fail("Não foi possível iniciar o motor alternativo.") }
            catch (_: LinkageError) { fail("O motor alternativo não está disponível neste dispositivo.") }
        }
    }

    private fun sample() {
        if (released) return
        val p = exo
        val v = vlc
        if (p != null) {
            position = p.currentPosition.coerceAtLeast(0L)
            duration = p.duration.takeIf { it > 0 } ?: 0L
            seekable = p.isCurrentMediaItemSeekable
            fun tracks(type: Int): List<Track> = p.currentTracks.groups.filter { it.type == type }
                .flatMapIndexed { groupIndex, group -> (0 until group.length).filter { group.isTrackSupported(it) }.map { i ->
                    val f = group.getTrackFormat(i)
                    Track(groupIndex * 1000 + i, f.label ?: f.language ?: "Faixa ${i + 1}")
                } }
            audioTracks = tracks(C.TRACK_TYPE_AUDIO)
            subtitles = tracks(C.TRACK_TYPE_TEXT)
        } else if (v != null) {
            pendingSeek?.let { if (v.isSeekable) { v.setTime(it); pendingSeek = null } }
            if (pendingSeek == null) position = v.time.coerceAtLeast(0L)
            duration = v.length.coerceAtLeast(0L)
            seekable = v.isSeekable
            audioTracks = v.audioTracks?.map { Track(it.id, it.name) }.orEmpty()
            subtitles = v.spuTracks?.filter { it.id >= 0 }?.map { Track(it.id, it.name) }.orEmpty()
        }
        if (hasPlayed && !live) onProgress(position)
    }

    fun togglePlay() {
        desiredPlay = !desiredPlay
        waitingSince = System.currentTimeMillis()
        exo?.playWhenReady = desiredPlay && foreground
        vlc?.let { if (desiredPlay && foreground) it.play() else it.pause() }
    }
    fun seek(value: Long) {
        if (!seekable) return
        val target = value.coerceIn(0L, duration.takeIf { it > 0 } ?: Long.MAX_VALUE)
        exo?.seekTo(target)
        vlc?.setTime(target)
        position = target
        waitingSince = System.currentTimeMillis()
    }
    fun toggleMute() {
        muted = !muted
        exo?.volume = if (muted) 0f else 1f
        vlc?.setVolume(if (muted) 0 else 100)
    }
    fun changeSpeed(value: Float) {
        speed = value
        exo?.setPlaybackSpeed(value)
        vlc?.setRate(value)
    }
    fun selectTrack(id: Int, text: Boolean) {
        val p = exo
        if (p != null) {
            val type = if (text) C.TRACK_TYPE_TEXT else C.TRACK_TYPE_AUDIO
            val builder = p.trackSelectionParameters.buildUpon().clearOverridesOfType(type).setTrackTypeDisabled(type, id < 0)
            if (id >= 0) p.currentTracks.groups.filter { it.type == type }.getOrNull(id / 1000)?.let {
                builder.setOverrideForType(TrackSelectionOverride(it.mediaTrackGroup, id % 1000))
            }
            p.trackSelectionParameters = builder.build()
        } else vlc?.let { if (text) it.setSpuTrack(id) else it.setAudioTrack(id) }
    }
    fun setForeground(value: Boolean) {
        foreground = value
        waitingSince = System.currentTimeMillis()
        exo?.playWhenReady = value && desiredPlay
        vlc?.let { if (value && desiredPlay) it.play() else it.pause() }
    }
    fun retry() {
        if (released) return
        disposeEngines()
        policy = EngineFallback()
        engine = policy.engine
        error = null
        desiredPlay = true
        startExo()
        surfaceRevision++
    }
    private fun disposeEngines() {
        ++generation
        exo?.release(); exo = null
        vlc?.let { it.setEventListener(null); it.stop(); it.detachViews(); it.release() }
        vlc = null
        vlcLayout = null
        lib?.release(); lib = null
    }
    fun release() {
        if (released) return
        sample()
        released = true
        handler.removeCallbacksAndMessages(null)
        disposeEngines()
    }
    internal companion object { const val START_TIMEOUT_MS = 30_000L }
}

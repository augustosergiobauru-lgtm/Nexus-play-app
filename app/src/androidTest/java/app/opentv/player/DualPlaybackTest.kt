package app.opentv.player

import android.widget.FrameLayout
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.opentv.MainActivity
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Public publisher-provided samples only. No Supabase account or provider credentials. */
@RunWith(AndroidJUnit4::class)
class DualPlaybackTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private fun main(action: () -> Unit) = instrumentation.runOnMainSync(action)
    private fun await(label: String, predicate: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 60_000
        while (System.currentTimeMillis() < deadline) {
            var ready = false
            main { ready = predicate() }
            if (ready) return
            Thread.sleep(250)
        }
        fail("Timed out: $label")
    }
    @Test fun publicMp4BothEnginesAndControls() = exercise("https://storage.googleapis.com/exoplayer-test-media-0/BigBuckBunny_320x180.mp4")
    @Test fun publicHlsBothEnginesAndControls() = exercise("https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8")


    @Test fun httpRedirectBothEnginesAndControls() {
        val server = java.net.ServerSocket(0, 8, java.net.InetAddress.getByName("127.0.0.1"))
        server.soTimeout = 1000
        val responder = Thread {
            while (!server.isClosed) {
                try {
                    server.accept().use { socket ->
                        socket.soTimeout = 5000
                        val reader = socket.getInputStream().bufferedReader()
                        while (true) {
                            val line = reader.readLine() ?: break
                            if (line.isEmpty()) break
                        }
                        val response = "HTTP/1.1 302 Found\r\n" +
                            "Location: https://storage.googleapis.com/exoplayer-test-media-0/BigBuckBunny_320x180.mp4\r\n" +
                            "Content-Length: 0\r\nConnection: close\r\n\r\n"
                        socket.getOutputStream().write(response.toByteArray(Charsets.US_ASCII))
                        socket.getOutputStream().flush()
                    }
                } catch (_: java.io.IOException) { /* Socket timeout or normal shutdown. */ }
            }
        }.apply { isDaemon = true; start() }
        try {
            exercise("http://127.0.0.1:${server.localPort}/redirect")
        } finally {
            server.close()
            responder.join(2000)
        }
    }

    @Test fun livePlaybackReportsHistoryOnlyAfterStarting() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var session: NexusPlayback
            var reported = false
            scenario.onActivity { activity ->
                val host = FrameLayout(activity)
                activity.setContentView(host)
                session = NexusPlayback(activity,
                    "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8", 0, true) {
                    assertEquals(0L, it)
                    reported = true
                }
                assertFalse(reported)
                host.addView(session.surface(), FrameLayout.LayoutParams(-1, 280))
            }
            try { await("Live playback recorded in history") { session.playing && reported } }
            finally { main { session.release() } }
        }
    }

    private fun exercise(url: String) {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var session: NexusPlayback
            lateinit var host: FrameLayout
            var progress = 0L
            scenario.onActivity { activity ->
                host = FrameLayout(activity)
                activity.setContentView(host)
                session = NexusPlayback(activity, url, 0, false) { progress = it }
                host.addView(session.surface(), FrameLayout.LayoutParams(-1, 280))
            }
            try {
                await("Exo decoded and advanced") { session.playing && session.videoRendered && session.position > 1500 }
                main {
                    assertEquals(EngineFallback.Engine.EXO, session.engine)
                    session.togglePlay()
                }
                await("Exo pause") { !session.playing }
                main {
                    session.toggleMute()
                    session.changeSpeed(1.5f)
                    // Force the same path called by the Exo error listener, while paused.
                    session.fail("Injected decoder failure")
                    host.removeAllViews()
                    host.addView(session.surface(), FrameLayout.LayoutParams(-1, 280))
                }
                await("VLC ready, preserving pause") { !session.buffering && !session.playing && session.seekable }
                main {
                    assertEquals(EngineFallback.Engine.VLC, session.engine)
                    assertTrue(session.muted)
                    assertEquals(1.5f, session.speed)
                    session.seek(5_000)
                    session.togglePlay()
                    // Expand the SAME surface, then return to mini player.
                    host.getChildAt(0).layoutParams = FrameLayout.LayoutParams(-1, -1)
                }
                await("VLC decoded and advanced") { session.playing && session.videoRendered && session.position > 6500 }
                main {
                    host.getChildAt(0).layoutParams = FrameLayout.LayoutParams(-1, 280)
                    assertTrue(progress > 0)
                    assertNull(session.error)
                    session.fail("Injected VLC failure")
                    assertNotNull(session.error)
                    assertFalse(session.buffering)
                    session.retry()
                    host.removeAllViews()
                    host.addView(session.surface(), FrameLayout.LayoutParams(-1, 280))
                }
                await("Explicit retry back on Exo") { session.engine == EngineFallback.Engine.EXO && session.playing }
            } finally { main { session.release(); host.removeAllViews() } }
        }
    }
}

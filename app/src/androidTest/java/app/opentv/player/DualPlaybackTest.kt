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
    @Test fun publicMp4BothEnginesAndControls() = exercise("https://storage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4")
    @Test fun publicHlsBothEnginesAndControls() = exercise("https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8")

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
                await("Exo decoded and advanced") { session.playing && session.position > 1500 }
                main {
                    assertEquals(EngineFallback.Engine.EXO, session.engine)
                    session.togglePlay()
                }
                await("Exo pause") { !session.playing }
                main {
                    session.toggleMute()
                    session.setSpeed(1.5f)
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
                await("VLC decoded and advanced") { session.playing && session.position > 6500 }
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

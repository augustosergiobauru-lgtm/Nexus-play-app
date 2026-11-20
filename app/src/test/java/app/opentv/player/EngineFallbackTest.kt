package app.opentv.player

import org.junit.Assert.*
import org.junit.Test

class EngineFallbackTest {
    @Test fun brokenSourceTriesEachEngineOnce() {
        val policy = EngineFallback()
        assertEquals(EngineFallback.Engine.EXO, policy.engine)
        assertTrue(policy.next())
        repeat(10) { assertFalse(policy.next()) }
        assertEquals(EngineFallback.Engine.VLC, policy.engine)
    }
    @Test fun newRequestOrExplicitRetryStartsWithExo() {
        val old = EngineFallback()
        old.next()
        val fresh = EngineFallback()
        assertEquals(EngineFallback.Engine.EXO, fresh.engine)
        assertTrue(fresh.next())
    }
}

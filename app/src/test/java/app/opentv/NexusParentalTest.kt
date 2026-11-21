package app.opentv

import android.content.Context
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class NexusParentalTest {
    @Test fun pinPersistsPerAccountAndDoesNotStorePlaintext() {
        val context = RuntimeEnvironment.getApplication()
        val account = "pin-test-${System.nanoTime()}"
        val store = NexusParental(context, account)
        assertFalse(store.configured)
        store.create("5831")
        assertTrue(NexusParental(context, account).verify("5831"))
        assertFalse(NexusParental(context, "other-$account").configured)
        assertFalse(context.getSharedPreferences("nexus_parental_$account", Context.MODE_PRIVATE).all.values.contains("5831"))
    }
    @Test fun repeatedFailuresBlockEvenTheCorrectPinTemporarily() {
        val context = RuntimeEnvironment.getApplication()
        val store = NexusParental(context, "attempt-test-${System.nanoTime()}")
        store.create("5831")
        repeat(5) { assertFalse(store.verify("0000")) }
        assertFalse(store.verify("5831"))
    }
}

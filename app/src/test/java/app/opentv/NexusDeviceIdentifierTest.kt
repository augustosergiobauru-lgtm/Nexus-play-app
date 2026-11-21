package app.opentv

import android.content.Context
import android.provider.Settings
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class NexusDeviceIdentifierTest {
    @Test fun restoringOldPreferencesDoesNotTransferDeviceAuthorization() {
        val context = RuntimeEnvironment.getApplication()
        val old = UUID.randomUUID().toString()
        context.getSharedPreferences("nexus_installation", Context.MODE_PRIVATE).edit().putString("identifier", old).commit()
        Settings.Secure.putString(context.contentResolver, Settings.Secure.ANDROID_ID, "first-device")
        val first = NexusInstallation.identifier(context)
        assertEquals(first, NexusInstallation.identifier(context))
        assertNotEquals(old, first)
        assertEquals(first, UUID.fromString(first).toString())
        Settings.Secure.putString(context.contentResolver, Settings.Secure.ANDROID_ID, "second-device")
        assertNotEquals(first, NexusInstallation.identifier(context))
    }
    @Test(expected = IllegalStateException::class)
    fun missingDeviceIdentifierFailsClosed() {
        val context = RuntimeEnvironment.getApplication()
        Settings.Secure.putString(context.contentResolver, Settings.Secure.ANDROID_ID, "")
        NexusInstallation.identifier(context)
    }
}

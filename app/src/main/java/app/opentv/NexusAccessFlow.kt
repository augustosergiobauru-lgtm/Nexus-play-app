package app.opentv

import android.content.Context
import java.util.UUID

/** Preliminary client gate, not a substitute for server authorization. */
class NexusAccessFlow(
    private val entitlement: NexusEntitlement,
    private val devices: NexusDeviceAccess
) {
    enum class Decision { ELIGIBLE, SUBSCRIPTION_REQUIRED, DEVICE_REQUIRED }

    fun check(session: NexusAuth.Session, customerId: String, deviceId: String,
              callback: (Result<Decision>) -> Unit) {
        entitlement.check(session) { subscription ->
            subscription.fold(onSuccess = { valid ->
                if (!valid) {
                    callback(Result.success(Decision.SUBSCRIPTION_REQUIRED))
                } else {
                    try {
                        devices.check(session, customerId, deviceId) { approval ->
                            callback(approval.map {
                                if (it) Decision.ELIGIBLE else Decision.DEVICE_REQUIRED
                            })
                        }
                    } catch (e: Exception) {
                        callback(Result.failure(e))
                    }
                }
            }, onFailure = { callback(Result.failure(it)) })
        }
    }
}

object NexusInstallation {
    @Synchronized
    fun identifier(context: Context): String {
        val prefs = context.applicationContext.getSharedPreferences("nexus_installation", Context.MODE_PRIVATE)
        val existing = prefs.getString("identifier", null)
        if (existing != null && runCatching { UUID.fromString(existing) }.isSuccess) return existing
        val id = UUID.randomUUID().toString()
        check(prefs.edit().putString("identifier", id).commit()) { "Unable to save installation identifier" }
        return id
    }
}

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
    /** Scoped Android ID survives reinstall on the same signing key and cannot travel in backups. */
    @Synchronized
    fun identifier(context: Context): String {
        val androidId = android.provider.Settings.Secure.getString(
            context.applicationContext.contentResolver, android.provider.Settings.Secure.ANDROID_ID
        )?.trim().orEmpty()
        check(androidId.isNotBlank() && androidId != "9774d56d682e549c") {
            "Não foi possível identificar este aparelho."
        }
        return UUID.nameUUIDFromBytes(
            ("nexus-device-v2:" + context.packageName + ":" + androidId).toByteArray(Charsets.UTF_8)
        ).toString()
    }
}

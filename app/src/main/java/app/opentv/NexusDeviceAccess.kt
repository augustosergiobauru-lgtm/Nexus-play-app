package app.opentv

import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONArray
import java.io.IOException

/** Read-only approval lookup. Administrative registration and server enforcement are separate. */
class NexusDeviceAccess(private val projectUrl: String, private val publishableKey: String) {
    private val client = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false).build()

    fun check(session: NexusAuth.Session, customerId: String, deviceIdentifier: String,
              callback: (Result<Boolean>) -> Unit) {
        val url = (projectUrl.trimEnd('/') + "/rest/v1/devices").toHttpUrl()
        require(url.isHttps)
        require(customerId.matches(Regex("[0-9a-fA-F-]{36}")))
        require(deviceIdentifier.matches(Regex("[0-9a-fA-F-]{36}")))
        val query = url.newBuilder()
            .addQueryParameter("select", "id")
            .addQueryParameter("customer_id", "eq.$customerId")
            .addQueryParameter("device_identifier", "eq.$deviceIdentifier")
            .addQueryParameter("status", "eq.active")
            .addQueryParameter("limit", "1").build()
        val request = Request.Builder().url(query)
            .header("apikey", publishableKey)
            .header("Authorization", "Bearer ${session.accessToken}").get().build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(Result.failure(IOException("Falha na verificação do dispositivo")))
            }
            override fun onResponse(call: Call, response: Response) {
                val result = response.use {
                    runCatching {
                        check(it.isSuccessful) { "Falha na verificação (HTTP ${it.code})" }
                        JSONArray(it.body?.string().orEmpty()).length() > 0
                    }
                }
                callback(result)
            }
        })
    }
}

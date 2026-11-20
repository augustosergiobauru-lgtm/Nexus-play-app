package app.opentv

import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/** Resolves a catalog item only after the server validates this account and installation. */
class NexusPlaybackAccess(private val projectUrl: String, private val publishableKey: String) {
    private val client = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false).build()

    fun resolve(session: NexusAuth.Session, channelId: String, deviceIdentifier: String,
                callback: (Result<String>) -> Unit): Call {
        val endpoint = (projectUrl.trimEnd('/') + "/rest/v1/rpc/resolve_nexus_playback").toHttpUrl()
        require(endpoint.isHttps)
        val body = JSONObject().put("p_channel_id", channelId)
            .put("p_device_identifier", deviceIdentifier).toString()
            .toRequestBody("application/json".toMediaType())
        val request = Request.Builder().url(endpoint)
            .header("apikey", publishableKey)
            .header("Authorization", "Bearer ${session.accessToken}")
            .post(body).build()
        val call = client.newCall(request)
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(Result.failure(IOException("Não foi possível autorizar a reprodução.")))
            }
            override fun onResponse(call: Call, response: Response) {
                val result = response.use {
                    runCatching {
                        check(it.isSuccessful) {
                            if (it.code == 401 || it.code == 403) "Assinatura ou aparelho sem autorização."
                            else "Conteúdo indisponível (HTTP ${it.code})."
                        }
                        val rows = JSONArray(it.body?.string().orEmpty())
                        check(rows.length() == 1) { "Conteúdo indisponível." }
                        val value = rows.getJSONObject(0).getString("access_url")
                        val stream = value.toHttpUrl()
                        check(stream.scheme in setOf("http", "https")) { "Formato de endereço inválido." }
                        value
                    }
                }
                callback(result)
            }
        })
        return call
    }
}

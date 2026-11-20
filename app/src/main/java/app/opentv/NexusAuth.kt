package app.opentv

import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException

/** Authentication transport. The caller must enforce authorization server-side. */
class NexusAuth(private val projectUrl: String, private val publishableKey: String) {
    private val client = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false).build()

    class Session(val accessToken: String, val refreshToken: String, val userId: String)

    fun signIn(email: String, password: String, callback: (Result<Session>) -> Unit) {
        val body = JSONObject().put("email", email.trim()).put("password", password)
        requestToken("password", body, callback)
    }

    fun refresh(refreshToken: String, callback: (Result<Session>) -> Unit) {
        requestToken("refresh_token", JSONObject().put("refresh_token", refreshToken), callback)
    }

    private fun requestToken(grant: String, body: JSONObject, callback: (Result<Session>) -> Unit) {
        val base = projectUrl.trimEnd('/')
        require(base.startsWith("https://")) { "HTTPS required" }
        val request = Request.Builder()
            .url("$base/auth/v1/token?grant_type=$grant")
            .header("apikey", publishableKey)
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(Result.failure(IOException("Falha de conexão")))
            }

            override fun onResponse(call: Call, response: Response) {
                val result = response.use {
                    runCatching {
                        check(it.isSuccessful) { "Falha de autenticação (HTTP ${it.code})" }
                        val data = JSONObject(it.body?.string().orEmpty())
                        val session = Session(
                            data.getString("access_token"),
                            data.getString("refresh_token"),
                            data.getJSONObject("user").getString("id")
                        )
                        check(session.accessToken.isNotBlank() && session.refreshToken.isNotBlank())
                        session
                    }
                }
                callback(result)
            }
        })
    }
}

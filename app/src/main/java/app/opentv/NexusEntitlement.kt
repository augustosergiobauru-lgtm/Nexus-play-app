package app.opentv

import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONArray
import java.io.IOException

/** UI eligibility precheck only. Server RLS remains authoritative for catalog access. */
class NexusEntitlement(private val projectUrl: String, private val publishableKey: String) {
    private val client = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false).build()

    fun check(session: NexusAuth.Session, callback: (Result<Boolean>) -> Unit) {
        val base = projectUrl.trimEnd('/')
        require(base.startsWith("https://"))
        val url = "$base/rest/v1/customers".toHttpUrl().newBuilder()
            .addQueryParameter("select", "id,subscriptions!inner(id)")
            .addQueryParameter("auth_user_id", "eq.${session.userId}")
            .addQueryParameter("status", "eq.active")
            .addQueryParameter("subscriptions.status", "eq.active")
            .addQueryParameter("subscriptions.starts_at", "lte.now")
            .addQueryParameter("subscriptions.expires_at", "gt.now")
            .addQueryParameter("limit", "1")
            .build()
        val request = Request.Builder()
            .url(url)
            .header("apikey", publishableKey)
            .header("Authorization", "Bearer ${session.accessToken}")
            .get().build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(Result.failure(IOException("Falha na validação")))
            }

            override fun onResponse(call: Call, response: Response) {
                val result = response.use {
                    runCatching {
                        check(it.isSuccessful) { "Falha na validação (HTTP ${it.code})" }
                        JSONArray(it.body?.string().orEmpty()).length() > 0
                    }
                }
                callback(result)
            }
        })
    }
}

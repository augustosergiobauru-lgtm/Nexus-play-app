package app.opentv

import okhttp3.*
import org.json.JSONArray
import java.io.IOException
import java.time.Instant

/** UI eligibility precheck only. Server RLS remains authoritative for catalog access. */
class NexusEntitlement(private val projectUrl: String, private val publishableKey: String) {
    private val client = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false).build()

    fun check(session: NexusAuth.Session, callback: (Result<Boolean>) -> Unit) {
        val base = projectUrl.trimEnd('/')
        require(base.startsWith("https://"))
        val request = Request.Builder()
            .url("$base/rest/v1/customers?select=id,subscriptions(status,starts_at,expires_at)&auth_user_id=eq.${session.userId}&status=eq.active")
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
                        val customers = JSONArray(it.body?.string().orEmpty())
                        val now = Instant.now()
                        (0 until customers.length()).any { i ->
                            val subscriptions = customers.getJSONObject(i).getJSONArray("subscriptions")
                            (0 until subscriptions.length()).any { j ->
                                val subscription = subscriptions.getJSONObject(j)
                                subscription.optString("status") == "active" &&
                                    runCatching {
                                        val start = Instant.parse(subscription.getString("starts_at"))
                                        val end = Instant.parse(subscription.getString("expires_at"))
                                        !start.isAfter(now) && end.isAfter(now)
                                    }.getOrDefault(false)
                            }
                        }
                    }
                }
                callback(result)
            }
        })
    }
}


package app.opentv

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

object NexusSupabase {

    private const val SUPABASE_URL =
        "https://ypjajibbcyopkfrenibb.supabase.co"

    private const val SUPABASE_ANON_KEY =
        "sb_publishable_iU6tvWzwe5Ib0z6kOnyx6w_St2a663h"

    fun catalog() = NexusCatalog(SUPABASE_URL, SUPABASE_ANON_KEY)

    fun auth() = NexusAuth(SUPABASE_URL, SUPABASE_ANON_KEY)

    fun entitlement() = NexusEntitlement(SUPABASE_URL, SUPABASE_ANON_KEY)

    fun accessFlow() = NexusAccessFlow(
        NexusEntitlement(SUPABASE_URL, SUPABASE_ANON_KEY),
        NexusDeviceAccess(SUPABASE_URL, SUPABASE_ANON_KEY)
    )

    fun checkOwnDevice(session: NexusAuth.Session, deviceId: String, callback: (Result<Boolean>) -> Unit) {
        try {
            val url = okhttp3.HttpUrl.Builder()
                .scheme("https").host("ypjajibbcyopkfrenibb.supabase.co")
                .addPathSegments("rest/v1/customers")
                .addQueryParameter("select", "id,devices!inner(id)")
                .addQueryParameter("auth_user_id", "eq.${session.userId}")
                .addQueryParameter("status", "eq.active")
                .addQueryParameter("devices.device_identifier", "eq.$deviceId")
                .addQueryParameter("devices.status", "eq.active")
                .addQueryParameter("limit", "1").build()
            val request = Request.Builder().url(url)
                .header("apikey", SUPABASE_ANON_KEY)
                .header("Authorization", "Bearer ${session.accessToken}").get().build()
            client.newCall(request).enqueue(object : okhttp3.Callback {
                override fun onFailure(call: okhttp3.Call, e: IOException) {
                    callback(Result.failure(IOException("Falha na conexão")))
                }
                override fun onResponse(call: okhttp3.Call, response: Response) {
                    val result = response.use {
                        runCatching {
                            check(it.isSuccessful)
                            org.json.JSONArray(it.body?.string().orEmpty()).length() > 0
                        }
                    }
                    callback(result)
                }
            })
        } catch (e: Exception) {
            callback(Result.failure(e))
        }
    }

    private val client = OkHttpClient()

    fun get(
        table: String,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        val url = "$SUPABASE_URL/rest/v1/$table?select=*"

        val request = Request.Builder()
            .url(url)
            .addHeader("apikey", SUPABASE_ANON_KEY)
            .get()
            .build()

        client.newCall(request).enqueue(
            object : okhttp3.Callback {

                override fun onFailure(
                    call: okhttp3.Call,
                    e: IOException
                ) {
                    onError(e.message ?: "Erro de conexão")
                }

                override fun onResponse(
                    call: okhttp3.Call,
                    response: Response
                ) {
                    response.use {
                        val body = it.body?.string().orEmpty()

                        if (it.isSuccessful) {
                            onSuccess(body)
                        } else {
                            onError(
                                "HTTP ${it.code}: $body"
                            )
                        }
                    }
                }
            }
        )
    }
}

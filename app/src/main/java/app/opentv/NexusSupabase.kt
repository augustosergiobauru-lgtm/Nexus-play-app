
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

    fun auth() = NexusAuth(SUPABASE_URL, SUPABASE_ANON_KEY)

    fun accessFlow() = NexusAccessFlow(
        NexusEntitlement(SUPABASE_URL, SUPABASE_ANON_KEY),
        NexusDeviceAccess(SUPABASE_URL, SUPABASE_ANON_KEY)
    )

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

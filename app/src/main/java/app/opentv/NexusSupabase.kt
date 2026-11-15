
package app.opentv

import okhttp3.OkHttpClient
import okhttp3.Request
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object NexusSupabase {

    // Configuraremos os valores reais depois.
    private const val SUPABASE_URL = ""
    private const val SUPABASE_ANON_KEY = ""

    private val client = OkHttpClient()

    suspend fun testarConexao(): Boolean =
        withContext(Dispatchers.IO) {
            if (SUPABASE_URL.isBlank() ||
                SUPABASE_ANON_KEY.isBlank()
            ) {
                return@withContext false
            }

            try {
                val request = Request.Builder()
                    .url(
                        "${SUPABASE_URL.trimEnd('/')}/rest/v1/"
                    )
                    .header("apikey", SUPABASE_ANON_KEY)
                    .get()
                    .build()

                client.newCall(request).execute().use {
                    it.isSuccessful
                }
            } catch (e: Exception) {
                false
            }
        }
}

package app.opentv

import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONArray
import java.io.IOException

/** Metadata only; playback URLs are intentionally excluded. */
class NexusCatalog(private val projectUrl: String, private val publishableKey: String) {
    data class Item(val id: String, val title: String, val type: String, val categoryId: String?)
    private val client = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false).build()

    data class Category(val id: String, val name: String)
    fun liveCategories(session: NexusAuth.Session, callback: (Result<List<Category>>) -> Unit) {
        val url = (projectUrl.trimEnd('/') + "/rest/v1/categories").toHttpUrl().newBuilder()
            .addQueryParameter("select", "id,name")
            .addQueryParameter("content_type", "eq.live")
            .addQueryParameter("order", "name.asc")
            .addQueryParameter("limit", "1000").build()
        val request = Request.Builder().url(url).header("apikey", publishableKey)
            .header("Authorization", "Bearer ${session.accessToken}").build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { callback(Result.failure(e)) }
            override fun onResponse(call: Call, response: Response) {
                callback(response.use { runCatching {
                    check(it.isSuccessful) { "Falha ao carregar categorias" }
                    val rows = JSONArray(it.body?.string().orEmpty())
                    (0 until rows.length()).map { i ->
                        val row = rows.getJSONObject(i)
                        Category(row.getString("id"), row.getString("name"))
                    }.sortedWith(compareBy<Category> { it.name.contains("24", true) }.thenBy { it.name })
                } })
            }
        })
    }

    fun page(session: NexusAuth.Session, offset: Int = 0, contentType: String? = null, query: String = "", categoryId: String? = null, callback: (Result<List<Item>>) -> Unit) {
        try {
            require(offset >= 0)
            val base = (projectUrl.trimEnd('/') + "/rest/v1/channels").toHttpUrl()
            require(base.isHttps)
            require(contentType == null || contentType in setOf("live", "movie", "series"))
            val builder = base.newBuilder()
                .addQueryParameter("select", "id,title,content_type,category_id")
                .addQueryParameter("is_active", "eq.true")
                .addQueryParameter("order", "title.asc,id.asc")
                .addQueryParameter("limit", "100")
                .addQueryParameter("offset", offset.toString())
            categoryId?.let { builder.addQueryParameter("category_id", "eq.$it") }
            contentType?.let { builder.addQueryParameter("content_type", "eq.$it") }
            val search = query.trim().take(100).replace("*", "").replace("%", "").replace("_", "")
            if (search.isNotEmpty()) builder.addQueryParameter("title", "ilike.%$search%")
            val url = builder.build()
            val request = Request.Builder().url(url)
                .header("apikey", publishableKey)
                .header("Authorization", "Bearer ${session.accessToken}").get().build()
            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    callback(Result.failure(IOException("Falha ao consultar catálogo")))
                }
                override fun onResponse(call: Call, response: Response) {
                    val result = response.use {
                        runCatching {
                            check(it.isSuccessful) { "Falha no catálogo (HTTP ${it.code})" }
                            val rows = JSONArray(it.body?.string().orEmpty())
                            (0 until rows.length()).map { index ->
                                val row = rows.getJSONObject(index)
                                Item(row.getString("id"), row.getString("title"),
                                    row.getString("content_type"),
                                    if (row.isNull("category_id")) null else row.getString("category_id"))
                            }
                        }
                    }
                    callback(result)
                }
            })
        } catch (e: Exception) {
            callback(Result.failure(e))
        }
    }
}

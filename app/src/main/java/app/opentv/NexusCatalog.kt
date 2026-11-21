package app.opentv

import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONArray
import java.io.IOException

/** Metadata only; playback URLs are intentionally excluded. */
class NexusCatalog(
    private val projectUrl: String,
    private val publishableKey: String,
    private val client: OkHttpClient = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false).build()
) {
    data class Item(val id: String, val title: String, val type: String, val categoryId: String?, val logoUrl: String? = null, val synopsis: String? = null, val adult: Boolean = false)

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

    fun page(session: NexusAuth.Session, offset: Int = 0, contentType: String? = null, query: String = "", categoryId: String? = null, recentlyAdded: Boolean = false, pageSize: Int = 100, sortOrder: String = "az", callback: (Result<List<Item>>) -> Unit) {
        try {
            require(offset >= 0 && pageSize in 1..100)
            val base = (projectUrl.trimEnd('/') + "/rest/v1/channels").toHttpUrl()
            require(base.isHttps)
            require(contentType == null || contentType in setOf("live", "movie", "series"))
            val builder = base.newBuilder()
                .addQueryParameter("select", "id,title,content_type,category_id,logo_url,categories(name)")
                .addQueryParameter("is_active", "eq.true")
                .addQueryParameter("order", if (recentlyAdded) "created_at.desc,id.desc" else when (sortOrder) { "default" -> "source_position.asc.nullslast,created_at.asc,id.asc"; "za" -> "title.desc,id.desc"; else -> "title.asc,id.asc" })
                .addQueryParameter("limit", pageSize.toString())
                .addQueryParameter("offset", offset.toString())
            categoryId?.let { builder.addQueryParameter("category_id", "eq.$it") }
            contentType?.let { builder.addQueryParameter("content_kind", "eq.$it") }
            if (recentlyAdded && contentType == "series") {
                // Home shows one entry per show, rather than dozens of episodes of the same show.
                builder.addQueryParameter("title", "imatch. S0?1E0?1([^0-9]|$)")
            }
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
                                    if (row.isNull("category_id")) null else row.getString("category_id"),
                                    row.optString("logo_url").takeIf { it.isNotBlank() && it != "null" },
                                    listOf("synopsis", "overview", "description", "plot").firstNotNullOfOrNull { key ->
                                        row.optString(key).takeIf { it.isNotBlank() && it != "null" }
                                    }, NexusContentPolicy.isAdult(row.getString("title"), row.optJSONObject("categories")?.optString("name").orEmpty()))
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

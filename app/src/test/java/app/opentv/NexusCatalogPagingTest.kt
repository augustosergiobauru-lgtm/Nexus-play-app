package app.opentv

import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class NexusCatalogPagingTest {
    private val session = NexusAuth.Session("fixture", "", "fixture-user")

    @Test fun returnsFirstSeriesPageWithoutDownloadingRemainingEpisodes() {
        val requests = AtomicInteger(0)
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            requests.incrementAndGet()
            val request = chain.request()
            assertEquals("100", request.url.queryParameter("limit"))
            val offset = request.url.queryParameter("offset")!!.toInt()
            val rows = JSONArray()
            repeat(if (offset == 0) 100 else 1) { i ->
                rows.put(JSONObject().put("id", "ep-${offset+i}")
                    .put("title", "Show S01E${offset+i+1}").put("content_type", "series"))
            }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(rows.toString().toResponseBody()).build()
        }.build()
        val catalog = NexusCatalog("https://catalog.test", "fixture", client)
        fun page(offset: Int): List<NexusCatalog.Item> {
            val latch = CountDownLatch(1)
            var result: Result<List<NexusCatalog.Item>>? = null
            catalog.page(session, offset, contentType = "series") { result = it; latch.countDown() }
            assertTrue(latch.await(10, TimeUnit.SECONDS))
            return result!!.getOrThrow()
        }
        val first = page(0)
        assertEquals(100, first.size)
        assertEquals(1, requests.get())
        assertEquals("Show", NexusSeries.shows(first).single().title)
        val next = page(first.size)
        assertEquals(2, requests.get())
        assertEquals(101, NexusSeries.episodes(first + next, "Show").size)
    }

    @Test fun seriesBrowseUsesCompactShowCatalog() {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            assertEquals("/rest/v1/series_catalog", request.url.encodedPath)
            assertEquals("100", request.url.queryParameter("limit"))
            assertEquals("eq.series", request.url.queryParameter("content_kind"))
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body("""[{"id":"playlist:Show","title":"Show","content_type":"series"}]""".toResponseBody()).build()
        }.build()
        val latch = CountDownLatch(1)
        var result: Result<List<NexusCatalog.Item>>? = null
        NexusCatalog("https://catalog.test", "fixture", client).page(session, contentType = "series", seriesShows = true) {
            result = it; latch.countDown()
        }
        assertTrue(latch.await(10, TimeUnit.SECONDS))
        assertEquals("Show", result!!.getOrThrow().single().title)
    }

    @Test fun episodeSearchIsScopedToSelectedShowAndKeepsPlayableIds() {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            assertEquals("eq.series", request.url.queryParameter("content_kind"))
            assertEquals("eq.A Sete Palmos", request.url.queryParameter("series_title"))
            assertEquals("ilike.%A Sete Palmos%", request.url.queryParameter("title"))
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body("""[{"id":"original-episode-id","title":"A Sete Palmos S02E03","content_type":"series"}]""".toResponseBody()).build()
        }.build()
        val catalog = NexusCatalog("https://catalog.test", "fixture", client)
        val latch = CountDownLatch(1)
        var result: Result<List<NexusCatalog.Item>>? = null
        catalog.page(session, contentType = "series", query = "A Sete Palmos", seriesTitle = "A Sete Palmos") { result = it; latch.countDown() }
        assertTrue(latch.await(10, TimeUnit.SECONDS))
        val episode = NexusSeries.episodes(result!!.getOrThrow(), "A Sete Palmos").single()
        assertEquals("original-episode-id", episode.item.id)
        assertEquals(2, episode.season)
        assertEquals(3, episode.number)
    }
    @Test fun popularSeriesPreservePaginationFiltersAndPreferShowPoster() {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            assertEquals("/rest/v1/series_catalog", request.url.encodedPath)
            assertEquals("popularity_score.desc.nullslast,title.asc,id.asc", request.url.queryParameter("order"))
            assertEquals("100", request.url.queryParameter("offset"))
            assertEquals("eq.category-fixture", request.url.queryParameter("category_id"))
            assertEquals("ilike.%Show%", request.url.queryParameter("title"))
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body("""[{"id":"playlist:Show","title":"Show","content_type":"series","poster_url":"https://images.test/show.jpg","logo_url":"https://images.test/episode.jpg"}]""".toResponseBody()).build()
        }.build()
        val latch = CountDownLatch(1)
        var result: Result<List<NexusCatalog.Item>>? = null
        NexusCatalog("https://catalog.test", "fixture", client).page(session, offset = 100,
            contentType = "series", seriesShows = true, sortOrder = "popular", categoryId = "category-fixture", query = "Show") {
            result = it; latch.countDown()
        }
        assertTrue(latch.await(10, TimeUnit.SECONDS))
        assertEquals("https://images.test/show.jpg", result!!.getOrThrow().single().logoUrl)
    }
}

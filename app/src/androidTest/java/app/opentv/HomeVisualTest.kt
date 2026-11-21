package app.opentv

import android.content.pm.ActivityInfo
import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Real catalog metadata; no tokens, provider credentials or playable URLs in fixtures. */
@RunWith(AndroidJUnit4::class)
class HomeVisualTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test fun landscapeHomeLoadsPostersAndSwipesToSeries() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val fixture = JSONObject(instrumentation.context.assets.open("home-catalog.json").bufferedReader().use { it.readText() })
        val movie = fixture.getJSONArray("movies").getJSONObject(0)
        val series = fixture.getJSONArray("series").getJSONObject(0)
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val response = if (request.url.encodedPath.endsWith("/categories")) "[]" else {
                assertEquals("id,title,content_type,category_id,logo_url,created_at,categories(name)" + if (request.url.encodedPath.endsWith("/series_catalog")) ",poster_url" else "", request.url.queryParameter("select"))
                assertEquals("created_at.desc,id.desc", request.url.queryParameter("order"))
                assertEquals("48", request.url.queryParameter("limit"))
                val kind = request.url.queryParameter("content_kind")
                if (kind == "eq.series") {
                    assertTrue(request.url.encodedPath.endsWith("/series_catalog"))
                    fixture.getJSONArray("series").toString()
                } else {
                    assertEquals("eq.movie", kind)
                    fixture.getJSONArray("movies").toString()
                }
            }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(response.toResponseBody("application/json".toMediaType())).build()
        }.build()
        // Ensure the actual public artwork is reachable, then warm Coil's cache for the render.
        for (row in listOf(movie, series)) {
            val result = runBlocking {
                rule.activity.imageLoader.execute(ImageRequest.Builder(rule.activity).data(row.getString("logo_url")).build())
            }
            assertTrue("Catalog artwork did not load", result is SuccessResult)
        }
        rule.activityRule.scenario.onActivity { activity ->
            activity.setContent {
                NexusHomeScreen(NexusAuth.Session("fixture-only", "", "visual-fixture"), {}, NexusCatalog("https://catalog.invalid", "fixture", client))
            }
        }
        rule.waitUntil(15000) { rule.onAllNodesWithTag("home-highlights").fetchSemanticsNodes().isNotEmpty() }
        // Replace the login composition first: its disposal restores the login orientation.
        rule.activityRule.scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        rule.waitUntil(10000) {
            rule.activity.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        }
        rule.waitForIdle()
        rule.onNodeWithTag("featured-title-0").assertTextEquals(movie.getString("title"))
        rule.onNodeWithText("Conteúdo em breve").assertDoesNotExist()
        rule.onNodeWithTag("home-rail-2").assertIsDisplayed()
        rule.onNodeWithContentDescription(movie.getString("title")).assertIsDisplayed()
        rule.onAllNodesWithText("Ao Vivo").assertCountEquals(1)
        rule.waitForIdle()
        Thread.sleep(2000)
        // Public Downloads survives the test runner uninstalling the target APK.
        val screenshot = instrumentation.uiAutomation.executeShellCommand(
            "screencap -p /sdcard/Download/nexus-home-landscape.png"
        )
        android.os.ParcelFileDescriptor.AutoCloseInputStream(screenshot).use { it.readBytes() }
        rule.onNodeWithTag("home-highlights").performTouchInput { swipeLeft() }
        rule.waitForIdle()
        val cleanTitle = series.getString("title").replace(Regex("\\s+S\\d+E\\d+.*$", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s*\\[L]\\s*"), " ").trim()
        rule.onNodeWithTag("featured-title-1").assertTextEquals(cleanTitle)
    }
}

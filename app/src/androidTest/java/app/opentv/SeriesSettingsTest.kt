package app.opentv

import android.content.Context
import android.content.pm.ActivityInfo
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SeriesSettingsTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test fun seriesBrowserOpensSeasons() {
        var played: String? = null
        val episode = NexusCatalog.Item("episode-fixture", "Série de teste S01E01", "episode", null)
        rule.activityRule.scenario.onActivity { activity ->
            activity.setContent {
                NexusMoviesScreen(contentLabel = "SÉRIES", categories = emptyList(), categoryId = null,
                    onCategory = {}, items = listOf(episode), query = "", onQuery = {}, sort = "default", onSort = {},
                    favoritesOnly = false, onFavorites = {}, favorites = emptySet(), onFavorite = {},
                    loading = false, more = false, error = null, busy = false, onMore = {},
                    onPlay = { played = it.id }, onBack = {})
            }
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
        rule.waitUntil(10000) { rule.activity.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE }
        rule.waitForIdle()
        rule.onNodeWithText("SÉRIES").assertIsDisplayed()
        rule.onNodeWithText("FAVORITOS").assertIsDisplayed()
        rule.onNodeWithText("Ordem: Padrão").assertIsDisplayed()
        rule.onAllNodesWithContentDescription(episode.title).onFirst().performClick()
        rule.onNodeWithText("Temporadas e episódios").performScrollTo().assertIsDisplayed().performClick()
        rule.runOnIdle { assertEquals(episode.id, played) }
    }

    @Test fun seriesDetailSelectsSeasonPlaysOriginalEpisodeAndReturns() {
        var played: String? = null
        var returned = false
        val rows = listOf(
            NexusCatalog.Item("ep-1", "Minha Série S01E01", "series", null),
            NexusCatalog.Item("ep-2", "Minha Série S02E03", "series", null)
        )
        rule.activityRule.scenario.onActivity { activity ->
            activity.setContent {
                NexusSeriesDetailScreen(NexusSeries.shows(rows).single(), NexusSeries.episodes(rows, "Minha Série"),
                    loading = false, busy = false, error = null,
                    onBack = { returned = true }, onPlay = { played = it.id }, onRetry = {})
            }
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
        rule.onNodeWithText("Episódio 1").assertIsDisplayed()
        rule.onNodeWithText("Temporada 1 ▾").performClick()
        rule.onNodeWithText("Temporada 2").performClick()
        rule.onNodeWithText("Episódio 1").assertDoesNotExist()
        rule.onNodeWithText("Episódio 3").performClick()
        rule.runOnIdle { assertEquals("ep-2", played) }
        rule.onNodeWithContentDescription("Voltar às séries").performClick()
        rule.runOnIdle { assertTrue(returned) }
    }

    @Test fun clearCachePreservesFavoritesAndAccountPreferences() {
        val favorites = rule.activity.getSharedPreferences("nexus_favorites_settings-fixture", Context.MODE_PRIVATE)
        val options = rule.activity.getSharedPreferences("nexus_playback_options", Context.MODE_PRIVATE)
        favorites.edit().putStringSet("ids", setOf("saved-film")).commit()
        options.edit().putBoolean("resume", false).commit()
        rule.activityRule.scenario.onActivity { activity ->
            activity.setContent {
                MaterialTheme {
                    NexusSettingsScreen(NexusAuth.Session("fixture-only", "", "settings-fixture"), {}, {}, {})
                }
            }
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
        rule.onNodeWithText("Armazenamento").performScrollTo().performClick()
        rule.onNodeWithText("Apagar cache").performClick()
        rule.onNodeWithText("Apagar cache?").assertIsDisplayed()
        rule.onNodeWithText("Confirmar").performClick()
        rule.waitUntil(10000) { rule.onAllNodesWithText("Cache apagado.").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(setOf("saved-film"), favorites.getStringSet("ids", emptySet()))
        assertFalse(options.getBoolean("resume", true))
        favorites.edit().clear().commit()
        options.edit().remove("resume").commit()
    }
}

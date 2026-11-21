package app.opentv

import org.junit.Assert.*
import org.junit.Test

class NexusSeriesTest {
    private fun episode(id: String, title: String, logo: String? = null) = NexusCatalog.Item(id, title, "episode", null, logo)
    @Test fun groupsSeasonsAndEpisodesWithoutMergingDifferentShows() {
        val rows = listOf(episode("3", "A Sete Palmos S02E03"), episode("1", "A Sete Palmos S01E01", "poster"),
            episode("2", "A Sete Palmos S01E02"), episode("4", "Breaking Bad S01E01"))
        val shows = NexusSeries.shows(rows)
        assertEquals(listOf("A Sete Palmos", "Breaking Bad"), shows.map { it.title })
        assertEquals("poster", shows.first().logoUrl)
        assertEquals(listOf("1", "2", "3"), NexusSeries.episodes(rows, "A Sete Palmos").map { it.item.id })
        assertEquals(listOf(1, 1, 2), NexusSeries.episodes(rows, "A Sete Palmos").map { it.season })
    }
    @Test fun acceptsProviderFormatsAndPreservesUnnumberedTitles() {
        listOf("Show S03 E012", "Show 3x12", "Show Temporada 3 Episódio 12", "Show T03E12").forEach { title ->
            val parsed = NexusSeries.episodes(listOf(episode("original", title)), "Show").single()
            assertEquals(3, parsed.season)
            assertEquals(12, parsed.number)
            assertEquals("original", parsed.item.id)
        }
        assertEquals("1899", NexusSeries.title("1899"))
        assertEquals("Show (2024)", NexusSeries.title("Show (2024) S01E01"))
    }
}

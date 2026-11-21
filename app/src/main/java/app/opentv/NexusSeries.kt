package app.opentv

import java.util.Locale

/** Provider M3U episodes carry their season/episode in the title. Keep original IDs for playback. */
object NexusSeries {
    data class Episode(val item: NexusCatalog.Item, val season: Int, val number: Int)
    private val markers = listOf(
        Regex("""(?i)\bS(\d{1,3})\s*[-._ ]*E(?:P)?\s*(\d{1,4})\b"""),
        Regex("""(?i)\b(\d{1,3})x(\d{1,4})\b"""),
        Regex("""(?i)\b(?:temporada|temp\.?|T)\s*(\d{1,3})\s*[-._ ]*(?:epis[oó]dio|ep\.?|E)\s*(\d{1,4})\b""")
    )
    private fun match(title: String) = markers.firstNotNullOfOrNull { it.find(title) }
    fun title(title: String): String {
        val marker = match(title) ?: return title.trim()
        return title.substring(0, marker.range.first).trim().trimEnd('-', '|', ':', '.', ' ').ifBlank { title.trim() }
    }
    fun key(rawTitle: String) = title(rawTitle).lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")
    fun shows(items: List<NexusCatalog.Item>, categoryId: String? = null): List<NexusCatalog.Item> = items.groupBy { key(it.title) }.values
        .filter { episodes -> categoryId == null || episodes.any { it.categoryId == categoryId } }.map { episodes ->
        val representative = episodes.first()
        representative.copy(id = "series:" + key(representative.title), title = title(representative.title), type = "series",
            logoUrl = episodes.firstOrNull { !it.logoUrl.isNullOrBlank() }?.logoUrl,
            createdAt = episodes.mapNotNull { it.createdAt }.maxOrNull())
    }
    fun episodes(items: List<NexusCatalog.Item>, showTitle: String): List<Episode> = items
        .filter { key(it.title) == key(showTitle) }.distinctBy { it.id }.map { item ->
            val marker = match(item.title)
            Episode(item, marker?.groupValues?.get(1)?.toIntOrNull() ?: 1,
                marker?.groupValues?.get(2)?.toIntOrNull() ?: 0)
        }.sortedWith(compareBy<Episode> { it.season }.thenBy { it.number }.thenBy { it.item.title })
}

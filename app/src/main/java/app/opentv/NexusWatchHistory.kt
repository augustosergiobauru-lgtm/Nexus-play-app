package app.opentv

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Call record only after playback has actually started. */
class NexusWatchHistory(context: Context, userId: String) {
    data class Entry(val id: String, val title: String, val positionMs: Long, val updatedAt: Long)
    private val prefs = context.applicationContext.getSharedPreferences("nexus_history_$userId", Context.MODE_PRIVATE)

    @Synchronized
    fun recent(): List<Entry> = runCatching {
        val rows = JSONArray(prefs.getString("entries", "[]") ?: "[]")
        (0 until rows.length()).map { index ->
            val row = rows.getJSONObject(index)
            Entry(row.getString("id"), row.getString("title"), row.getLong("positionMs"), row.getLong("updatedAt"))
        }
    }.getOrDefault(emptyList())

    @Synchronized
    fun record(id: String, title: String, positionMs: Long) {
        require(id.isNotBlank())
        val entries = (listOf(Entry(id, title, positionMs.coerceAtLeast(0), System.currentTimeMillis())) +
            recent().filterNot { it.id == id }).take(100)
        val rows = JSONArray()
        entries.forEach { entry ->
            rows.put(JSONObject().put("id", entry.id).put("title", entry.title)
                .put("positionMs", entry.positionMs).put("updatedAt", entry.updatedAt))
        }
        prefs.edit().putString("entries", rows.toString()).apply()
    }

    @Synchronized
    fun clear() { prefs.edit().remove("entries").apply() }
}

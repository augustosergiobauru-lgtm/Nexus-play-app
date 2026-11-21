package app.opentv

import java.text.Normalizer

internal object NexusContentPolicy {
    fun isAdult(title: String, category: String = ""): Boolean {
        val text = Normalizer.normalize("$category $title", Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "").lowercase(java.util.Locale.ROOT)
        return Regex("adult|porn|xxx|erotic|sexo|sex[oy]|sexy|playboy|sextreme|sexprive|venus|hustler|penthouse|brazzers|redlight|priv[eé]|hot[ _-]?(tv|club)|\\+\\s*18|18\\s*\\+|maiores de 18").containsMatchIn(text)
    }
}


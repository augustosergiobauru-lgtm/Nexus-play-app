package app.opentv

import org.junit.Assert.*
import org.junit.Test

class NexusContentPolicyTest {
    @Test fun hidesAdultCategoriesAndUnlabelledTitles() {
        for (category in listOf("• CANAIS ADULTOS (XXX)", "Filmes | [XXX] Adultos", "PRIVATE", "XXX ONLYFANS +18")) {
            assertTrue(category, NexusContentPolicy.isAdult("Canal 1 HD", category))
        }
        for (title in listOf("BR: Playboy FHD", "Sex Privé HD", "Vênus HD", "Hustler TV")) {
            assertTrue(title, NexusContentPolicy.isAdult(title))
        }
    }
    @Test fun keepsOrdinaryChannelsAndNumberedShows() {
        for (title in listOf("ESPN 3 FHD", "Disney Channel", "A Fazenda 18", "Globo SP", "Documentários")) {
            assertFalse(title, NexusContentPolicy.isAdult(title))
        }
    }
}

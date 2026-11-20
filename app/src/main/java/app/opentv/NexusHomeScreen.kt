package app.opentv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Empty-state UI preview; no catalog entitlement or playback is implied. */
@Composable
fun NexusHomeScreen(onExit: () -> Unit) {
    val gold = Color(0xFFE5C465)
    val tabs = listOf("Início", "Ao Vivo", "Filmes", "Séries", "Mais")
    var selected by remember { mutableStateOf(0) }
    MaterialTheme(colorScheme = darkColorScheme(
        primary = gold, onPrimary = Color.Black,
        background = Color(0xFF080808), surface = Color(0xFF171717)
    )) {
        Scaffold(
            containerColor = Color(0xFF080808),
            bottomBar = {
                NavigationBar(containerColor = Color(0xFF171717)) {
                    tabs.forEachIndexed { index, name ->
                        NavigationBarItem(
                            selected = selected == index,
                            onClick = { selected = index },
                            icon = { Text(listOf("⌂", "▣", "▶", "▤", "⋯")[index], fontSize = 20.sp) },
                            label = { Text(name) }
                        )
                    }
                }
            }
        ) { padding ->
            Column(
                Modifier.fillMaxSize().padding(padding).padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Text("NEXUS PLAY", color = gold, fontSize = 28.sp)
                Text("Prévia de desenvolvimento", color = Color.LightGray)
                Text(tabs[selected], style = MaterialTheme.typography.headlineMedium)
                val sections = when (selected) {
                    0 -> listOf("Destaques", "Continuar assistindo", "Ao vivo", "Filmes", "Séries")
                    1 -> listOf("Categorias", "Favoritos", "Recentes")
                    2 -> listOf("Categorias de filmes", "Favoritos")
                    3 -> listOf("Categorias de séries", "Favoritos")
                    else -> listOf("Minha conta", "Dispositivos")
                }
                sections.forEach { title ->
                    Text(title, style = MaterialTheme.typography.titleLarge, color = gold)
                    Card(Modifier.fillMaxWidth()) {
                        Text("Nenhum conteúdo disponível nesta prévia.", Modifier.padding(18.dp))
                    }
                }
                if (selected == 4) Button(onClick = onExit) { Text("Sair") }
            }
        }
    }
}

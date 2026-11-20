package app.opentv

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Development preview: deliberately does not unlock the original player. */
@Composable
fun NexusEntry() {
    val auth = remember { NexusSupabase.auth() }
    var authenticated by remember { mutableStateOf(false) }
    if (!authenticated) {
        NexusLoginRoute(auth = auth, onAuthenticated = { authenticated = true })
    } else {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Nexus Play")
            Text("Login confirmado. Validação de acesso em desenvolvimento.")
            Button(onClick = { authenticated = false }) { Text("Voltar ao login") }
        }
    }
}

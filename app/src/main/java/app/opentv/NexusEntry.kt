package app.opentv

import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/** Development preview; no playback access is granted here. */
@Composable
fun NexusEntry() {
    val context = LocalContext.current
    val auth = remember { NexusSupabase.auth() }
    val handler = remember { Handler(Looper.getMainLooper()) }
    val deviceId = remember { runCatching { NexusInstallation.identifier(context) }.getOrNull() }
    var session by remember { mutableStateOf<NexusAuth.Session?>(null) }
    val current = session
    if (current == null) {
        NexusLoginRoute(auth = auth, onAuthenticated = { session = it })
    } else {
        var message by remember(current) { mutableStateOf("Validando assinatura...") }
        DisposableEffect(current) {
            val active = java.util.concurrent.atomic.AtomicBoolean(true)
            try {
                NexusSupabase.entitlement().check(current) { result ->
                    handler.post {
                        if (active.get()) {
                            message = result.fold(
                                onSuccess = { valid ->
                                    if (!valid) "Assinatura inválida ou vencida."
                                    else if (deviceId == null) "Identificação do aparelho indisponível."
                                    else {
                                        NexusSupabase.checkOwnDevice(current, deviceId) { approval ->
                                            handler.post {
                                                if (active.get()) {
                                                    message = approval.fold(
                                                        onSuccess = { if (it) "Assinatura e aparelho aprovados. Player em desenvolvimento." else "Aparelho aguardando aprovação administrativa." },
                                                        onFailure = { "Falha ao verificar o aparelho." }
                                                    )
                                                }
                                            }
                                        }
                                        "Verificando aparelho..."
                                    }
                                },
                                onFailure = { "Não foi possível validar a assinatura." }
                            )
                        }
                    }
                }
            } catch (_: Exception) {
                message = "Não foi possível iniciar a validação."
            }
            onDispose { active.set(false) }
        }
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Nexus Play")
            Text(message)
            Text("Código da instalação: " + (deviceId ?: "Indisponível"))
            Button(onClick = { session = null }) { Text("Voltar ao login") }
        }
    }
}

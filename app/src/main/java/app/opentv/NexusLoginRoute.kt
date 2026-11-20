package app.opentv

import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.*

/** Reports authentication only; caller must validate subscription and device before granting access. */
@Composable
fun NexusLoginRoute(
    auth: NexusAuth,
    onAuthenticated: (NexusAuth.Session) -> Unit
) {
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val currentCallback by rememberUpdatedState(onAuthenticated)
    val handler = remember { Handler(Looper.getMainLooper()) }
    val active = remember { java.util.concurrent.atomic.AtomicBoolean(true) }
    DisposableEffect(Unit) {
        active.set(true)
        onDispose { active.set(false) }
    }

    NexusLoginScreen(loading = loading, error = error, onLogin = { email, password ->
        if (!loading) {
            loading = true
            error = null
            try {
                auth.signIn(email, password) { result ->
                    handler.post {
                        if (active.get()) {
                            loading = false
                            result.fold(
                                onSuccess = { currentCallback(it) },
                                onFailure = { error = "Não foi possível entrar. Confira seus dados e a conexão." }
                            )
                        }
                    }
                }
            } catch (_: Exception) {
                loading = false
                error = "Não foi possível iniciar a autenticação."
            }
        }
    })
}

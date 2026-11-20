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
    val store = remember { NexusSessionStore(context.applicationContext) }
    var restoring by remember { mutableStateOf(true) }
    var restoreError by remember { mutableStateOf(false) }
    var attempt by remember { mutableStateOf(0) }
    fun signOut() { store.clear(); session = null }
    // Keep phones upright for sign-in and switch immediately after authentication.
    // TVs always remain in landscape, including on the login screen.
    val activity = remember(context) {
        var owner = context
        while (owner is android.content.ContextWrapper && owner !is android.app.Activity) {
            owner = owner.baseContext
        }
        owner as? android.app.Activity
    }
    val orientation = if (session != null || isRunningOnTelevision(context)) {
        android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    } else {
        android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }
    SideEffect {
        if (activity != null && activity.requestedOrientation != orientation) {
            activity.requestedOrientation = orientation
        }
    }
    DisposableEffect(attempt) {
        val active = java.util.concurrent.atomic.AtomicBoolean(true)
        val token = store.read()
        if (token == null) restoring = false
        else auth.refresh(token) { result ->
            handler.post {
                if (active.get()) {
                    result.onSuccess { store.save(it.refreshToken); session = it; restoring = false }
                        .onFailure { failure ->
                            if (failure is java.io.IOException) restoreError = true
                            else { store.clear(); restoring = false }
                        }
                }
            }
        }
        onDispose { active.set(false) }
    }
    if (restoring) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            if (restoreError) {
                Text("Não foi possível conectar.")
                Button(onClick = { restoreError = false; attempt++ }) { Text("Tentar novamente") }
                TextButton(onClick = { store.clear(); restoring = false }) { Text("Voltar ao login") }
            } else {
                CircularProgressIndicator()
                Text("Conectando ao Nexus Play...")
            }
        }
        return
    }
    val current = session
    if (current == null) {
        NexusLoginRoute(auth = auth, onAuthenticated = { authenticated, rememberLogin ->
            if (rememberLogin) store.save(authenticated.refreshToken) else store.clear()
            session = authenticated
        })
    } else {
        var preview by remember(current) { mutableStateOf(false) }
        var approved by remember(current) { mutableStateOf(false) }
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
                                                    approved = approval.getOrDefault(false)
                                                    if (approved) preview = true
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
        if (preview && approved) {
            NexusHomeScreen(session = current, onExit = { signOut() })
        } else Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Nexus Play")
            Text(message)
            Text("Código da instalação: " + (deviceId ?: "Indisponível"))
            if (approved) Button(onClick = { preview = true }) { Text("Abrir prévia do Nexus Play") }
            Button(onClick = { signOut() }) { Text("Voltar ao login") }
        }
    }
}

/** Stores only the rotating refresh token, encrypted by a device-bound Android Keystore key. */
private class NexusSessionStore(context: android.content.Context) {
    private val prefs = context.getSharedPreferences("nexus_session_v1", android.content.Context.MODE_PRIVATE)
    private val alias = "nexus_session_aes_v1"
    private fun key(): javax.crypto.SecretKey {
        val ks = java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(alias, null) as? javax.crypto.SecretKey)?.let { return it }
        return javax.crypto.KeyGenerator.getInstance("AES", "AndroidKeyStore").apply {
            init(android.security.keystore.KeyGenParameterSpec.Builder(alias,
                android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or android.security.keystore.KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    fun save(token: String) {
        runCatching {
            val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, key())
            val data = cipher.doFinal(token.toByteArray(Charsets.UTF_8))
            prefs.edit().putString("iv", android.util.Base64.encodeToString(cipher.iv, android.util.Base64.NO_WRAP))
                .putString("token", android.util.Base64.encodeToString(data, android.util.Base64.NO_WRAP)).commit()
        }.onFailure { clear() }
    }
    fun read(): String? = runCatching {
        val data = prefs.getString("token", null) ?: return null
        val iv = prefs.getString("iv", null) ?: return null
        val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(javax.crypto.Cipher.DECRYPT_MODE, key(), javax.crypto.spec.GCMParameterSpec(128, android.util.Base64.decode(iv, android.util.Base64.NO_WRAP)))
        String(cipher.doFinal(android.util.Base64.decode(data, android.util.Base64.NO_WRAP)), Charsets.UTF_8)
    }.getOrElse { clear(); null }
    fun clear() { prefs.edit().clear().commit() }
}

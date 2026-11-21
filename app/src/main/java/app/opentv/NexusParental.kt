package app.opentv

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.Normalizer
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** PIN is account scoped; the unlock itself is never persisted. */
internal class NexusParental(context: Context, userId: String) {
    private val prefs = context.getSharedPreferences("nexus_parental_$userId", Context.MODE_PRIVATE)
    val configured: Boolean get() = prefs.contains("hash")
    fun create(pin: String) {
        require(!configured && pin.matches(Regex("[0-9]{4,8}")))
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        prefs.edit().putString("salt", hex(salt)).putString("hash", hex(hash(pin, salt)))
            .putInt("attempts", 0).putLong("blocked_until", 0).commit()
    }
    fun verify(pin: String): Boolean {
        if (System.currentTimeMillis() < prefs.getLong("blocked_until", 0)) return false
        if (!configured) return false
        val salt = prefs.getString("salt", "")!!.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val correct = MessageDigest.isEqual(hex(hash(pin, salt)).toByteArray(), prefs.getString("hash", "")!!.toByteArray())
        val attempts = if (correct) 0 else prefs.getInt("attempts", 0) + 1
        prefs.edit().putInt("attempts", if (attempts >= 5) 0 else attempts)
            .putLong("blocked_until", if (attempts >= 5) System.currentTimeMillis() + 60_000 else 0).commit()
        return correct
    }
    private fun hash(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, 120_000, 256)
        return try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).encoded } finally { spec.clearPassword() }
    }
    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it.toInt() and 255) }
}

@Composable
internal fun NexusParentalDialog(store: NexusParental, unlocked: Boolean, onUnlock: () -> Unit, onLock: () -> Unit, onDismiss: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val create = !store.configured
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text("Controle parental") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(if (unlocked) "Conteúdo adulto liberado nesta sessão. Ao sair do app, o bloqueio será reativado." else if (create) "Responsável: cadastre um PIN de 4 a 8 números e guarde-o. O conteúdo adulto permanece oculto até a liberação." else "Informe o PIN para liberar o conteúdo adulto nesta sessão.")
                if (!unlocked) {
                    OutlinedTextField(pin, { pin = it.filter(Char::isDigit).take(8); error = null }, label = { Text(if (create) "Criar PIN" else "PIN") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
                    if (create) OutlinedTextField(confirm, { confirm = it.filter(Char::isDigit).take(8) }, label = { Text("Confirmar PIN") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (unlocked) { onLock(); onDismiss() }
                else if (create) {
                    if (!pin.matches(Regex("[0-9]{4,8}")) || pin != confirm) error = "Use de 4 a 8 números e confirme o mesmo PIN."
                    else { store.create(pin); onDismiss() }
                } else if (store.verify(pin)) { onUnlock(); onDismiss() }
                else { error = "PIN incorreto ou bloqueado. Após 5 tentativas, aguarde 1 minuto."; pin = "" }
            }) { Text(if (unlocked) "Bloquear agora" else if (create) "Salvar PIN" else "Liberar") }
        }, dismissButton = { TextButton(onClick = onDismiss) { Text("Fechar") } })
}

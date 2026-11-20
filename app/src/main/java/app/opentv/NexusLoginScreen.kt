package app.opentv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun NexusLoginScreen(
    loading: Boolean,
    error: String?,
    onLogin: (String, String) -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val gold = Color(0xFFE5C465)
    MaterialTheme(colorScheme = darkColorScheme(primary = gold, onPrimary = Color.Black,
        background = Color(0xFF080808), surface = Color(0xFF171717))) {
        Box(Modifier.fillMaxSize().background(Color(0xFF080808)).padding(24.dp),
            contentAlignment = Alignment.Center) {
            Column(Modifier.widthIn(max = 420.dp).fillMaxWidth().verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("NEXUS PLAY", color = gold, fontSize = 32.sp)
                Text("Entre na sua conta", color = Color.White)
                OutlinedTextField(value = email, onValueChange = { email = it },
                    label = { Text("E-mail") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth(), enabled = !loading)
                OutlinedTextField(value = password, onValueChange = { password = it },
                    label = { Text("Senha") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(), enabled = !loading)
                if (error != null) Text(error, color = MaterialTheme.colorScheme.error)
                Button(onClick = { onLogin(email.trim(), password); password = "" },
                    enabled = !loading && email.isNotBlank() && password.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    if (loading) CircularProgressIndicator(Modifier.size(24.dp), color = Color.Black)
                    else Text("Entrar")
                }
            }
        }
    }
}

package app.opentv

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun NexusLoginScreen(
    loading: Boolean,
    error: String?,
    onLogin: (String, String, Boolean) -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var rememberLogin by remember { mutableStateOf(true) }
    val gold = Color(0xFFE5C465)
    MaterialTheme(colorScheme = darkColorScheme(primary = gold, onPrimary = Color.Black,
        background = Color(0xFF080808), surface = Color(0xFF171717))) {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF080808), Color(0xFF20190D), Color(0xFF080808)))).safeDrawingPadding().padding(24.dp),
            contentAlignment = Alignment.Center) {
            Column(Modifier.widthIn(max = 420.dp).fillMaxWidth().verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("▷N", color = gold, fontSize = 64.sp, fontWeight = FontWeight.Bold)
                Text("NEXUS PLAY", color = gold, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                Text("Entre na sua conta", color = Color.White)
                OutlinedTextField(value = email, onValueChange = { email = it },
                    label = { Text("E-mail") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth(), enabled = !loading)
                OutlinedTextField(value = password, onValueChange = { password = it },
                    label = { Text("Senha") }, singleLine = true,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }, enabled = !loading) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = if (passwordVisible) "Ocultar senha" else "Mostrar senha",
                                tint = gold
                            )
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(), enabled = !loading)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = rememberLogin, onCheckedChange = { rememberLogin = it }, enabled = !loading)
                    Text("Lembrar meu acesso", color = Color.White)
                }
                if (error != null) Text(error, color = MaterialTheme.colorScheme.error)
                Button(onClick = { onLogin(email.trim(), password, rememberLogin); password = ""; passwordVisible = false },
                    enabled = !loading && email.isNotBlank() && password.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = RoundedCornerShape(10.dp)) {
                    if (loading) CircularProgressIndicator(Modifier.size(24.dp), color = Color.Black)
                    else Text("Entrar")
                }
            }
        }
    }
}

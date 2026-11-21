package app.opentv

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Alignment
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val SettingsGold = Color(0xFFE5C465)

@Composable
fun NexusSettingsScreen(session: NexusAuth.Session, onParental: () -> Unit, onExit: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val sections = listOf("Meu plano", "Reprodução", "Conexão", "Controle parental", "Armazenamento", "Meu dispositivo", "Suporte")
    var section by remember { mutableStateOf("Meu plano") }
    var account by remember { mutableStateOf<org.json.JSONObject?>(null) }
    val scope = rememberCoroutineScope()
    var accountError by remember { mutableStateOf(false) }
    var refresh by remember { mutableStateOf(0) }
    var running by remember { mutableStateOf(false) }
    var diagnostic by remember { mutableStateOf("Execute o teste para verificar sua conexão.") }
    var cacheMessage by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf<String?>(null) }
    val prefs = remember { context.getSharedPreferences("nexus_playback_options", Context.MODE_PRIVATE) }
    var resume by remember { mutableStateOf(prefs.getBoolean("resume", true)) }
    LaunchedEffect(session, refresh) {
        accountError = false
        withContext(Dispatchers.IO) { runCatching {
            NexusSupabase.settingsRequest(session, "customers?select=subscriptions(status,starts_at,expires_at,plans(name)),devices(device_name,device_identifier,status)&auth_user_id=eq.${session.userId}").use {
                check(it.isSuccessful)
                JSONArray(it.body?.string().orEmpty()).optJSONObject(0)
            }
        } }.onSuccess { account = it }.onFailure { accountError = true }
    }
    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        diagnostic = "Verificando internet, servidor e velocidade…"
        diagnostic = withContext(Dispatchers.IO) {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val caps = cm.getNetworkCapabilities(cm.activeNetwork)
            if (caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) != true) "Internet indisponível ou sem acesso validado. Confira o Wi-Fi ou os dados móveis."
            else {
                val content = StringBuilder()
                val server = runCatching {
                    val start = System.nanoTime()
                    NexusSupabase.settingsRequest(session, "customers?select=id&auth_user_id=eq.${session.userId}").use { check(it.isSuccessful); it.body?.string() }
                    (System.nanoTime() - start) / 1_000_000
                }
                val speed = runCatching {
                    val start = System.nanoTime()
                    NexusSupabase.diagnosticClient.newCall(Request.Builder().url("https://speed.cloudflare.com/__down?bytes=5000000").header("Cache-Control", "no-cache").build()).execute().use { response ->
                        check(response.isSuccessful)
                        val input = response.body!!.byteStream()
                        val buffer = ByteArray(16384)
                        var total = 0L
                        while (total < 5_000_000) { val n = input.read(buffer); if (n < 0) break; total += n }
                        check(total == 5_000_000L)
                        total * 8.0 / ((System.nanoTime() - start) / 1e9) / 1e6
                    }
                }
                for ((kind, label) in listOf("live" to "Canais", "movie" to "Filmes", "series" to "Séries")) {
                    val result = runCatching {
                        val rows = NexusSupabase.settingsRequest(session, "channels?select=id,title,categories(name)&is_active=eq.true&content_kind=eq.$kind&limit=30").use { response -> check(response.isSuccessful); JSONArray(response.body?.string().orEmpty()) }
                        val sample = (0 until rows.length()).map { rows.getJSONObject(it) }.firstOrNull { !NexusContentPolicy.isAdult(it.optString("title"), it.optJSONObject("categories")?.optString("name").orEmpty()) }
                        if (sample == null) "sem amostra disponível" else {
                            val resolved = NexusSupabase.settingsRpc(session, "resolve_nexus_playback", org.json.JSONObject().put("p_channel_id", sample.getString("id")).put("p_device_identifier", NexusInstallation.identifier(context))).use { response ->
                                check(response.isSuccessful)
                                JSONArray(response.body?.string().orEmpty()).getJSONObject(0).getString("access_url")
                            }
                            // This client carries no account headers to the provider. Read only a small sample.
                            NexusSupabase.diagnosticClient.newCall(Request.Builder().url(resolved).header("Range", "bytes=0-4095").build()).execute().use { response ->
                                check(response.isSuccessful)
                                check(response.body?.contentType()?.subtype?.contains("html") != true)
                                check((response.body?.byteStream()?.read(ByteArray(4096)) ?: -1) > 0)
                            }
                            "servidor respondeu"
                        }
                    }
                    content.append("$label: ").append(result.getOrDefault("falha na amostra ou autorização")).append("\n")
                }
                buildString {
                    append("Internet conectada\n")
                    append(server.fold({ "Servidor do app: disponível • ${it} ms\n" }, { "Servidor do app: não respondeu\n" }))
                    append(content)
                    append(speed.fold({ "Download: ${String.format(Locale.US, "%.1f", it)} Mbps\n" + when { it >= 25 -> "Conexão estimada adequada para Full HD."; it >= 10 -> "Conexão estimada adequada para HD."; else -> "Conexão limitada; pode haver travamentos." } }, { "Não foi possível medir a velocidade." }))
                    append("\nTeste de até 5 MB. Uma amostra por categoria; não garante todos os títulos nem a decodificação do vídeo.")
                }
            }
        }
        running = false
    }
    fun copyDiagnostic() {
        val version = context.packageManager.getPackageInfo(context.packageName, 0).versionName
        val text = "Nexus Play $version\nAndroid ${android.os.Build.VERSION.RELEASE}\n${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}\n$diagnostic"
        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Diagnóstico Nexus Play", text))
        cacheMessage = "Diagnóstico copiado."
    }
    MaterialTheme(colorScheme = darkColorScheme(primary = SettingsGold, onPrimary = Color.Black, background = Color(0xFF080808), surface = Color(0xFF171717))) {
    Column(Modifier.fillMaxSize().background(Color(0xFF080808)).safeDrawingPadding()) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            NexusBrandMark(Modifier.size(38.dp))
            Text("CONFIGURAÇÕES", color = SettingsGold, fontSize = 18.sp, modifier = Modifier.weight(1f).padding(start = 12.dp))
            OutlinedButton(onClick = onBack) { Text("Voltar") }
        }
        Row(Modifier.weight(1f)) {
            Column(Modifier.width(175.dp).fillMaxHeight().background(Color(0xFF151515)).verticalScroll(rememberScrollState()).padding(10.dp)) {
                sections.forEach { label ->
                    Text(label, color = if (section == label) SettingsGold else Color.LightGray,
                        modifier = Modifier.fillMaxWidth().background(if (section == label) Color(0xFF332A16) else Color.Transparent, RoundedCornerShape(8.dp)).clickable { section = label; cacheMessage = "" }.padding(14.dp))
                }
                TextButton(onClick = { confirm = "exit" }) { Text("Sair da conta", color = SettingsGold) }
            }
            Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF171717))) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(section, color = SettingsGold, fontSize = 22.sp)
                when(section) {
                    "Meu plano" -> {
                        val subscriptions = account?.optJSONArray("subscriptions")
                        val all = (0 until (subscriptions?.length() ?: 0)).mapNotNull { subscriptions?.optJSONObject(it) }
                        val now = java.time.Instant.now()
                        val sub = all.filter { it.optString("status") == "active" && runCatching { OffsetDateTime.parse(it.optString("starts_at")).toInstant() <= now && OffsetDateTime.parse(it.optString("expires_at")).toInstant() > now }.getOrDefault(false) }.maxByOrNull { it.optString("expires_at") }
                            ?: all.maxByOrNull { it.optString("expires_at") }
                        if (accountError) Text("Não foi possível consultar seu plano.")
                        else if (sub == null) Text(if (account == null) "Consultando assinatura…" else "Nenhuma assinatura encontrada.")
                        else {
                            Text(sub.optJSONObject("plans")?.optString("name") ?: "Assinatura Nexus Play", color = Color.White)
                            val end = runCatching { OffsetDateTime.parse(sub.optString("expires_at")).toInstant() }.getOrNull()
                            val start = runCatching { OffsetDateTime.parse(sub.optString("starts_at")).toInstant() }.getOrNull()
                            val active = sub.optString("status") == "active" && start != null && start <= now && end != null && end > now
                            Text(if (active) "● Plano ativo" else "● Plano sem acesso ativo", color = SettingsGold)
                            Text("Início: ${settingsDate(sub.optString("starts_at"))}")
                            Text("Vencimento: ${settingsDate(sub.optString("expires_at"))}")
                            if (active && end != null) { val hours = java.time.Duration.between(now, end).toHours(); Text("Restam ${hours / 24} dias e ${hours % 24} horas") }
                            Text("Horário de Brasília", color = Color.Gray, fontSize = 12.sp)
                        }
                        OutlinedButton(onClick = { refresh++ }) { Text("Atualizar plano") }
                    }
                    "Reprodução" -> {
                        Row { Text("Continuar de onde parei", Modifier.weight(1f)); Switch(checked = resume, onCheckedChange = { resume = it; prefs.edit().putBoolean("resume", it).apply() }) }
                        Text("Áudio, legendas e velocidade ficam nas opções do player durante a reprodução.", color = Color.LightGray)
                    }
                    "Conexão" -> {
                        Text(diagnostic)
                        Button(onClick = { running = true }, enabled = !running) { Text(if (running) "Testando…" else "Testar conexão e velocidade") }
                        TextButton(onClick = { copyDiagnostic() }) { Text("Copiar diagnóstico") }
                    }
                    "Controle parental" -> {
                        Text("Proteja o acesso ao conteúdo adulto com seu PIN.")
                        Button(onClick = onParental) { Text("Gerenciar controle parental") }
                    }
                    "Armazenamento" -> {
                        Text("Limpe as imagens temporárias para liberar espaço. Login, favoritos, histórico e PIN serão preservados.")
                        OutlinedButton(onClick = { confirm = "cache" }) { Text("Apagar cache") }
                    }
                    "Meu dispositivo" -> {
                        Text("${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}", color = Color.White)
                        Text("Este aparelho • Android ${android.os.Build.VERSION.RELEASE}")
                        val id = NexusInstallation.identifier(context)
                        val devices = account?.optJSONArray("devices")
                        val registered = (0 until (devices?.length() ?: 0)).mapNotNull { devices?.optJSONObject(it) }.any { it.optString("device_identifier") == id && it.optString("status") == "active" }
                        Text(if (accountError) "Não foi possível consultar autorização." else if (account == null) "Consultando autorização…" else if (registered) "● Aparelho autorizado" else "Aparelho não vinculado", color = SettingsGold)
                        Text("1 aparelho por conta. Para trocar de aparelho, solicite a alteração ao atendimento.")
                    }
                    "Suporte" -> {
                        Text("Precisa de ajuda? Copie o diagnóstico e envie ao atendimento que forneceu sua assinatura.")
                        Button(onClick = { copyDiagnostic() }) { Text("Copiar diagnóstico") }
                        Text("Conteúdo travando: teste a conexão e reinicie o roteador.\nTela sem carregar: confira o plano e tente novamente.\nTroca de aparelho: solicite ao atendimento.")
                        Text("Versão ${context.packageManager.getPackageInfo(context.packageName, 0).versionName}", color = Color.Gray)
                    }
                }
                if (cacheMessage.isNotEmpty()) Text(cacheMessage, color = SettingsGold)
            }
        }
            }
        }
    }
    }
    confirm?.let { action -> AlertDialog(onDismissRequest = { confirm = null }, title = { Text(if (action == "cache") "Apagar cache?" else "Sair da conta?") }, text = { Text(if (action == "cache") "Somente imagens temporárias serão removidas." else "Você precisará entrar novamente para assistir.") }, confirmButton = { TextButton(onClick = {
        confirm = null
        if (action == "exit") onExit() else {
            coil.Coil.imageLoader(context).memoryCache?.clear()
            cacheMessage = "Limpando cache…"
            section = "Armazenamento"
            refresh++
            scope.launch {
                val success = withContext(Dispatchers.IO) { runCatching { coil.Coil.imageLoader(context).diskCache?.clear() }.isSuccess }
                cacheMessage = if (success) "Cache apagado." else "Não foi possível apagar o cache. Tente novamente."
            }
        }
    }) { Text("Confirmar") } }, dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancelar") } }) }
}

private fun settingsDate(value: String): String = runCatching {
    OffsetDateTime.parse(value).atZoneSameInstant(ZoneId.of("America/Sao_Paulo")).format(DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm"))
}.getOrDefault("Não informado")

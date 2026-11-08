package com.asoc.ping.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.asoc.ping.BuildConfig
import com.asoc.ping.data.mail.MailboxKind
import com.asoc.ping.domain.ai.Provenance
import com.asoc.ping.domain.ai.ProviderPreset
import com.asoc.ping.ui.components.InfoText
import com.asoc.ping.ui.components.ProvenanceBadge
import com.asoc.ping.work.PollScheduler

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: SettingsViewModel, onBack: () -> Unit) {
    val s by vm.uiState.collectAsState()
    val boxes by vm.mailboxes.collectAsState()
    val ctx = LocalContext.current
    var token by remember { mutableStateOf("") }
    var playKey by remember { mutableStateOf("") }

    Scaffold(topBar = { TopAppBar(title = { Text("Settings") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }) }) { padding ->
        LazyColumn(Modifier.padding(padding), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            s.status?.let { item { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary) } }

            item {
                Section("Accounts", "One GitHub token and one Play service account serve every project. Secrets are encrypted with the Android Keystore and sent only to their own service.") {
                    OutlinedTextField(token, { token = it }, label = { Text(if (s.hasGithubToken) "GitHub token (saved — enter to replace)" else "GitHub personal access token") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                    Button(onClick = { vm.saveGithubToken(token); token = "" }, enabled = token.isNotBlank()) { Text("Save token") }
                    OutlinedTextField(playKey, { playKey = it }, label = { Text(if (s.hasPlayKey) "Play service-account JSON (saved — paste to replace)" else "Play service-account JSON") }, minLines = 3, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                    Button(onClick = { vm.savePlayKey(playKey); playKey = "" }, enabled = playKey.isNotBlank()) { Text("Save key") }
                    OutlinedButton(onClick = vm::clearCredentials) { Text("Clear all credentials") }
                }
            }

            item {
                Section("Support mailboxes", "Mail is read-only. Use an IMAP app-password, or Gmail sign-in (read-only scope). Route mail to projects with a mail filter on each project.") {
                    boxes.forEach { m ->
                        OutlinedCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Column(Modifier.weight(1f)) {
                                        Text("✉ ${m.label}", style = MaterialTheme.typography.titleMedium)
                                        Text("${m.kind} · ${m.username} · ${m.folder}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(if (vm.hasMailSecret(m)) "Credential saved" else "No credential yet", style = MaterialTheme.typography.bodySmall)
                                    }
                                    Switch(checked = m.enabled, onCheckedChange = { vm.setMailboxEnabled(m, it) })
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (m.kind == MailboxKind.GMAIL.name) {
                                        OutlinedButton(onClick = {
                                            val url = vm.beginGmailAuth(m)
                                            runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                                        }) { Text("Sign in with Google") }
                                    }
                                    TextButton(onClick = { vm.deleteMailbox(m) }) { Text("Remove") }
                                }
                            }
                        }
                    }
                    AddMailbox(vm)
                }
            }

            item { AiSection(s, vm) }

            item {
                Section("Sync", "How often Ping checks for new reviews, issues and mail while you're online. You can always pull down on Home to sync now.") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PollScheduler.INTERVAL_CHOICES_MINUTES.forEach { m ->
                            FilterChip(selected = s.intervalMinutes == m, onClick = { vm.setInterval(m) }, label = { Text("$m min") })
                        }
                    }
                }
            }

            item {
                Section("About", null) {
                    Text("Ping ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "No telemetry, no analytics, no automatic sends. Network calls go only to the services you configure: Google Play Developer API, GitHub API, your mail server or Gmail, and the AI model provider you choose.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (BuildConfig.DEBUG) {
                        OutlinedButton(onClick = { vm.shareDiagnostics(ctx) }) { Text("Export diagnostics (debug)") }
                        InfoText("Versions, device and row counts only — no message text, no credentials.")
                    }
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, info: String?, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        info?.let { InfoText(it) }
        content()
    }
}

@Composable
private fun AddMailbox(vm: SettingsViewModel) {
    var open by remember { mutableStateOf(false) }
    if (!open) { OutlinedButton(onClick = { open = true }) { Text("Add mailbox") }; return }
    var kind by remember { mutableStateOf(MailboxKind.IMAP) }
    var label by remember { mutableStateOf("Support") }
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("993") }
    var user by remember { mutableStateOf("") }
    var folder by remember { mutableStateOf("INBOX") }
    var secret by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(kind == MailboxKind.IMAP, { kind = MailboxKind.IMAP }, { Text("IMAP") })
            FilterChip(kind == MailboxKind.GMAIL, { kind = MailboxKind.GMAIL }, { Text("Gmail") })
        }
        OutlinedTextField(label, { label = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(host, { host = it }, label = { Text(if (kind == MailboxKind.IMAP) "IMAP server, e.g. imap.example.com" else "Google OAuth client ID") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        if (kind == MailboxKind.IMAP) OutlinedTextField(port, { port = it }, label = { Text("Port") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(user, { user = it }, label = { Text(if (kind == MailboxKind.IMAP) "Username" else "Gmail address") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(folder, { folder = it }, label = { Text(if (kind == MailboxKind.IMAP) "Folder" else "Gmail label (inbox, …)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        if (kind == MailboxKind.IMAP) OutlinedTextField(secret, { secret = it }, label = { Text("App password") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.addMailbox(label, kind, host, port.toIntOrNull() ?: 993, user, folder, secret); open = false }) { Text("Add") }
            TextButton(onClick = { open = false }) { Text("Cancel") }
        }
    }
}

@Composable
private fun AiSection(s: SettingsUiState, vm: SettingsViewModel) {
    var preset by remember(s.aiPreset) { mutableStateOf(s.aiPreset) }
    var url by remember(s.aiPreset, s.aiBaseUrl) { mutableStateOf(s.aiBaseUrl.ifBlank { s.aiPreset?.baseUrl.orEmpty() }) }
    var model by remember(s.aiPreset, s.aiModel) { mutableStateOf(s.aiModel.ifBlank { s.aiPreset?.defaultModel.orEmpty() }) }
    var key by remember { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }
    Section("AI suggestions", "AI only suggests. Severity, titles, merges and reply drafts are applied only when you tap Accept. Auto-reply does not exist.") {
        Box(preset, menu, { menu = it }) { p ->
            preset = p; url = p?.baseUrl.orEmpty(); model = p?.defaultModel.orEmpty()
        }
        preset?.let { p ->
            ProvenanceBadge(if (p.local) Provenance.Local else Provenance.Cloud(p.name.lowercase()), model.ifBlank { p.defaultModel })
            OutlinedTextField(url, { url = it }, label = { Text("Base URL") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(model, { model = it }, label = { Text("Model") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            if (!p.local) OutlinedTextField(key, { key = it }, label = { Text(if (s.hasAiKey) "API key (saved — enter to replace)" else "API key") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
            InfoText(if (p.local) "Runs on your device / local network. Nothing leaves it." else "Review text is sent to ${java.net.URI(url).host ?: url} when you run an AI action. The key is sent only there.")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.saveAi(preset, url, model, key); key = "" }) { Text(if (preset == null) "Turn AI off" else "Save") }
            if (preset != null) OutlinedButton(onClick = vm::testAi, enabled = !s.busy) { Text("Test") }
            if (s.hasAiKey) TextButton(onClick = vm::clearAiKey) { Text("Clear key") }
        }
    }
}

@Composable
private fun Box(current: ProviderPreset?, expanded: Boolean, setExpanded: (Boolean) -> Unit, onPick: (ProviderPreset?) -> Unit) {
    Column {
        OutlinedButton(onClick = { setExpanded(true) }) { Text(current?.label ?: "Off — choose a provider") }
        DropdownMenu(expanded = expanded, onDismissRequest = { setExpanded(false) }) {
            DropdownMenuItem(text = { Text("Off") }, onClick = { onPick(null); setExpanded(false) })
            ProviderPreset.entries.forEach { p ->
                DropdownMenuItem(text = { Text((if (p.local) "◉ " else "◌ ") + p.label) }, onClick = { onPick(p); setExpanded(false) })
            }
        }
    }
}

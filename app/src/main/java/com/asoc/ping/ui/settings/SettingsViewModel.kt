package com.asoc.ping.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asoc.ping.data.db.entities.MailboxEntity
import com.asoc.ping.data.mail.GmailOAuth
import com.asoc.ping.data.mail.MailPollRepository
import com.asoc.ping.data.mail.MailboxKind
import com.asoc.ping.di.AppContainer
import com.asoc.ping.domain.ai.AiException
import com.asoc.ping.domain.ai.ProviderPreset
import com.asoc.ping.work.PollScheduler
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val hasGithubToken: Boolean = false,
    val hasPlayKey: Boolean = false,
    val aiPreset: ProviderPreset? = null,
    val aiBaseUrl: String = "",
    val aiModel: String = "",
    val hasAiKey: Boolean = false,
    val intervalMinutes: Int = 30,
    val busy: Boolean = false,
    val status: String? = null,
)

class SettingsViewModel(private val c: AppContainer) : ViewModel() {
    private val _ui = MutableStateFlow(load())
    val uiState: StateFlow<SettingsUiState> = _ui.asStateFlow()

    val mailboxes: StateFlow<List<MailboxEntity>> = c.database.projectDao().observeMailboxes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private fun load() = SettingsUiState(
        hasGithubToken = c.credentialStore.hasGithubToken(),
        hasPlayKey = c.credentialStore.hasPlayServiceAccountKey(),
        aiPreset = c.settingsStore.aiPreset?.let { runCatching { ProviderPreset.valueOf(it) }.getOrNull() },
        aiBaseUrl = c.settingsStore.aiBaseUrl.orEmpty(),
        aiModel = c.settingsStore.aiModel.orEmpty(),
        hasAiKey = !c.credentialStore.getSecret(AppContainer.AI_KEY).isNullOrBlank(),
        intervalMinutes = c.settingsStore.pollIntervalMinutes,
    )

    private fun say(msg: String) = _ui.update { it.copy(status = msg) }

    fun saveGithubToken(t: String) { c.credentialStore.putGithubToken(t.trim()); _ui.value = load().copy(status = "GitHub token saved (encrypted)") }
    fun savePlayKey(json: String) {
        try {
            c.playServiceAccountAuthForValidation().parseKey(json)
            c.credentialStore.putPlayServiceAccountKeyJson(json); _ui.value = load().copy(status = "Play key saved (encrypted)")
        } catch (e: Exception) { say("That doesn't look like a service-account key JSON") }
    }
    fun clearCredentials() { c.credentialStore.clearAll(); _ui.value = load().copy(status = "All credentials cleared") }

    fun setInterval(minutes: Int) {
        c.settingsStore.pollIntervalMinutes = minutes
        PollScheduler.schedule(c.appContext, minutes, replace = true)
        _ui.value = load().copy(status = "Sync every $minutes min (Android may batch it)")
    }

    // ---- mailboxes
    fun addMailbox(label: String, kind: MailboxKind, host: String, port: Int, user: String, folder: String, secret: String) {
        if (label.isBlank() || host.isBlank() || user.isBlank()) { say("Fill in name, server and username"); return }
        viewModelScope.launch {
            val id = UUID.randomUUID().toString().take(8)
            c.database.projectDao().upsertMailbox(MailboxEntity(id, label.trim(), kind.name, host.trim(), port, user.trim(), folder.ifBlank { "INBOX" }.trim(), true))
            if (secret.isNotBlank()) c.credentialStore.putSecret(MailPollRepository.secretName(id), secret)
            say("Mailbox added")
        }
    }

    fun setMailboxEnabled(m: MailboxEntity, on: Boolean) = viewModelScope.launch { c.database.projectDao().upsertMailbox(m.copy(enabled = on)) }
    fun deleteMailbox(m: MailboxEntity) = viewModelScope.launch {
        c.database.projectDao().deleteMailbox(m.id); c.credentialStore.clearSecret(MailPollRepository.secretName(m.id))
    }
    fun hasMailSecret(m: MailboxEntity) = c.credentialStore.getSecret(MailPollRepository.secretName(m.id)) != null

    /** Returns the consent URL to open in the browser. */
    fun beginGmailAuth(m: MailboxEntity): String {
        val verifier = GmailOAuth.newVerifier()
        val state = UUID.randomUUID().toString()
        c.settingsStore.pendingGmailAuth = "${m.id}|$verifier|$state"
        return GmailOAuth.authUrl(m.host, verifier, state)
    }

    fun shareDiagnostics(context: android.content.Context) {
        viewModelScope.launch { try { Diagnostics.share(context, c) } catch (e: Exception) { say("Diagnostics failed: ${e.message}") } }
    }

    // ---- AI
    fun saveAi(preset: ProviderPreset?, baseUrl: String, model: String, key: String) {
        c.settingsStore.aiPreset = preset?.name
        c.settingsStore.aiBaseUrl = baseUrl.trim().takeIf { it.isNotEmpty() && it != preset?.baseUrl }
        c.settingsStore.aiModel = model.trim().takeIf { it.isNotEmpty() && it != preset?.defaultModel }
        if (key.isNotBlank()) c.credentialStore.putSecret(AppContainer.AI_KEY, key.trim())
        _ui.value = load().copy(status = if (preset == null) "AI turned off" else "AI provider saved")
    }

    fun clearAiKey() { c.credentialStore.clearSecret(AppContainer.AI_KEY); _ui.value = load().copy(status = "AI key cleared") }

    fun testAi() {
        val p = c.currentProvider()
        if (p == null) { say("Choose a provider (and key, for cloud providers) first"); return }
        viewModelScope.launch {
            _ui.update { it.copy(busy = true, status = "Contacting ${p.displayName}…") }
            val msg = try {
                p.complete("Reply with the single word: ok", "ping", maxTokens = 8)
                "${p.displayName} responded"
            } catch (e: AiException) { e.message ?: "Failed" }
            _ui.update { it.copy(busy = false, status = msg) }
        }
    }
}

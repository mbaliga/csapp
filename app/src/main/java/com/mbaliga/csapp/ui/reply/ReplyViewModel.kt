package com.mbaliga.csapp.ui.reply

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mbaliga.csapp.data.db.entities.ReplyLogEntity
import com.mbaliga.csapp.data.db.entities.SuggestionEntity
import com.mbaliga.csapp.data.play.PlayReviewRepository
import com.mbaliga.csapp.di.AppContainer
import com.mbaliga.csapp.domain.model.Signal
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

sealed interface SendState {
    data object Idle : SendState
    data object Sending : SendState
    data object Sent : SendState
    data class Failed(val message: String) : SendState
}

data class ReplyUiState(
    val signal: Signal? = null,
    val draftText: String = "",
    val sendState: SendState = SendState.Idle,
    val log: List<ReplyLogEntity> = emptyList(),
    val aiConfigured: Boolean = false,
    val aiDraft: SuggestionEntity? = null,
    val aiBusy: Boolean = false,
    val aiError: String? = null,
) {
    val length get() = draftText.length
    val overLimit get() = length > PlayReviewRepository.MAX_REPLY_CHARS
    /** Language the review was written in (Play's `reviewerLanguage`), shown so the reply matches it. */
    val language: String? get() = signal?.metadataJson?.let {
        runCatching { Json.parseToJsonElement(it).jsonObject["lang"]?.jsonPrimitive?.content }.getOrNull()?.takeIf { l -> l.isNotBlank() }
    }
    val previouslySent: String? get() = signal?.sentReplyText
}

/**
 * Reply is a two-step, human-confirmed flow by design: editing only saves local text; nothing is
 * sent to Play until [confirmAndSend] is called, which the UI exposes only behind a dialog that
 * shows the exact text. This is the ONLY caller of `sendConfirmedReply` (SingleSendSiteTest).
 */
class ReplyViewModel(private val sourceKey: String, private val c: AppContainer) : ViewModel() {
    private val _ui = MutableStateFlow(ReplyUiState(aiConfigured = c.suggestionService.isConfigured))
    val uiState: StateFlow<ReplyUiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            c.signalRepository.observeAll().collect { all ->
                val signal = all.find { it.sourceKey == sourceKey } ?: return@collect
                _ui.update { s -> s.copy(signal = signal, draftText = s.draftText.ifBlank { signal.replyDraft ?: signal.sentReplyText.orEmpty() }) }
            }
        }
        viewModelScope.launch { c.database.governanceDao().observeReplyLog(sourceKey).collect { l -> _ui.update { it.copy(log = l) } } }
    }

    fun updateDraft(text: String) {
        _ui.update { it.copy(draftText = text, sendState = SendState.Idle) }
        viewModelScope.launch { c.signalRepository.updateReplyDraft(sourceKey, text) }
    }

    fun requestAiDraft() {
        val sig = _ui.value.signal ?: return
        viewModelScope.launch {
            _ui.update { it.copy(aiBusy = true, aiError = null) }
            try {
                val s = c.suggestionService.suggestReply(sig)
                _ui.update { it.copy(aiDraft = s) }
            } catch (e: Exception) { _ui.update { it.copy(aiError = e.message ?: "AI draft failed") } }
            _ui.update { it.copy(aiBusy = false) }
        }
    }

    fun useAiDraft() {
        val s = _ui.value.aiDraft ?: return
        val text = Json.parseToJsonElement(s.payloadJson).jsonObject["text"]?.jsonPrimitive?.content.orEmpty()
        viewModelScope.launch {
            c.suggestionService.accept(s.id) // user tapped "Use draft": writes replyDraft, does not send
            _ui.update { it.copy(draftText = text, aiDraft = null) }
        }
    }

    fun dismissAiDraft() {
        val s = _ui.value.aiDraft ?: return
        viewModelScope.launch { c.suggestionService.reject(s.id); _ui.update { it.copy(aiDraft = null) } }
    }

    /** Only ever called after the UI's explicit confirmation dialog. Sends exactly [text]. */
    fun confirmAndSend(text: String) {
        viewModelScope.launch {
            _ui.update { it.copy(sendState = SendState.Sending) }
            try {
                c.playReviewRepository.sendConfirmedReply(sourceKey, text)
                _ui.update { it.copy(sendState = SendState.Sent) }
            } catch (e: Exception) {
                _ui.update { it.copy(sendState = SendState.Failed(e.message ?: "Unknown error")) }
            }
        }
    }
}

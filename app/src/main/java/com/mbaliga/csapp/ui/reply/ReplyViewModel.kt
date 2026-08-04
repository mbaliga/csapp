package com.mbaliga.csapp.ui.reply

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mbaliga.csapp.data.play.PlayReviewRepository
import com.mbaliga.csapp.domain.model.Signal
import com.mbaliga.csapp.domain.repository.SignalRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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
)

/**
 * Reply is a two-step, human-confirmed flow by design: [updateDraft] just edits local text
 * (persisted as [com.mbaliga.csapp.domain.model.ReplyState.DRAFTED]); nothing is sent to Play
 * until the human explicitly calls [confirmAndSend], which the UI only exposes behind an
 * additional confirmation dialog.
 */
class ReplyViewModel(
    private val sourceKey: String,
    private val signalRepository: SignalRepository,
    private val playReviewRepository: PlayReviewRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReplyUiState())
    val uiState: StateFlow<ReplyUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            signalRepository.observeAll().collect { all ->
                val signal = all.find { it.sourceKey == sourceKey }
                if (signal != null) {
                    _uiState.value = _uiState.value.copy(
                        signal = signal,
                        draftText = _uiState.value.draftText.ifBlank { signal.replyDraft.orEmpty() },
                    )
                }
            }
        }
    }

    fun updateDraft(text: String) {
        _uiState.value = _uiState.value.copy(draftText = text)
        viewModelScope.launch { signalRepository.updateReplyDraft(sourceKey, text) }
    }

    /** Only ever called after the UI's explicit confirmation dialog. Sends exactly [text]. */
    fun confirmAndSend(text: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(sendState = SendState.Sending)
            try {
                playReviewRepository.sendConfirmedReply(sourceKey, text)
                _uiState.value = _uiState.value.copy(sendState = SendState.Sent)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(sendState = SendState.Failed(e.message ?: "Unknown error"))
            }
        }
    }

    class Factory(
        private val sourceKey: String,
        private val signalRepository: SignalRepository,
        private val playReviewRepository: PlayReviewRepository,
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return ReplyViewModel(sourceKey, signalRepository, playReviewRepository) as T
        }
    }
}

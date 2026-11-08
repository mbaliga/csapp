package com.asoc.ping.ui.incident

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asoc.ping.data.db.entities.SuggestionEntity
import com.asoc.ping.di.AppContainer
import com.asoc.ping.domain.model.Incident
import com.asoc.ping.domain.model.IncidentStatus
import com.asoc.ping.domain.model.Severity
import com.asoc.ping.domain.model.Signal
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class IncidentDetailUiState(
    val incident: Incident? = null,
    val signals: List<Signal> = emptyList(),
    val others: List<Incident> = emptyList(),
    val suggestions: List<SuggestionEntity> = emptyList(),
    val selectedForSplit: Set<String> = emptySet(),
    val aiConfigured: Boolean = false,
    val busy: Boolean = false,
    val message: String? = null,
    val isLoading: Boolean = true,
)

class IncidentDetailViewModel(private val incidentId: String, private val c: AppContainer) : ViewModel() {
    private val _ui = MutableStateFlow(IncidentDetailUiState())
    val uiState: StateFlow<IncidentDetailUiState> = _ui.asStateFlow()

    init {
        refresh()
        viewModelScope.launch {
            c.database.governanceDao().observePending().collect { all ->
                _ui.update { s -> s.copy(suggestions = all.filter { it.targetId == incidentId || it.targetId.split("+").contains(incidentId) }) }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            val incident = c.incidentRepository.getById(incidentId)
            val signals = if (incident != null) c.signalRepository.getByIncidentId(incidentId) else emptyList()
            val others = incident?.let { inc ->
                c.incidentRepository.getForProject(inc.projectId).filter { it.id != incidentId && it.status != IncidentStatus.MERGED }
            }.orEmpty()
            _ui.update { it.copy(incident = incident, signals = signals, others = others, aiConfigured = c.suggestionService.isConfigured, isLoading = false) }
        }
    }

    private fun act(block: suspend () -> Unit) = viewModelScope.launch {
        try { block() } catch (e: Exception) { _ui.update { it.copy(message = e.message ?: "Failed") } }
        refresh()
    }

    fun setSeverity(severity: Severity) = act { c.incidentRepository.updateSeverity(incidentId, severity) }
    fun setStatus(status: IncidentStatus) = act { c.incidentRepository.updateStatus(incidentId, status) }
    fun markRecurringOf(otherId: String) = act { c.incidentRepository.markRecurring(incidentId, otherId) }
    fun mergeWith(otherId: String) = act { c.incidentRepository.merge(listOf(incidentId, otherId)) }

    fun toggleSplit(sourceKey: String) = _ui.update { it.copy(selectedForSplit = if (sourceKey in it.selectedForSplit) it.selectedForSplit - sourceKey else it.selectedForSplit + sourceKey) }

    fun splitOut(onDone: (String?) -> Unit) {
        val keys = _ui.value.selectedForSplit.toList()
        viewModelScope.launch {
            val created = try { c.incidentRepository.split(incidentId, keys) } catch (e: Exception) { null }
            _ui.update { it.copy(selectedForSplit = emptySet()) }
            refresh()
            onDone(created?.id)
        }
    }

    fun runTriage() {
        val inc = _ui.value.incident ?: return
        viewModelScope.launch {
            _ui.update { it.copy(busy = true, message = null) }
            try { c.suggestionService.suggestTriage(inc) } catch (e: Exception) { _ui.update { it.copy(message = "AI triage failed: ${e.message}") } }
            _ui.update { it.copy(busy = false) }
        }
    }

    fun runMergeSuggestions() {
        val inc = _ui.value.incident ?: return
        viewModelScope.launch {
            _ui.update { it.copy(busy = true, message = null) }
            try {
                val n = c.suggestionService.suggestMerges(inc.projectId).size
                _ui.update { it.copy(message = if (n == 0) "No merge candidates found" else "$n merge suggestion(s) added") }
            } catch (e: Exception) { _ui.update { it.copy(message = "AI merge check failed: ${e.message}") } }
            _ui.update { it.copy(busy = false) }
        }
    }

    fun accept(id: String) = act { c.suggestionService.accept(id) }
    fun reject(id: String) = act { c.suggestionService.reject(id) }
    fun consumeMessage() = _ui.update { it.copy(message = null) }
}

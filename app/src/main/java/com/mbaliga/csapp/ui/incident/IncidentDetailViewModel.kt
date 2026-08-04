package com.mbaliga.csapp.ui.incident

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mbaliga.csapp.domain.model.Incident
import com.mbaliga.csapp.domain.model.IncidentStatus
import com.mbaliga.csapp.domain.model.Severity
import com.mbaliga.csapp.domain.model.Signal
import com.mbaliga.csapp.domain.repository.IncidentRepository
import com.mbaliga.csapp.domain.repository.SignalRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class IncidentDetailUiState(
    val incident: Incident? = null,
    val signals: List<Signal> = emptyList(),
    val isLoading: Boolean = true,
)

class IncidentDetailViewModel(
    private val incidentId: String,
    private val incidentRepository: IncidentRepository,
    private val signalRepository: SignalRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(IncidentDetailUiState())
    val uiState: StateFlow<IncidentDetailUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val incident = incidentRepository.getById(incidentId)
            val signals = if (incident != null) signalRepository.getByIncidentId(incidentId) else emptyList()
            _uiState.value = IncidentDetailUiState(incident, signals, isLoading = false)
        }
    }

    fun setSeverity(severity: Severity) {
        viewModelScope.launch {
            incidentRepository.updateSeverity(incidentId, severity)
            refresh()
        }
    }

    fun setStatus(status: IncidentStatus) {
        viewModelScope.launch {
            incidentRepository.updateStatus(incidentId, status)
            refresh()
        }
    }

    fun dismiss() {
        viewModelScope.launch {
            incidentRepository.dismiss(incidentId)
            refresh()
        }
    }

    fun markRecurringOf(otherIncidentId: String) {
        viewModelScope.launch {
            incidentRepository.markRecurring(incidentId, otherIncidentId)
            refresh()
        }
    }

    fun mergeWith(otherIncidentId: String) {
        viewModelScope.launch {
            incidentRepository.merge(listOf(incidentId, otherIncidentId))
            refresh()
        }
    }

    fun splitOut(sourceKeys: List<String>) {
        viewModelScope.launch {
            incidentRepository.split(incidentId, sourceKeys)
            refresh()
        }
    }

    class Factory(
        private val incidentId: String,
        private val incidentRepository: IncidentRepository,
        private val signalRepository: SignalRepository,
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return IncidentDetailViewModel(incidentId, incidentRepository, signalRepository) as T
        }
    }
}

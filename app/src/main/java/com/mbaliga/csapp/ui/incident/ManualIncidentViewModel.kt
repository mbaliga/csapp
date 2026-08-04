package com.mbaliga.csapp.ui.incident

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mbaliga.csapp.domain.model.Severity
import com.mbaliga.csapp.domain.repository.IncidentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ManualIncidentViewModel(private val incidentRepository: IncidentRepository) : ViewModel() {

    private val _created = MutableStateFlow<String?>(null)
    val created: StateFlow<String?> = _created.asStateFlow()

    fun create(title: String, summary: String, severity: Severity) {
        viewModelScope.launch {
            val incident = incidentRepository.createManual(title, summary, severity)
            _created.value = incident.id
        }
    }
}

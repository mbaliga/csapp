package com.mbaliga.csapp.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mbaliga.csapp.domain.model.Incident
import com.mbaliga.csapp.domain.repository.IncidentRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DashboardViewModel(private val incidentRepository: IncidentRepository) : ViewModel() {

    val incidents: StateFlow<List<Incident>> = incidentRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun runClusteringNow() {
        viewModelScope.launch { incidentRepository.runClustering() }
    }
}

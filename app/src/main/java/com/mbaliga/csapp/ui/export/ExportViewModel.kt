package com.mbaliga.csapp.ui.export

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mbaliga.csapp.data.export.IssuesManifestExporter
import com.mbaliga.csapp.domain.repository.IncidentRepository
import com.mbaliga.csapp.domain.repository.SignalRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ExportState {
    data object Idle : ExportState
    data object Exporting : ExportState
    data object Done : ExportState
    data class Failed(val message: String) : ExportState
}

class ExportViewModel(
    private val incidentRepository: IncidentRepository,
    private val signalRepository: SignalRepository,
) : ViewModel() {

    private val _incidentCount = MutableStateFlow(0)
    val incidentCount: StateFlow<Int> = _incidentCount.asStateFlow()

    private val _exportState = MutableStateFlow<ExportState>(ExportState.Idle)
    val exportState: StateFlow<ExportState> = _exportState.asStateFlow()

    init {
        viewModelScope.launch { _incidentCount.value = incidentRepository.getAll().size }
    }

    /**
     * Writes the manifest to [destination] - a [Uri] the user picked via the SAF
     * `ACTION_CREATE_DOCUMENT` picker. This is the only place a manifest file is ever written;
     * there is no silent/automatic export anywhere else in the app.
     */
    fun exportTo(destination: Uri, exporter: IssuesManifestExporter) {
        viewModelScope.launch {
            _exportState.value = ExportState.Exporting
            try {
                val incidents = incidentRepository.getAll()
                val signalsByIncident = signalRepository.getByIncidentIds(incidents.map { it.id })
                exporter.export(destination, incidents, signalsByIncident)
                _exportState.value = ExportState.Done
            } catch (e: Exception) {
                _exportState.value = ExportState.Failed(e.message ?: "Unknown error")
            }
        }
    }
}

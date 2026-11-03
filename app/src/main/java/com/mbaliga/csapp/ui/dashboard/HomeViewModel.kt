package com.mbaliga.csapp.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mbaliga.csapp.di.AppContainer
import com.mbaliga.csapp.domain.ai.SuggestionKind
import com.mbaliga.csapp.domain.ai.SuggestionState
import com.mbaliga.csapp.domain.model.Incident
import com.mbaliga.csapp.domain.model.IncidentStatus
import com.mbaliga.csapp.domain.model.Project
import com.mbaliga.csapp.domain.model.ReplyState
import com.mbaliga.csapp.domain.model.Severity
import com.mbaliga.csapp.domain.model.SignalType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SortOrder(val label: String) { UPDATED("Updated"), SEVERITY("Severity"), SIGNALS("Signals") }

data class Filters(
    val statuses: Set<IncidentStatus> = setOf(IncidentStatus.OPEN, IncidentStatus.ACKNOWLEDGED, IncidentStatus.RESOLVED),
    val severities: Set<Severity> = emptySet(),
    val channels: Set<SignalType> = emptySet(),
    val sort: SortOrder = SortOrder.UPDATED,
)

data class NeedsYou(val newSignals: Int, val pendingSuggestions: Int, val failedSends: Int) {
    val total get() = newSignals + pendingSuggestions + failedSends
}

data class UndoAction(val incidentId: String, val previous: IncidentStatus, val label: String)

data class HomeState(
    val projects: List<Project> = emptyList(),
    val selected: Project? = null,
    val incidents: List<Incident> = emptyList(),
    val severityCounts: Map<Severity, Int> = emptyMap(),
    val needsYou: NeedsYou = NeedsYou(0, 0, 0),
    val digest: String? = null,
    val digestProvenance: Pair<com.mbaliga.csapp.domain.ai.Provenance, String>? = null,
    val filters: Filters = Filters(),
    val aiConfigured: Boolean = false,
    val syncing: Boolean = false,
    val message: String? = null,
    val undo: UndoAction? = null,
)

class HomeViewModel(private val c: AppContainer) : ViewModel() {
    private val filters = MutableStateFlow(Filters())
    private val transient = MutableStateFlow(Triple(false, null as String?, null as UndoAction?))
    private val signalCountsByIncident = MutableStateFlow<Map<String, Int>>(emptyMap())

    init {
        viewModelScope.launch {
            c.projectRepository.ensureDefaults(c.settingsStore)
            val list = c.projectRepository.projects()
            val sel = c.selectedProject.value
            if (sel == null || list.none { it.id == sel }) {
                c.selectProject((list.firstOrNull { it.id != com.mbaliga.csapp.domain.model.INBOX_PROJECT_ID } ?: list.firstOrNull())?.id)
            }
        }
    }

    val state: StateFlow<HomeState> = combine(
        c.projectRepository.observeProjects(),
        c.selectedProject,
        c.incidentRepository.observeAll(),
        c.signalRepository.observeAll(),
        c.database.governanceDao().observePending(),
    ) { projects, selId, incidents, signals, pending -> arrayOf(projects, selId, incidents, signals, pending) }
        .combine(filters) { a, f -> a to f }
        .combine(transient) { (a, f), t ->
            @Suppress("UNCHECKED_CAST")
            val projects = a[0] as List<Project>
            val selId = a[1] as String?
            @Suppress("UNCHECKED_CAST")
            val allIncidents = a[2] as List<Incident>
            @Suppress("UNCHECKED_CAST")
            val signals = a[3] as List<com.mbaliga.csapp.domain.model.Signal>
            @Suppress("UNCHECKED_CAST")
            val pending = a[4] as List<com.mbaliga.csapp.data.db.entities.SuggestionEntity>
            val selected = projects.firstOrNull { it.id == selId }
            val mine = allIncidents.filter { it.projectId == selId }
            val live = mine.filter { it.status == IncidentStatus.OPEN || it.status == IncidentStatus.ACKNOWLEDGED }
            val counts = Severity.entries.associateWith { s -> live.count { it.severity == s } }
            val sigs = signals.filter { it.projectId == selId }
            val incIds = mine.map { it.id }.toSet()
            val sigCount = sigs.filter { it.incidentId != null }.groupingBy { it.incidentId!! }.eachCount()
            var shown = mine.filter { it.status in f.statuses }
            if (f.severities.isNotEmpty()) shown = shown.filter { it.severity in f.severities }
            if (f.channels.isNotEmpty()) {
                val ok = sigs.filter { it.type in f.channels }.mapNotNull { it.incidentId }.toSet()
                shown = shown.filter { it.id in ok }
            }
            shown = when (f.sort) {
                SortOrder.UPDATED -> shown.sortedByDescending { it.updatedAt }
                SortOrder.SEVERITY -> shown.sortedWith(compareByDescending<Incident> { it.severity.ordinal }.thenByDescending { it.updatedAt })
                SortOrder.SIGNALS -> shown.sortedWith(compareByDescending<Incident> { sigCount[it.id] ?: 0 }.thenByDescending { it.updatedAt })
            }
            val pendingHere = pending.filter { it.kind != SuggestionKind.DIGEST.name && (it.targetId in incIds || it.targetId.split("+").any { id -> id in incIds } || sigs.any { s -> s.sourceKey == it.targetId }) }
            val digest = pending.firstOrNull { it.kind == SuggestionKind.DIGEST.name && it.targetId == selId }
            HomeState(
                projects = projects, selected = selected, incidents = shown, severityCounts = counts,
                needsYou = NeedsYou(
                    newSignals = sigs.count { it.incidentId == null },
                    pendingSuggestions = pendingHere.count { it.state == SuggestionState.PENDING.name },
                    failedSends = sigs.count { it.replyState == ReplyState.FAILED },
                ),
                digest = digest?.let { runCatching { kotlinx.serialization.json.Json.parseToJsonElement(it.payloadJson).let { j -> (j as kotlinx.serialization.json.JsonObject)["text"].toString().trim('"') } }.getOrNull() },
                digestProvenance = digest?.let { com.mbaliga.csapp.domain.ai.Provenance.parse(it.provenance) to it.model },
                filters = f, aiConfigured = c.suggestionService.isConfigured,
                syncing = t.first, message = t.second, undo = t.third,
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeState())

    fun select(id: String) = c.selectProject(id)
    fun setFilters(f: Filters) { filters.value = f }
    fun toggleStatus(s: IncidentStatus) = filters.update { it.copy(statuses = if (s in it.statuses) it.statuses - s else it.statuses + s) }
    fun toggleSeverity(s: Severity) = filters.update { it.copy(severities = if (s in it.severities) it.severities - s else it.severities + s) }
    fun toggleChannel(t: SignalType) = filters.update { it.copy(channels = if (t in it.channels) it.channels - t else it.channels + t) }
    fun setSort(s: SortOrder) = filters.update { it.copy(sort = s) }

    fun syncNow() {
        if (transient.value.first) return
        viewModelScope.launch {
            transient.update { it.copy(first = true, second = null) }
            val msg = try {
                c.pollCoordinator.syncAll().summary()
            } catch (e: Exception) { "Sync failed: ${e.message}" }
            transient.update { it.copy(first = false, second = msg) }
        }
    }

    fun consumeMessage() = transient.update { it.copy(second = null) }

    fun setStatus(incident: Incident, status: IncidentStatus, label: String) {
        viewModelScope.launch {
            c.incidentRepository.updateStatus(incident.id, status)
            transient.update { it.copy(third = UndoAction(incident.id, incident.status, label)) }
        }
    }

    fun undo() {
        val u = transient.value.third ?: return
        viewModelScope.launch {
            c.incidentRepository.updateStatus(u.incidentId, u.previous)
            transient.update { it.copy(third = null) }
        }
    }

    fun clearUndo() = transient.update { it.copy(third = null) }

    fun refreshDigest() {
        val id = state.value.selected?.id ?: return
        viewModelScope.launch {
            try {
                c.suggestionService.suggestDigest(id, System.currentTimeMillis() - 7L * 24 * 3600 * 1000)
            } catch (e: Exception) { transient.update { it.copy(second = "AI digest failed: ${e.message}") } }
        }
    }
}

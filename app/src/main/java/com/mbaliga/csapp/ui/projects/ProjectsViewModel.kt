package com.mbaliga.csapp.ui.projects

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mbaliga.csapp.di.AppContainer
import com.mbaliga.csapp.domain.model.Project
import com.mbaliga.csapp.domain.model.ProjectSource
import com.mbaliga.csapp.domain.model.SourceKind
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ProjectsViewModel(private val c: AppContainer) : ViewModel() {
    val projects: StateFlow<List<Project>> = c.projectRepository.observeProjects()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun create(name: String, onCreated: (String) -> Unit) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val p = c.projectRepository.create(name)
            c.selectProject(p.id)
            onCreated(p.id)
        }
    }
}

class ProjectDetailViewModel(private val projectId: String, private val c: AppContainer) : ViewModel() {
    val project: StateFlow<Project?> = c.projectRepository.observeProjects().let { f ->
        kotlinx.coroutines.flow.flow { f.collect { l -> emit(l.firstOrNull { it.id == projectId }) } }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val sources: StateFlow<List<ProjectSource>> = c.projectRepository.observeSources(projectId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _status = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status.asStateFlow()

    fun addSource(kind: SourceKind, value: String) {
        val v = value.trim()
        if (v.isEmpty()) return
        if (kind == SourceKind.GITHUB_REPO && !Regex("^[\\w.-]+/[\\w.-]+$").matches(v)) { _status.value = "GitHub repo must look like owner/name"; return }
        viewModelScope.launch { c.projectRepository.addSource(projectId, kind, v); _status.value = null }
    }

    fun toggle(s: ProjectSource) = viewModelScope.launch { c.projectRepository.setSourceEnabled(s, !s.enabled) }
    fun remove(s: ProjectSource) = viewModelScope.launch { c.projectRepository.removeSource(s) }
    fun rename(name: String) = viewModelScope.launch { c.projectRepository.rename(projectId, name) }
    fun archive(onDone: () -> Unit) = viewModelScope.launch { c.projectRepository.archive(projectId); onDone() }

    fun autoTriage(): Boolean = projectId in c.settingsStore.autoTriageProjects
    fun setAutoTriage(on: Boolean) {
        c.settingsStore.autoTriageProjects = if (on) c.settingsStore.autoTriageProjects + projectId else c.settingsStore.autoTriageProjects - projectId
    }

    /** Backfill a Play Console monthly CSV picked via SAF into this project. */
    fun backfill(context: Context, uri: Uri, month: String) {
        viewModelScope.launch {
            _status.value = "Importing…"
            _status.value = try {
                val n = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)!!.use { c.playReviewRepository.backfillFromMonthlyReport(it, month, projectId) }
                }
                c.incidentRepository.runClustering()
                "Backfill imported"
            } catch (e: Exception) { "Backfill failed: ${e.message}" }
        }
    }

    private suspend fun render(): String? {
        val p = project.value ?: return null
        val incidents = c.incidentRepository.getForProject(projectId)
        val signals = c.signalRepository.getByIncidentIds(incidents.map { it.id })
        return c.issuesManifestExporter.render(incidents, signals, p.externalId, p.createdAt)
    }

    fun exportTo(uri: Uri) {
        val p = project.value ?: return
        viewModelScope.launch {
            _status.value = try {
                val incidents = c.incidentRepository.getForProject(projectId)
                val signals = c.signalRepository.getByIncidentIds(incidents.map { it.id })
                c.issuesManifestExporter.export(uri, incidents, signals, p.externalId, p.createdAt)
                "Exported issues-manifest.v1.json"
            } catch (e: Exception) { "Export failed: ${e.message}" }
        }
    }

    /** Share-to-Fonebrew: writes the manifest to cache and hands it to the system share sheet. */
    fun share(context: Context) {
        viewModelScope.launch {
            try {
                val text = render() ?: return@launch
                val file = withContext(Dispatchers.IO) {
                    File(context.cacheDir, "exports").apply { mkdirs() }.resolve("issues-manifest.v1.json").also { it.writeText(text) }
                }
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "application/json"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(send, "Share to Fonebrew").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (e: Exception) { _status.value = "Share failed: ${e.message}" }
        }
    }

    fun syncNow() = viewModelScope.launch {
        _status.value = "Syncing…"
        _status.value = try { c.pollCoordinator.syncAll().summary() } catch (e: Exception) { "Sync failed: ${e.message}" }
    }
}

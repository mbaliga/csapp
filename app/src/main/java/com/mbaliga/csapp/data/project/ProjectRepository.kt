package com.mbaliga.csapp.data.project

import com.mbaliga.csapp.data.db.dao.ProjectDao
import com.mbaliga.csapp.data.db.entities.ProjectEntity
import com.mbaliga.csapp.data.db.entities.ProjectSourceEntity
import com.mbaliga.csapp.data.settings.AppSettingsStore
import com.mbaliga.csapp.domain.model.DEFAULT_PROJECT_ID
import com.mbaliga.csapp.domain.model.INBOX_PROJECT_ID
import com.mbaliga.csapp.domain.model.Project
import com.mbaliga.csapp.domain.model.ProjectSource
import com.mbaliga.csapp.domain.model.SourceKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Owns projects and the sources (GitHub repo / Play package / mailbox filter) routed to each. */
class ProjectRepository(
    private val dao: ProjectDao,
    private val now: () -> Long = { System.currentTimeMillis() },
) {
    fun observeProjects(): Flow<List<Project>> = dao.observeActive().map { it.map(::toDomain) }
    suspend fun projects(): List<Project> = dao.findActive().map(::toDomain)
    suspend fun find(id: String): Project? = dao.find(id)?.let(::toDomain)
    fun observeSources(projectId: String): Flow<List<ProjectSource>> =
        dao.observeSourcesFor(projectId).map { l -> l.map(::toDomain) }
    suspend fun sources(projectId: String): List<ProjectSource> = dao.sourcesFor(projectId).map(::toDomain)
    suspend fun allEnabledSources(): List<ProjectSource> = dao.allEnabledSources().map(::toDomain)

    /** Creates a project; the slug is derived from [name] and made unique. */
    suspend fun create(name: String): Project {
        val base = slugify(name)
        var slug = base
        var n = 2
        while (dao.find(slug) != null) slug = "$base-${n++}"
        val project = Project(slug, name.trim(), "csapp:$slug", now())
        dao.upsert(toEntity(project))
        return project
    }

    suspend fun rename(id: String, name: String) {
        val p = dao.find(id) ?: return
        dao.upsert(p.copy(name = name.trim()))
    }

    suspend fun archive(id: String) {
        if (id == INBOX_PROJECT_ID) return
        val p = dao.find(id) ?: return
        dao.upsert(p.copy(archived = true))
    }

    suspend fun addSource(projectId: String, kind: SourceKind, value: String): Long {
        val id = dao.insertSource(ProjectSourceEntity(projectId = projectId, kind = kind.name, value = value.trim(), enabled = true))
        refreshExternalId(projectId)
        return id
    }

    suspend fun setSourceEnabled(source: ProjectSource, enabled: Boolean) {
        dao.updateSource(toEntity(source.copy(enabled = enabled)))
    }

    suspend fun removeSource(source: ProjectSource) {
        dao.deleteSource(source.id)
        refreshExternalId(source.projectId)
    }

    /** `github:o/r` when the project has exactly one GitHub repo, else `csapp:<slug>`. */
    private suspend fun refreshExternalId(projectId: String) {
        val p = dao.find(projectId) ?: return
        val repos = dao.sourcesFor(projectId).filter { it.kind == SourceKind.GITHUB_REPO.name }
        val ext = if (repos.size == 1) "github:${repos[0].value}" else "csapp:${p.id}"
        if (ext != p.externalId) dao.upsert(p.copy(externalId = ext))
    }

    /**
     * Idempotent startup step: guarantees the built-in Inbox project exists, and migrates the
     * pre-multi-project single-repo/package settings (and any pre-existing data) into the
     * `default` project so nothing a V1 user had is lost.
     */
    suspend fun ensureDefaults(legacy: AppSettingsStore) {
        if (dao.find(INBOX_PROJECT_ID) == null) {
            dao.upsert(ProjectEntity(INBOX_PROJECT_ID, "Inbox", "csapp:$INBOX_PROJECT_ID", now(), archived = false))
        }
        if (dao.find(DEFAULT_PROJECT_ID) != null) return
        val owner = legacy.githubOwner?.trim().orEmpty()
        val repo = legacy.githubRepo?.trim().orEmpty()
        val pkg = legacy.playPackageName?.trim().orEmpty()
        val hasData = dao.signalCount(DEFAULT_PROJECT_ID) > 0
        if (owner.isEmpty() && repo.isEmpty() && pkg.isEmpty() && !hasData) return
        val name = when {
            owner.isNotEmpty() && repo.isNotEmpty() -> repo
            pkg.isNotEmpty() -> pkg
            else -> "My project"
        }
        dao.upsert(ProjectEntity(DEFAULT_PROJECT_ID, name, "csapp:$DEFAULT_PROJECT_ID", now(), archived = false))
        if (owner.isNotEmpty() && repo.isNotEmpty()) addSource(DEFAULT_PROJECT_ID, SourceKind.GITHUB_REPO, "$owner/$repo")
        if (pkg.isNotEmpty()) addSource(DEFAULT_PROJECT_ID, SourceKind.PLAY_PACKAGE, pkg)
    }

    private fun toDomain(e: ProjectEntity) = Project(e.id, e.name, e.externalId, e.createdAt, e.archived)
    private fun toDomain(e: ProjectSourceEntity) = ProjectSource(e.id, e.projectId, SourceKind.valueOf(e.kind), e.value, e.enabled)
    private fun toEntity(p: Project) = ProjectEntity(p.id, p.name, p.externalId, p.createdAt, p.archived)
    private fun toEntity(s: ProjectSource) = ProjectSourceEntity(s.id, s.projectId, s.kind.name, s.value, s.enabled)

    companion object {
        fun slugify(name: String): String =
            name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').take(32).ifEmpty { "project" }
    }
}

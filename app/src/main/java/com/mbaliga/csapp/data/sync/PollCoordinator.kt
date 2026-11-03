package com.mbaliga.csapp.data.sync

import com.mbaliga.csapp.data.credentials.CredentialStore
import com.mbaliga.csapp.data.github.GitHubIssuePollRepository
import com.mbaliga.csapp.data.mail.MailPollRepository
import com.mbaliga.csapp.data.play.PlayReviewRepository
import com.mbaliga.csapp.data.project.ProjectRepository
import com.mbaliga.csapp.domain.model.SourceKind
import com.mbaliga.csapp.domain.repository.IncidentRepository

data class SyncReport(val polled: Int, val failures: List<String>) {
    val ok get() = failures.isEmpty()
    fun summary() = if (ok) "Synced $polled source(s)" else "Synced $polled source(s); ${failures.size} failed: ${failures.joinToString("; ")}"
}

/** Fans out one sync pass over every enabled source of every project, then clusters. */
class PollCoordinator(
    private val projects: ProjectRepository,
    private val github: GitHubIssuePollRepository,
    private val play: PlayReviewRepository,
    private val mail: MailPollRepository,
    private val incidents: IncidentRepository,
    private val credentials: CredentialStore,
) {
    suspend fun syncAll(): SyncReport {
        var polled = 0
        val failures = mutableListOf<String>()
        for (source in projects.allEnabledSources()) {
            try {
                when (source.kind) {
                    SourceKind.GITHUB_REPO -> {
                        val (owner, repo) = source.value.split("/", limit = 2).let { it[0] to it.getOrElse(1) { "" } }
                        if (owner.isBlank() || repo.isBlank()) continue
                        github.pollRepo(owner, repo, source.projectId); polled++
                    }
                    SourceKind.PLAY_PACKAGE -> {
                        if (!credentials.hasPlayServiceAccountKey()) { failures += "Play: no service-account key"; continue }
                        play.pollRecentReviews(source.value, source.projectId); polled++
                    }
                    SourceKind.MAILBOX_FILTER -> Unit // mail is polled per mailbox, below
                }
            } catch (e: Exception) {
                failures += "${source.kind.name.lowercase()} ${source.value}: ${e.message ?: e::class.simpleName}"
            }
        }
        try {
            polled += mail.pollAll(failures)
        } catch (e: Exception) {
            failures += "mail: ${e.message ?: e::class.simpleName}"
        }
        incidents.runClustering()
        return SyncReport(polled, failures)
    }
}

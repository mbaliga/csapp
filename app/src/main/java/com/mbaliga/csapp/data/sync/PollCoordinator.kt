package com.mbaliga.csapp.data.sync

import com.mbaliga.csapp.data.credentials.CredentialStore
import com.mbaliga.csapp.data.github.GitHubIssuePollRepository
import com.mbaliga.csapp.data.mail.MailPollRepository
import com.mbaliga.csapp.data.play.PlayReviewRepository
import com.mbaliga.csapp.data.project.ProjectRepository
import com.mbaliga.csapp.domain.model.SourceKind
import com.mbaliga.csapp.domain.repository.IncidentRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
    /** Optional record of per-channel outcomes (the "Sync status" widget reads it). */
    private val health: PollHealthSink? = null,
    private val now: () -> Long = { System.currentTimeMillis() },
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun syncAll(): SyncReport {
        var polled = 0
        val failures = mutableListOf<String>()
        val attempted = mutableMapOf<PollChannel, Int>()
        val failed = mutableMapOf<PollChannel, Int>()
        fun count(channel: PollChannel, ok: Boolean) {
            attempted[channel] = (attempted[channel] ?: 0) + 1
            if (!ok) failed[channel] = (failed[channel] ?: 0) + 1
        }
        for (source in projects.allEnabledSources()) {
            val channel = when (source.kind) {
                SourceKind.GITHUB_REPO -> PollChannel.GITHUB
                SourceKind.PLAY_PACKAGE -> PollChannel.PLAY
                SourceKind.MAILBOX_FILTER -> null
            }
            try {
                when (source.kind) {
                    SourceKind.GITHUB_REPO -> {
                        val (owner, repo) = source.value.split("/", limit = 2).let { it[0] to it.getOrElse(1) { "" } }
                        if (owner.isBlank() || repo.isBlank()) continue
                        github.pollRepo(owner, repo, source.projectId); polled++
                        count(PollChannel.GITHUB, ok = true)
                    }
                    SourceKind.PLAY_PACKAGE -> {
                        if (!credentials.hasPlayServiceAccountKey()) {
                            failures += "Play: no service-account key"; count(PollChannel.PLAY, ok = false); continue
                        }
                        play.pollRecentReviews(source.value, source.projectId); polled++
                        count(PollChannel.PLAY, ok = true)
                    }
                    SourceKind.MAILBOX_FILTER -> Unit // mail is polled per mailbox, below
                }
            } catch (e: Exception) {
                failures += "${source.kind.name.lowercase()} ${source.value}: ${e.message ?: e::class.simpleName}"
                channel?.let { count(it, ok = false) }
            }
        }
        val failuresBeforeMail = failures.size
        try {
            val mailPolled = mail.pollAll(failures)
            polled += mailPolled
            val mailFailed = failures.size - failuresBeforeMail
            if (mailPolled + mailFailed > 0) {
                attempted[PollChannel.MAIL] = mailPolled + mailFailed
                failed[PollChannel.MAIL] = mailFailed
            }
        } catch (e: Exception) {
            failures += "mail: ${e.message ?: e::class.simpleName}"
            count(PollChannel.MAIL, ok = false)
        }
        // Recording is best effort (a failed write must never fail the sync) and is file I/O, so it
        // runs on the IO dispatcher: syncAll is also called from viewModelScope.
        val pass = PollPass(now(), attempted, failed)
        try {
            health?.let { sink -> withContext(io) { sink.record(pass) } }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
        incidents.runClustering()
        return SyncReport(polled, failures)
    }
}

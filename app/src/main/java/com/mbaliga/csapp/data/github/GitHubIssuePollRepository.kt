package com.mbaliga.csapp.data.github

import com.mbaliga.csapp.data.db.dao.CheckpointDao
import com.mbaliga.csapp.data.db.entities.GithubCheckpointEntity
import com.mbaliga.csapp.domain.model.Signal
import com.mbaliga.csapp.domain.model.SignalType
import com.mbaliga.csapp.domain.repository.SignalRepository
import java.time.Instant

/** Orchestrates one polling pass for a single GitHub repo's issues, end to end. */
class GitHubIssuePollRepository(
    private val client: GitHubIssuePollingClient,
    private val checkpointDao: CheckpointDao,
    private val signalRepository: SignalRepository,
    private val now: () -> Long = { System.currentTimeMillis() },
) {
    suspend fun pollRepo(owner: String, repo: String) {
        val repoFullName = "$owner/$repo"
        val existingCheckpoint = checkpointDao.findGithubCheckpoint(repoFullName)
        val checkpoint = GithubPollCheckpoint(
            sinceMillis = existingCheckpoint?.sinceMillis ?: 0L,
            etag = existingCheckpoint?.lastEtag,
            lastSeenIssueNumber = existingCheckpoint?.lastSeenIssueNumber ?: 0,
        )

        val result = client.pollIssues(owner, repo, checkpoint)
        if (result.notModified) return

        for (issue in result.issues) {
            val signal = issue.toSignal(owner, repo)
            signalRepository.ingestOrUpdate(signal)
        }

        checkpointDao.upsertGithubCheckpoint(
            GithubCheckpointEntity(
                repoFullName = repoFullName,
                sinceMillis = result.nextCheckpoint.sinceMillis,
                lastEtag = result.nextCheckpoint.etag,
                lastSeenIssueNumber = result.nextCheckpoint.lastSeenIssueNumber,
                updatedAt = now(),
            )
        )
    }

    private fun GitHubIssueDto.toSignal(owner: String, repo: String): Signal {
        val createdAtMillis = runCatching { Instant.parse(createdAt).toEpochMilli() }.getOrDefault(now())
        val updatedAtMillis = runCatching { Instant.parse(updatedAt).toEpochMilli() }.getOrDefault(createdAtMillis)
        return Signal(
            sourceKey = "github:$owner/$repo#$number",
            type = SignalType.GITHUB_ISSUE,
            title = title,
            body = body.orEmpty(),
            authorName = user?.login,
            rating = null,
            createdAt = createdAtMillis,
            sourceUpdatedAt = updatedAtMillis,
            ingestedAt = now(),
            metadataJson = """{"htmlUrl":"$htmlUrl","state":"$state"}""",
        )
    }
}

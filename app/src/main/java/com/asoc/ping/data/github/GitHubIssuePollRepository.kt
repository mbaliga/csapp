package com.asoc.ping.data.github

import com.asoc.ping.data.db.dao.CheckpointDao
import com.asoc.ping.data.db.entities.GithubCheckpointEntity
import com.asoc.ping.domain.model.DEFAULT_PROJECT_ID
import com.asoc.ping.domain.model.Signal
import com.asoc.ping.domain.model.SignalType
import com.asoc.ping.domain.repository.SignalRepository
import java.time.Instant
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Orchestrates one polling pass for a single GitHub repo's issues, end to end. */
class GitHubIssuePollRepository(
    private val client: GitHubIssuePollingClient,
    private val checkpointDao: CheckpointDao,
    private val signalRepository: SignalRepository,
    private val now: () -> Long = { System.currentTimeMillis() },
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun pollRepo(owner: String, repo: String, projectId: String = DEFAULT_PROJECT_ID) {
        val repoFullName = "$owner/$repo"
        val existingCheckpoint = checkpointDao.findGithubCheckpoint(repoFullName)
        val checkpoint = GithubPollCheckpoint(
            sinceMillis = existingCheckpoint?.sinceMillis ?: 0L,
            etag = existingCheckpoint?.lastEtag,
            lastSeenIssueNumber = existingCheckpoint?.lastSeenIssueNumber ?: 0,
        )

        val result = withContext(io) { client.pollIssues(owner, repo, checkpoint) }
        if (result.notModified) return

        for (issue in result.issues) {
            val signal = issue.toSignal(owner, repo, projectId)
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

    private fun GitHubIssueDto.toSignal(owner: String, repo: String, projectId: String): Signal {
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
            metadataJson = buildJsonObject {
                put("htmlUrl", htmlUrl)
                put("state", state)
            }.toString(),
            projectId = projectId,
        )
    }
}

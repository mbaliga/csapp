package com.mbaliga.csapp.data.github

import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/** Cursor persisted between polls; see [com.mbaliga.csapp.data.db.entities.GithubCheckpointEntity]. */
data class GithubPollCheckpoint(
    val sinceMillis: Long,
    val etag: String?,
    val lastSeenIssueNumber: Int,
)

data class GitHubIssuePollResult(
    val issues: List<GitHubIssueDto>,
    val notModified: Boolean,
    val nextCheckpoint: GithubPollCheckpoint,
)

/**
 * Polls the GitHub REST "list repository issues" endpoint for one repo, handling:
 *  - PR filtering (the issues endpoint interleaves pull requests; we drop any item whose
 *    `pull_request` field is present),
 *  - pagination via the RFC 5988 `Link: rel="next"` response header,
 *  - conditional requests (`If-None-Match` / ETag) to cheaply no-op when nothing changed,
 *  - an overlap-safe checkpoint: the next `since` cursor is set [OVERLAP_BUFFER_MILLIS] behind
 *    the newest `updated_at` seen this poll, so an issue updated in the same instant as the
 *    checkpoint is never silently skipped on the next poll. Ingestion is idempotent (keyed by
 *    sourceKey), so re-seeing an issue inside the overlap window is harmless.
 */
class GitHubIssuePollingClient(
    private val httpClient: OkHttpClient,
    private val tokenProvider: () -> String?,
    private val baseUrl: String = "https://api.github.com",
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun pollIssues(owner: String, repo: String, checkpoint: GithubPollCheckpoint): GitHubIssuePollResult {
        val sinceIso = DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(checkpoint.sinceMillis))
        val allIssues = mutableListOf<GitHubIssueDto>()
        var page = 1
        var newestUpdatedMillis = checkpoint.sinceMillis
        var maxIssueNumber = checkpoint.lastSeenIssueNumber
        var responseEtag: String? = checkpoint.etag

        var hasNextPage = true
        while (hasNextPage && page <= MAX_PAGES) {
            val url = "$baseUrl/repos/$owner/$repo/issues".toHttpUrl().newBuilder()
                .addQueryParameter("state", "all")
                .addQueryParameter("since", sinceIso)
                .addQueryParameter("sort", "updated")
                .addQueryParameter("direction", "asc")
                .addQueryParameter("per_page", "100")
                .addQueryParameter("page", page.toString())
                .build()

            val requestBuilder = Request.Builder()
                .url(url)
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
            tokenProvider()?.let { requestBuilder.header("Authorization", "Bearer $it") }
            // Conditional request only applies meaningfully to the first page.
            if (page == 1 && checkpoint.etag != null) {
                requestBuilder.header("If-None-Match", checkpoint.etag)
            }

            val notModified = httpClient.newCall(requestBuilder.build()).execute().use { response: Response ->
                if (page == 1 && response.code == 304) {
                    return@use true
                }
                if (!response.isSuccessful) {
                    throw GitHubPollException("GitHub issues request failed: HTTP ${response.code} for $owner/$repo")
                }
                if (page == 1) {
                    responseEtag = response.header("ETag") ?: responseEtag
                }
                val bodyString = response.body?.string().orEmpty()
                val pageIssues = if (bodyString.isBlank()) emptyList() else json.decodeFromString<List<GitHubIssueDto>>(bodyString)

                for (issue in pageIssues) {
                    if (issue.pullRequest != null) continue // exclude PRs, issues only
                    allIssues.add(issue)
                    val updatedMillis = runCatching { Instant.parse(issue.updatedAt).toEpochMilli() }.getOrDefault(newestUpdatedMillis)
                    if (updatedMillis > newestUpdatedMillis) newestUpdatedMillis = updatedMillis
                    if (issue.number > maxIssueNumber) maxIssueNumber = issue.number
                }

                hasNextPage = pageIssues.isNotEmpty() && parseLinkHeaderHasNext(response.header("Link"))
                false
            }

            if (notModified) {
                return GitHubIssuePollResult(
                    issues = emptyList(),
                    notModified = true,
                    nextCheckpoint = checkpoint,
                )
            }
            page += 1
        }

        val nextSince = (newestUpdatedMillis - OVERLAP_BUFFER_MILLIS).coerceAtLeast(0)
        return GitHubIssuePollResult(
            issues = allIssues,
            notModified = false,
            nextCheckpoint = GithubPollCheckpoint(
                sinceMillis = nextSince,
                etag = responseEtag,
                lastSeenIssueNumber = maxIssueNumber,
            ),
        )
    }

    private fun parseLinkHeaderHasNext(linkHeader: String?): Boolean {
        if (linkHeader.isNullOrBlank()) return false
        return linkHeader.split(",").any { part -> part.contains("rel=\"next\"") }
    }

    companion object {
        val OVERLAP_BUFFER_MILLIS = TimeUnit.MINUTES.toMillis(2)
        const val MAX_PAGES = 50
    }
}

class GitHubPollException(message: String) : Exception(message)

package com.mbaliga.csapp.data.github

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Minimal mapping of the GitHub REST "list repository issues" response shape we need. */
@Serializable
data class GitHubIssueDto(
    val id: Long,
    val number: Int,
    val title: String,
    val body: String? = null,
    val state: String,
    @SerialName("html_url") val htmlUrl: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    val user: GitHubUserDto? = null,
    /**
     * Present (non-null) only when this "issue" is actually a pull request. GitHub's issues API
     * returns PRs interleaved with real issues; callers must filter these out.
     */
    @SerialName("pull_request") val pullRequest: GitHubPullRequestMarkerDto? = null,
)

@Serializable
data class GitHubPullRequestMarkerDto(
    val url: String? = null,
)

@Serializable
data class GitHubUserDto(
    val login: String,
)

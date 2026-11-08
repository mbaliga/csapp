package com.asoc.ping.domain.model

/** Id of the project that holds data created before multi-project support existed. */
const val DEFAULT_PROJECT_ID = "default"

/** Built-in project that receives mail no mailbox filter routed anywhere else. */
const val INBOX_PROJECT_ID = "inbox"

enum class SourceKind { GITHUB_REPO, PLAY_PACKAGE, MAILBOX_FILTER }

data class Project(
    val id: String,
    val name: String,
    val externalId: String,
    val createdAt: Long,
    val archived: Boolean = false,
)

data class ProjectSource(
    val id: Long = 0,
    val projectId: String,
    val kind: SourceKind,
    val value: String,
    val enabled: Boolean = true,
)

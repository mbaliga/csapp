package com.mbaliga.csapp.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Per-repo polling checkpoint for GitHub issues. Keyed by "owner/repo".
 *
 * [sinceMillis] is intentionally kept a little behind the true last-seen timestamp
 * ([overlapBufferMillis]) so that issues updated in the same second as the last poll are never
 * silently skipped (GitHub's `since` query param is second-resolution and inclusive-ish but not
 * guaranteed race-free); duplicate re-fetches are cheap because ingestion is keyed by
 * `sourceKey` and is idempotent.
 */
@Entity(tableName = "github_checkpoints")
data class GithubCheckpointEntity(
    @PrimaryKey val repoFullName: String,
    val sinceMillis: Long,
    val lastEtag: String?,
    val lastSeenIssueNumber: Int,
    val updatedAt: Long,
)

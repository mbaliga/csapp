package com.mbaliga.csapp.data.db.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room row for a raw ingested signal. [sourceKey] is the stable, source-derived identity
 * ("play:<reviewId>", "github:<owner>/<repo>#<number>", or "manual:<uuid>") and is unique.
 */
@Entity(
    tableName = "signals",
    indices = [
        Index(value = ["sourceKey"], unique = true),
        Index(value = ["incidentId"]),
        Index(value = ["type"]),
    ],
)
data class SignalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceKey: String,
    val type: String,
    val title: String,
    val body: String,
    val authorName: String?,
    val rating: Int?,
    val createdAt: Long,
    val sourceUpdatedAt: Long,
    val ingestedAt: Long,
    val editedAt: Long?,
    val originalBodyHash: String?,
    val incidentId: String?,
    val replyState: String,
    val replyDraft: String?,
    val repliedAt: Long?,
    val metadataJson: String?,
)

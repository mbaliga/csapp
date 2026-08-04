package com.mbaliga.csapp.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Per-package polling checkpoint for Play review ingestion (recent-reviews API + backfill). */
@Entity(tableName = "play_checkpoints")
data class PlayCheckpointEntity(
    @PrimaryKey val packageName: String,
    val lastSeenReviewSubmitMillis: Long,
    val lastPollAt: Long,
    val lastBackfillMonth: String?,
)

package com.mbaliga.csapp.domain.model

/**
 * A single raw signal ingested from an external source (a Play review, a GitHub issue) or
 * authored manually. Signals are the atomic unit that clustering groups into [Incident]s.
 *
 * [sourceKey] is a stable, source-derived identity string (e.g. "play:<reviewId>" or
 * "github:<owner>/<repo>#<number>") used for de-duplication and as clustering anchors. It must
 * never be regenerated randomly - the same underlying review/issue must always produce the same
 * sourceKey across polls.
 */
data class Signal(
    val id: Long = 0,
    val sourceKey: String,
    val type: SignalType,
    val title: String,
    val body: String,
    val authorName: String?,
    val rating: Int?,
    val createdAt: Long,
    val sourceUpdatedAt: Long,
    val ingestedAt: Long,
    val editedAt: Long? = null,
    val originalBodyHash: String? = null,
    val incidentId: String? = null,
    val replyState: ReplyState = ReplyState.NONE,
    val replyDraft: String? = null,
    val repliedAt: Long? = null,
    val metadataJson: String? = null,
)

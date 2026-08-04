package com.mbaliga.csapp.domain.model

/**
 * A cluster of one or more [Signal]s representing a single underlying user-facing problem.
 *
 * [id] is either:
 *  - anchor-derived (`inc_<hex>`), deterministically produced from the earliest signal's
 *    [Signal.sourceKey] in an automatically clustered incident (see
 *    `AnchorIdGenerator`), so re-running clustering on the same data never mints a new id; or
 *  - a UUID minted once, at manual-creation or split time, for incidents that did not come from
 *    automatic clustering.
 */
data class Incident(
    val id: String,
    val title: String,
    val summary: String,
    val severity: Severity,
    val status: IncidentStatus,
    val isManual: Boolean,
    val isRecurringOf: String? = null,
    val mergedInto: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

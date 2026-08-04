package com.mbaliga.csapp.domain.model

/** Lifecycle status of an incident. */
enum class IncidentStatus {
    OPEN,
    ACKNOWLEDGED,
    RESOLVED,
    DISMISSED,
    /** Superseded by another incident as the result of a merge. */
    MERGED,
}

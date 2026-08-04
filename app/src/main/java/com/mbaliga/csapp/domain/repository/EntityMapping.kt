package com.mbaliga.csapp.domain.repository

import com.mbaliga.csapp.data.db.entities.IncidentEntity
import com.mbaliga.csapp.data.db.entities.SignalEntity
import com.mbaliga.csapp.domain.model.Incident
import com.mbaliga.csapp.domain.model.IncidentStatus
import com.mbaliga.csapp.domain.model.ReplyState
import com.mbaliga.csapp.domain.model.Severity
import com.mbaliga.csapp.domain.model.Signal
import com.mbaliga.csapp.domain.model.SignalType

fun SignalEntity.toDomain(): Signal = Signal(
    id = id,
    sourceKey = sourceKey,
    type = SignalType.valueOf(type),
    title = title,
    body = body,
    authorName = authorName,
    rating = rating,
    createdAt = createdAt,
    sourceUpdatedAt = sourceUpdatedAt,
    ingestedAt = ingestedAt,
    editedAt = editedAt,
    originalBodyHash = originalBodyHash,
    incidentId = incidentId,
    replyState = ReplyState.valueOf(replyState),
    replyDraft = replyDraft,
    repliedAt = repliedAt,
    metadataJson = metadataJson,
)

fun Signal.toEntity(): SignalEntity = SignalEntity(
    id = id,
    sourceKey = sourceKey,
    type = type.name,
    title = title,
    body = body,
    authorName = authorName,
    rating = rating,
    createdAt = createdAt,
    sourceUpdatedAt = sourceUpdatedAt,
    ingestedAt = ingestedAt,
    editedAt = editedAt,
    originalBodyHash = originalBodyHash,
    incidentId = incidentId,
    replyState = replyState.name,
    replyDraft = replyDraft,
    repliedAt = repliedAt,
    metadataJson = metadataJson,
)

fun IncidentEntity.toDomain(): Incident = Incident(
    id = id,
    title = title,
    summary = summary,
    severity = Severity.valueOf(severity),
    status = IncidentStatus.valueOf(status),
    isManual = isManual,
    isRecurringOf = isRecurringOf,
    mergedInto = mergedInto,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun Incident.toEntity(): IncidentEntity = IncidentEntity(
    id = id,
    title = title,
    summary = summary,
    severity = severity.name,
    status = status.name,
    isManual = isManual,
    isRecurringOf = isRecurringOf,
    mergedInto = mergedInto,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

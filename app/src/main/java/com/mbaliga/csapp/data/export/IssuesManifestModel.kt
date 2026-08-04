package com.mbaliga.csapp.data.export

import com.mbaliga.csapp.domain.model.Incident
import com.mbaliga.csapp.domain.model.Signal
import java.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** The `issues-manifest.json` v1 export format: a snapshot of all incidents and their signals. */
@Serializable
data class IssuesManifestV1(
    val schemaVersion: Int = 1,
    val generatedAt: String,
    val incidents: List<ManifestIncident>,
)

@Serializable
data class ManifestIncident(
    val id: String,
    val title: String,
    val summary: String,
    val severity: String,
    val status: String,
    val isManual: Boolean,
    val isRecurringOf: String? = null,
    val mergedInto: String? = null,
    val createdAt: String,
    val updatedAt: String,
    val signals: List<ManifestSignal>,
)

@Serializable
data class ManifestSignal(
    val sourceKey: String,
    val type: String,
    val title: String,
    val body: String,
    val authorName: String? = null,
    val rating: Int? = null,
    val createdAt: String,
    @SerialName("replyState") val replyState: String,
    val repliedAt: String? = null,
)

/** Builds the exportable manifest model from domain objects. Pure, deterministic, no I/O. */
object IssuesManifestBuilder {
    private val jsonFormat = Json {
        prettyPrint = true
        encodeDefaults = true
    }

    fun build(
        incidents: List<Incident>,
        signalsByIncidentId: Map<String, List<Signal>>,
        nowMillis: Long = System.currentTimeMillis(),
    ): IssuesManifestV1 {
        val manifestIncidents = incidents
            .sortedBy { it.id }
            .map { incident ->
                val signals = signalsByIncidentId[incident.id].orEmpty().sortedBy { it.sourceKey }
                ManifestIncident(
                    id = incident.id,
                    title = incident.title,
                    summary = incident.summary,
                    severity = incident.severity.name,
                    status = incident.status.name,
                    isManual = incident.isManual,
                    isRecurringOf = incident.isRecurringOf,
                    mergedInto = incident.mergedInto,
                    createdAt = Instant.ofEpochMilli(incident.createdAt).toString(),
                    updatedAt = Instant.ofEpochMilli(incident.updatedAt).toString(),
                    signals = signals.map { signal ->
                        ManifestSignal(
                            sourceKey = signal.sourceKey,
                            type = signal.type.name,
                            title = signal.title,
                            body = signal.body,
                            authorName = signal.authorName,
                            rating = signal.rating,
                            createdAt = Instant.ofEpochMilli(signal.createdAt).toString(),
                            replyState = signal.replyState.name,
                            repliedAt = signal.repliedAt?.let { Instant.ofEpochMilli(it).toString() },
                        )
                    },
                )
            }

        return IssuesManifestV1(
            generatedAt = Instant.ofEpochMilli(nowMillis).toString(),
            incidents = manifestIncidents,
        )
    }

    fun toJson(manifest: IssuesManifestV1): String = jsonFormat.encodeToString(IssuesManifestV1.serializer(), manifest)
}

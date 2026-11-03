package com.mbaliga.csapp.data.export

import com.mbaliga.csapp.domain.model.Incident
import com.mbaliga.csapp.domain.model.IncidentStatus
import com.mbaliga.csapp.domain.model.Severity
import com.mbaliga.csapp.domain.model.Signal
import java.time.Instant
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The `issues-manifest.json` v1 export format — CSApp's file lane in the Fonebrew hub contract
 * (`docs/ratified/CSAPP_ISSUES_MANIFEST_V1.md`, INT-013/INT-015, in `mbaliga/Android-IDE-core`;
 * wire shape at `schemas/integrations/issues-manifest.v1.schema.json` there). One snapshot of
 * this project's incident backlog, flattened to the shape the hub's import pipeline expects.
 *
 * Deliberately NOT wrapped in that constellation's `ContractEnvelope` — CSApp is a fully
 * independent app and must stay standalone-useful with zero dependency on the hub's envelope
 * library (INT-002). This file format, and how CSApp manages incidents/signals internally, are
 * separate concerns; this model only describes what gets written on export.
 */
@Serializable
data class IssuesManifestV1(
    val schemaVersion: String = "1.0.0",
    val exportId: String,
    val exportedAt: String,
    val producer: ManifestProducerRef,
    val projectRef: ManifestProjectRef,
    val issues: List<ManifestIssue>,
)

@Serializable
data class ManifestProducerRef(
    val app: String,
    val version: String,
)

@Serializable
data class ManifestProjectRef(
    val externalId: String,
)

/**
 * One CSApp [Incident], flattened to the hub's `CsAppIssue` shape (INT-013). That shape has no
 * named `signals` property in v1 (deliberately out of scope, ratified doc §4) — [Signal]s are
 * folded into [detail] (see [IssuesManifestBuilder.buildDetail]) rather than dropped. This is a
 * scope choice, not a schema restriction: `additionalProperties` on `CsAppIssue` in
 * `issues-manifest.v1.schema.json` is open (`true`), so an ad hoc `signals` field would validate
 * if CSApp added one. CSApp deliberately stays within the ratified field list instead and folds
 * signal detail into free text.
 */
@Serializable
data class ManifestIssue(
    val id: String,
    val title: String,
    val detail: String,
    val severity: String,
    val reporterRef: String,
    val occurredAt: String,
    val updatedAt: String,
    val sourceRevision: String? = null,
    val status: String? = null,
)

/** Builds the exportable manifest model from domain objects. Pure, deterministic, no I/O. */
object IssuesManifestBuilder {
    private val jsonFormat = Json {
        prettyPrint = true
        encodeDefaults = true
    }

    private const val PRODUCER_APP = "csapp"

    /** [Incident.reporterRef] fallback for an incident with no signals and no known author -
     *  e.g. a manually-created incident. Opaque per the hub contract; never null (required). */
    private const val REPORTER_REF_MANUAL = "csapp:manual-entry"
    private const val REPORTER_REF_UNATTRIBUTED = "csapp:unattributed"

    fun build(
        incidents: List<Incident>,
        signalsByIncidentId: Map<String, List<Signal>>,
        producerVersion: String,
        projectExternalId: String,
        nowMillis: Long? = null,
        exportId: String? = null,
        projectCreatedAt: Long = 0L,
    ): IssuesManifestV1 {
        // DISMISSED and MERGED incidents are not exported (a merge survivor carries the signals).
        val exported = incidents.filter { it.status != IncidentStatus.DISMISSED && it.status != IncidentStatus.MERGED }
        val issues = exported
            .sortedBy { it.id }
            .map { incident -> toManifestIssue(incident, signalsByIncidentId[incident.id].orEmpty()) }

        // Deterministic bytes: exportedAt is derived from the data, never the wall clock, so an
        // unchanged project re-exports byte-identically (Core detects IMPORT_DUPLICATE_SNAPSHOT).
        val exportedAt = nowMillis ?: exported.maxOfOrNull { it.updatedAt } ?: projectCreatedAt
        val withoutId = IssuesManifestV1(
            exportId = "",
            exportedAt = Instant.ofEpochMilli(exportedAt).toString(),
            producer = ManifestProducerRef(app = PRODUCER_APP, version = producerVersion),
            projectRef = ManifestProjectRef(externalId = projectExternalId),
            issues = issues,
        )
        return withoutId.copy(exportId = exportId ?: computeExportId(withoutId))
    }

    /** sha256 over the canonical JSON of the manifest with an empty exportId. */
    fun computeExportId(manifestWithoutId: IssuesManifestV1): String {
        val canonical = jsonFormat.encodeToString(IssuesManifestV1.serializer(), manifestWithoutId.copy(exportId = ""))
        return java.security.MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    /** Light invariant check run at export time (full JSON-Schema validation runs in CI tests). */
    fun validate(m: IssuesManifestV1) {
        require(Regex("""^1\.\d+\.\d+$""").matches(m.schemaVersion)) { "bad schemaVersion" }
        require(m.exportId.isNotBlank()) { "exportId empty" }
        require(m.projectRef.externalId.isNotBlank()) { "projectRef.externalId empty" }
        Instant.parse(m.exportedAt)
        val sev = setOf("SEV1", "SEV2", "SEV3", "SEV4")
        val ids = HashSet<String>()
        for (i in m.issues) {
            require(i.id.isNotBlank() && ids.add(i.id)) { "issue id missing or duplicated: ${i.id}" }
            require(i.title.isNotBlank() && i.reporterRef.isNotBlank()) { "issue ${i.id} missing title/reporterRef" }
            require(i.severity in sev) { "issue ${i.id} bad severity" }
            Instant.parse(i.occurredAt); Instant.parse(i.updatedAt)
        }
    }

    private fun toManifestIssue(incident: Incident, signals: List<Signal>): ManifestIssue {
        val sortedSignals = signals.sortedBy { it.sourceKey }
        return ManifestIssue(
            id = incident.id,
            title = incident.title,
            detail = buildDetail(incident, sortedSignals),
            severity = mapSeverity(incident.severity),
            reporterRef = resolveReporterRef(incident, sortedSignals),
            occurredAt = Instant.ofEpochMilli(incident.createdAt).toString(),
            updatedAt = Instant.ofEpochMilli(incident.updatedAt).toString(),
            sourceRevision = sortedSignals.maxOfOrNull { it.sourceUpdatedAt }?.let { Instant.ofEpochMilli(it).toString() },
            status = incident.status.name.lowercase(),
        )
    }

    /** Signals have no place in the hub's flat `CsAppIssue` (§4 of the ratified doc) - fold them
     *  into free-text [ManifestIssue.detail] instead of silently dropping them on export. */
    private fun buildDetail(incident: Incident, signals: List<Signal>): String {
        val parts = mutableListOf(incident.summary)
        incident.isRecurringOf?.let { parts += "Recurring of: $it" }
        incident.mergedInto?.let { parts += "Merged into: $it" }
        if (signals.isNotEmpty()) {
            val signalLines = signals.joinToString("\n") { "- [${it.type.name}] ${it.title} (${it.sourceKey})" }
            parts += "Signals:\n$signalLines"
        }
        return parts.joinToString("\n\n")
    }

    private fun resolveReporterRef(incident: Incident, signals: List<Signal>): String =
        signals.firstNotNullOfOrNull { it.authorName?.takeIf { name -> name.isNotBlank() } }
            ?: signals.firstOrNull()?.sourceKey
            ?: if (incident.isManual) REPORTER_REF_MANUAL else REPORTER_REF_UNATTRIBUTED

    /** CSApp's own [Severity] (LOW..CRITICAL) -> the hub's closed `SEV1`(highest)..`SEV4`(lowest)
     *  vocabulary (`$defs.IssueSeverity` in the hub schema). */
    private fun mapSeverity(severity: Severity): String = when (severity) {
        Severity.CRITICAL -> "SEV1"
        Severity.HIGH -> "SEV2"
        Severity.MEDIUM -> "SEV3"
        Severity.LOW -> "SEV4"
    }

    fun toJson(manifest: IssuesManifestV1): String = jsonFormat.encodeToString(IssuesManifestV1.serializer(), manifest)
}

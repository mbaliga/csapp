package com.mbaliga.csapp.export

import com.mbaliga.csapp.data.export.IssuesManifestBuilder
import com.mbaliga.csapp.domain.model.Incident
import com.mbaliga.csapp.domain.model.IncidentStatus
import com.mbaliga.csapp.domain.model.ReplyState
import com.mbaliga.csapp.domain.model.Severity
import com.mbaliga.csapp.domain.model.Signal
import com.mbaliga.csapp.domain.model.SignalType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IssuesManifestBuilderTest {

    private fun incident(
        id: String,
        title: String = "t",
        summary: String = "s",
        severity: Severity = Severity.LOW,
        status: IncidentStatus = IncidentStatus.OPEN,
        isManual: Boolean = false,
        isRecurringOf: String? = null,
        mergedInto: String? = null,
        createdAt: Long = 0,
        updatedAt: Long = 0,
    ) = Incident(id, title, summary, severity, status, isManual, isRecurringOf, mergedInto, createdAt, updatedAt)

    @Test
    fun `manifest has hub schemaVersion and includes all incidents as issues`() {
        val incident = incident(
            id = "inc_abc123",
            title = "Crash on login",
            severity = Severity.HIGH,
            status = IncidentStatus.OPEN,
            createdAt = 1_700_000_000_000,
            updatedAt = 1_700_000_100_000,
        )
        val signal = Signal(
            sourceKey = "github:owner/repo#5",
            type = SignalType.GITHUB_ISSUE,
            title = "Crash on login",
            body = "Steps to reproduce...",
            authorName = "octocat",
            rating = null,
            createdAt = 1_700_000_000_000,
            sourceUpdatedAt = 1_700_000_000_000,
            ingestedAt = 1_700_000_000_000,
            replyState = ReplyState.NONE,
        )

        val manifest = IssuesManifestBuilder.build(
            incidents = listOf(incident),
            signalsByIncidentId = mapOf(incident.id to listOf(signal)),
            producerVersion = "9.9.9",
            projectExternalId = "github:owner/repo",
            nowMillis = 1_700_000_200_000,
            exportId = "export-fixed-1",
        )

        assertEquals("1.0.0", manifest.schemaVersion)
        assertEquals(1, manifest.issues.size)
        assertEquals("inc_abc123", manifest.issues.first().id)
        assertEquals("SEV2", manifest.issues.first().severity)
        assertEquals("octocat", manifest.issues.first().reporterRef)
        assertTrue(manifest.issues.first().detail.contains("github:owner/repo#5"))
    }

    @Test
    fun `toJson produces valid, non-empty JSON containing the issue id`() {
        val incident = incident(id = "inc_xyz", isManual = true)
        val manifest = IssuesManifestBuilder.build(
            incidents = listOf(incident),
            signalsByIncidentId = emptyMap(),
            producerVersion = "9.9.9",
            projectExternalId = "csapp-proj",
            nowMillis = 0,
            exportId = "export-fixed-2",
        )

        val json = IssuesManifestBuilder.toJson(manifest)

        assertTrue(json.contains("\"inc_xyz\""))
        assertTrue(json.contains("\"schemaVersion\""))
        assertTrue(json.contains("\"exportId\""))
        assertTrue(json.contains("\"projectRef\""))
        // No signals -> falls back to the manual-entry reporterRef, never a blank/missing one.
        assertTrue(json.contains("csapp:manual-entry"))
    }

    @Test
    fun `output is deterministic for the same input - issues are stably ordered by incident id`() {
        val incidentB = incident(id = "inc_b", title = "B")
        val incidentA = incident(id = "inc_a", title = "A")

        val manifest1 = IssuesManifestBuilder.build(
            incidents = listOf(incidentB, incidentA),
            signalsByIncidentId = emptyMap(),
            producerVersion = "9.9.9",
            projectExternalId = "csapp-proj",
            nowMillis = 0,
            exportId = "export-fixed-3",
        )
        val manifest2 = IssuesManifestBuilder.build(
            incidents = listOf(incidentA, incidentB),
            signalsByIncidentId = emptyMap(),
            producerVersion = "9.9.9",
            projectExternalId = "csapp-proj",
            nowMillis = 0,
            exportId = "export-fixed-3",
        )

        assertEquals(manifest1.issues.map { it.id }, manifest2.issues.map { it.id })
        assertEquals(listOf("inc_a", "inc_b"), manifest1.issues.map { it.id })
    }

    @Test
    fun `severity maps CSApp's own vocabulary onto the hub's SEV1(highest) to SEV4(lowest) scale`() {
        val mapping = mapOf(
            Severity.CRITICAL to "SEV1",
            Severity.HIGH to "SEV2",
            Severity.MEDIUM to "SEV3",
            Severity.LOW to "SEV4",
        )
        mapping.forEach { (severity, expectedSev) ->
            val manifest = IssuesManifestBuilder.build(
                incidents = listOf(incident(id = "inc_$severity", severity = severity)),
                signalsByIncidentId = emptyMap(),
                producerVersion = "9.9.9",
                projectExternalId = "csapp-proj",
                nowMillis = 0,
                exportId = "export-sev-$severity",
            )
            assertEquals(expectedSev, manifest.issues.first().severity)
        }
    }

    @Test
    fun `exportId defaults to a fresh value each call when not supplied`() {
        val incident = incident(id = "inc_a")
        val m1 = IssuesManifestBuilder.build(listOf(incident), emptyMap(), "9.9.9", "csapp-proj", nowMillis = 0)
        val m2 = IssuesManifestBuilder.build(listOf(incident), emptyMap(), "9.9.9", "csapp-proj", nowMillis = 0)

        assertNotNull(m1.exportId)
        assertTrue(m1.exportId.isNotBlank())
        assertTrue("distinct exports must not share an exportId", m1.exportId != m2.exportId)
    }
}

package com.mbaliga.csapp.export

import com.mbaliga.csapp.data.export.IssuesManifestBuilder
import com.mbaliga.csapp.domain.model.Incident
import com.mbaliga.csapp.domain.model.IncidentStatus
import com.mbaliga.csapp.domain.model.ReplyState
import com.mbaliga.csapp.domain.model.Severity
import com.mbaliga.csapp.domain.model.Signal
import com.mbaliga.csapp.domain.model.SignalType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IssuesManifestBuilderTest {

    @Test
    fun `manifest has schemaVersion 1 and includes all incidents with their signals`() {
        val incident = Incident(
            id = "inc_abc123",
            title = "Crash on login",
            summary = "1 signal(s)",
            severity = Severity.HIGH,
            status = IncidentStatus.OPEN,
            isManual = false,
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
            nowMillis = 1_700_000_200_000,
        )

        assertEquals(1, manifest.schemaVersion)
        assertEquals(1, manifest.incidents.size)
        assertEquals("inc_abc123", manifest.incidents.first().id)
        assertEquals(1, manifest.incidents.first().signals.size)
        assertEquals("github:owner/repo#5", manifest.incidents.first().signals.first().sourceKey)
    }

    @Test
    fun `toJson produces valid, non-empty JSON containing the incident id`() {
        val incident = Incident(
            id = "inc_xyz",
            title = "t",
            summary = "s",
            severity = Severity.LOW,
            status = IncidentStatus.OPEN,
            isManual = true,
            createdAt = 0,
            updatedAt = 0,
        )
        val manifest = IssuesManifestBuilder.build(listOf(incident), emptyMap(), nowMillis = 0)

        val json = IssuesManifestBuilder.toJson(manifest)

        assertTrue(json.contains("\"inc_xyz\""))
        assertTrue(json.contains("\"schemaVersion\""))
    }

    @Test
    fun `output is deterministic for the same input - incidents and signals are stably ordered`() {
        val incidentB = Incident("inc_b", "B", "s", Severity.LOW, IncidentStatus.OPEN, false, createdAt = 0, updatedAt = 0)
        val incidentA = Incident("inc_a", "A", "s", Severity.LOW, IncidentStatus.OPEN, false, createdAt = 0, updatedAt = 0)

        val manifest1 = IssuesManifestBuilder.build(listOf(incidentB, incidentA), emptyMap(), nowMillis = 0)
        val manifest2 = IssuesManifestBuilder.build(listOf(incidentA, incidentB), emptyMap(), nowMillis = 0)

        assertEquals(manifest1.incidents.map { it.id }, manifest2.incidents.map { it.id })
        assertEquals(listOf("inc_a", "inc_b"), manifest1.incidents.map { it.id })
    }
}

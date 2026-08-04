package com.mbaliga.csapp.clustering

import com.mbaliga.csapp.domain.clustering.AnchorIdGenerator
import com.mbaliga.csapp.domain.clustering.ClusteringEngine
import com.mbaliga.csapp.domain.model.Incident
import com.mbaliga.csapp.domain.model.IncidentStatus
import com.mbaliga.csapp.domain.model.ReplyState
import com.mbaliga.csapp.domain.model.Severity
import com.mbaliga.csapp.domain.model.Signal
import com.mbaliga.csapp.domain.model.SignalType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClusteringEngineTest {

    private val engine = ClusteringEngine(similarityThreshold = 0.3, now = { 1_000_000L })

    private fun signal(
        sourceKey: String,
        text: String,
        createdAt: Long,
        type: SignalType = SignalType.GITHUB_ISSUE,
    ) = Signal(
        sourceKey = sourceKey,
        type = type,
        title = text,
        body = text,
        authorName = null,
        rating = null,
        createdAt = createdAt,
        sourceUpdatedAt = createdAt,
        ingestedAt = createdAt,
        replyState = ReplyState.NONE,
    )

    @Test
    fun `similar signals are grouped into one new incident`() {
        val a = signal("gh:1", "crash on login screen after update", 1000L)
        val b = signal("gh:2", "app crashes on the login screen since update", 2000L)

        val result = engine.cluster(listOf(a, b), emptyList(), emptyMap())

        assertEquals(1, result.newIncidents.size)
        assertEquals(result.signalAssignments["gh:1"], result.signalAssignments["gh:2"])
    }

    @Test
    fun `dissimilar signals become separate incidents`() {
        val a = signal("gh:1", "crash on login screen after update", 1000L)
        val b = signal("gh:2", "dark mode toggle does nothing in settings", 2000L)

        val result = engine.cluster(listOf(a, b), emptyList(), emptyMap())

        assertEquals(2, result.newIncidents.size)
        assertTrue(result.signalAssignments["gh:1"] != result.signalAssignments["gh:2"])
    }

    @Test
    fun `incident id is anchored on the earliest signal and is deterministic`() {
        val a = signal("gh:2", "crash on login screen after update", 2000L)
        val b = signal("gh:1", "app crashes on the login screen since update", 1000L)

        val result = engine.cluster(listOf(a, b), emptyList(), emptyMap())

        val expectedId = AnchorIdGenerator.fromAnchorKey("gh:1")
        assertEquals(expectedId, result.signalAssignments["gh:1"])
        assertEquals(expectedId, result.signalAssignments["gh:2"])
    }

    @Test
    fun `re-running clustering with the same input produces the same incident ids`() {
        val a = signal("gh:1", "crash on login screen after update", 1000L)
        val b = signal("gh:2", "app crashes on the login screen since update", 2000L)

        val result1 = engine.cluster(listOf(a, b), emptyList(), emptyMap())
        val result2 = engine.cluster(listOf(a, b), emptyList(), emptyMap())

        assertEquals(result1.newIncidents.map { it.id }, result2.newIncidents.map { it.id })
        assertEquals(result1.signalAssignments, result2.signalAssignments)
    }

    @Test
    fun `a new signal attaches to an existing open incident without changing its id`() {
        val existingIncident = Incident(
            id = "inc_existing123",
            title = "crash on login",
            summary = "1 signal",
            severity = Severity.MEDIUM,
            status = IncidentStatus.OPEN,
            isManual = false,
            createdAt = 500L,
            updatedAt = 500L,
        )
        val existingMember = signal("gh:1", "crash on login screen after update", 500L)
        val newSignal = signal("gh:2", "crash on the login screen after latest update", 1500L)

        val result = engine.cluster(
            listOf(newSignal),
            listOf(existingIncident),
            mapOf(existingIncident.id to listOf(existingMember)),
        )

        assertEquals("inc_existing123", result.signalAssignments["gh:2"])
        assertTrue(result.newIncidents.isEmpty())
    }

    @Test
    fun `signals far apart in time do not cluster even if textually similar`() {
        val a = signal("gh:1", "crash on login screen after update", 0L)
        val b = signal("gh:2", "crash on login screen after update again", 1000L * 60 * 60 * 24 * 400)

        val result = engine.cluster(listOf(a, b), emptyList(), emptyMap())

        assertTrue(result.signalAssignments["gh:1"] != result.signalAssignments["gh:2"])
    }
}

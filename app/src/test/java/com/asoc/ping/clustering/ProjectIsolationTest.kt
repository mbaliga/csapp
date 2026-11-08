package com.asoc.ping.clustering

import com.asoc.ping.domain.clustering.AnchorIdGenerator
import com.asoc.ping.domain.clustering.ClusteringEngine
import com.asoc.ping.domain.model.Signal
import com.asoc.ping.domain.model.SignalType
import com.asoc.ping.domain.repository.IncidentRepository
import com.asoc.ping.fakes.FakeIncidentDao
import com.asoc.ping.fakes.FakeSignalDao
import com.asoc.ping.domain.repository.SignalRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectIsolationTest {
    private fun sig(key: String, project: String, text: String = "app crashes on launch after update") = Signal(
        sourceKey = key, type = SignalType.GITHUB_ISSUE, title = text, body = text, authorName = null, rating = null,
        createdAt = 1000, sourceUpdatedAt = 1000, ingestedAt = 1000, projectId = project,
    )

    @Test fun `ids are project scoped and the legacy default project keeps old ids`() {
        val legacy = AnchorIdGenerator.fromAnchorKey("github:o/r#1")
        assertEquals(legacy, ClusteringEngine().cluster(listOf(sig("github:o/r#1", "default")), emptyList(), emptyMap(), "default").newIncidents.single().id)
        val a = AnchorIdGenerator.fromAnchorKey("github:o/r#1", "fotoz")
        val b = AnchorIdGenerator.fromAnchorKey("github:o/r#1", "pixly")
        assertNotEquals(a, b); assertNotEquals(legacy, a)
        assertTrue(Regex("inc_fotoz_[0-9a-f]{16}").matches(a))
    }

    @Test fun `identical signals in two projects never share an incident and reruns mint no new ids`() = runTest {
        val sd = FakeSignalDao(); val idao = FakeIncidentDao()
        val signals = SignalRepository(sd)
        val repo = IncidentRepository(idao, sd, ClusteringEngine(now = { 5000 }), now = { 5000 })
        signals.ingestOrUpdate(sig("github:a/x#1", "fotoz")); signals.ingestOrUpdate(sig("github:b/y#1", "pixly"))
        repo.runClustering()
        val first = repo.getAll()
        assertEquals(2, first.size)
        assertEquals(setOf("fotoz", "pixly"), first.map { it.projectId }.toSet())
        assertEquals(2, first.map { it.id }.toSet().size)
        // a later duplicate in "fotoz" joins fotoz's incident only
        signals.ingestOrUpdate(sig("github:a/x#2", "fotoz"))
        repo.runClustering()
        val after = repo.getAll()
        assertEquals(first.map { it.id }.toSet(), after.map { it.id }.toSet())
        val fotoz = after.single { it.projectId == "fotoz" }
        assertEquals(2, signals.getByIncidentId(fotoz.id).size)
        assertEquals(1, signals.getByIncidentId(after.single { it.projectId == "pixly" }.id).size)
    }

    @Test fun `merging across projects is refused`() = runTest {
        val sd = FakeSignalDao(); val idao = FakeIncidentDao()
        val signals = SignalRepository(sd)
        val repo = IncidentRepository(idao, sd, ClusteringEngine(now = { 5000 }), now = { 5000 })
        signals.ingestOrUpdate(sig("github:a/x#1", "fotoz")); signals.ingestOrUpdate(sig("github:b/y#1", "pixly"))
        repo.runClustering()
        try { repo.merge(repo.getAll().map { it.id }); throw AssertionError("expected") } catch (e: IllegalArgumentException) { }
    }
}

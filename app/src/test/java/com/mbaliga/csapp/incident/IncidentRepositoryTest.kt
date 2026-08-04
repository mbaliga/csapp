package com.mbaliga.csapp.incident

import com.mbaliga.csapp.domain.model.IncidentStatus
import com.mbaliga.csapp.domain.model.ReplyState
import com.mbaliga.csapp.domain.model.Severity
import com.mbaliga.csapp.domain.model.Signal
import com.mbaliga.csapp.domain.model.SignalType
import com.mbaliga.csapp.domain.repository.IncidentRepository
import com.mbaliga.csapp.domain.repository.toEntity
import com.mbaliga.csapp.fakes.FakeIncidentDao
import com.mbaliga.csapp.fakes.FakeSignalDao
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class IncidentRepositoryTest {

    private lateinit var signalDao: FakeSignalDao
    private lateinit var incidentDao: FakeIncidentDao
    private lateinit var repository: IncidentRepository
    private var clock = 1_000_000L

    @Before
    fun setUp() {
        signalDao = FakeSignalDao()
        incidentDao = FakeIncidentDao()
        repository = IncidentRepository(incidentDao, signalDao, now = { clock })
    }

    private fun signal(sourceKey: String, text: String, createdAt: Long) = Signal(
        sourceKey = sourceKey,
        type = SignalType.GITHUB_ISSUE,
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
    fun `createManual mints a fresh incident with OPEN status`() = runTest {
        val incident = repository.createManual("Title", "Summary", Severity.HIGH)

        assertTrue(incident.isManual)
        assertEquals(IncidentStatus.OPEN, incident.status)
        assertEquals(Severity.HIGH, incident.severity)
        assertNotNull(repository.getById(incident.id))
    }

    @Test
    fun `runClustering groups similar signals and re-running keeps the same incident id`() = runTest {
        signalDao.insertIgnoringDuplicates(signal("gh:1", "crash on login screen after update", 1000).toEntity())
        signalDao.insertIgnoringDuplicates(signal("gh:2", "app crashes on the login screen since update", 2000).toEntity())

        repository.runClustering()
        val incidentsAfterFirst = repository.getAll()
        assertEquals(1, incidentsAfterFirst.size)
        val firstId = incidentsAfterFirst.first().id

        // A third, similar signal arrives later and clustering runs again.
        signalDao.insertIgnoringDuplicates(signal("gh:3", "login screen crash still happening after update", 3000).toEntity())
        repository.runClustering()

        val incidentsAfterSecond = repository.getAll()
        assertEquals(1, incidentsAfterSecond.size)
        assertEquals(firstId, incidentsAfterSecond.first().id)
    }

    @Test
    fun `dismiss sets status to DISMISSED`() = runTest {
        val incident = repository.createManual("t", "s", Severity.LOW)
        repository.dismiss(incident.id)
        assertEquals(IncidentStatus.DISMISSED, repository.getById(incident.id)?.status)
    }

    @Test
    fun `markRecurring sets isRecurringOf`() = runTest {
        val older = repository.createManual("older", "s", Severity.LOW)
        val newer = repository.createManual("newer", "s", Severity.LOW)
        repository.markRecurring(newer.id, older.id)
        assertEquals(older.id, repository.getById(newer.id)?.isRecurringOf)
    }

    @Test
    fun `split moves selected signals into a new anchor-derived incident`() = runTest {
        signalDao.insertIgnoringDuplicates(signal("gh:1", "issue A", 1000).toEntity())
        signalDao.insertIgnoringDuplicates(signal("gh:2", "issue A restated", 1100).toEntity())
        repository.runClustering()
        val original = repository.getAll().first()

        // gh:2 gets split out on its own.
        val newIncident = repository.split(original.id, listOf("gh:2"))

        assertNotNull(newIncident)
        val movedSignal = signalDao.findBySourceKey("gh:2")
        assertEquals(newIncident!!.id, movedSignal?.incidentId)
        val remainingSignal = signalDao.findBySourceKey("gh:1")
        assertEquals(original.id, remainingSignal?.incidentId)
    }

    @Test
    fun `merge keeps the earliest-created incident as survivor and reassigns all signals`() = runTest {
        val earlier = repository.createManual("earlier", "s", Severity.LOW)
        clock += 1000
        val later = repository.createManual("later", "s", Severity.LOW)

        signalDao.insertIgnoringDuplicates(signal("gh:1", "a", 1).toEntity().copy(incidentId = earlier.id))
        signalDao.insertIgnoringDuplicates(signal("gh:2", "b", 2).toEntity().copy(incidentId = later.id))

        val survivor = repository.merge(listOf(later.id, earlier.id))

        assertNotNull(survivor)
        assertEquals(earlier.id, survivor!!.id)
        assertEquals(IncidentStatus.MERGED, repository.getById(later.id)?.status)
        assertEquals(earlier.id, repository.getById(later.id)?.mergedInto)
        assertEquals(earlier.id, signalDao.findBySourceKey("gh:1")?.incidentId)
        assertEquals(earlier.id, signalDao.findBySourceKey("gh:2")?.incidentId)
    }
}

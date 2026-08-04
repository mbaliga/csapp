package com.mbaliga.csapp.fakes

import com.mbaliga.csapp.data.db.dao.SignalDao
import com.mbaliga.csapp.data.db.entities.SignalEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** In-memory fake of [SignalDao] for pure-JVM unit tests (no Room/SQLite involved). */
class FakeSignalDao : SignalDao {
    private val bySourceKey = linkedMapOf<String, SignalEntity>()
    private var nextId = 1L
    private val allFlow = MutableStateFlow<List<SignalEntity>>(emptyList())

    private fun publish() {
        allFlow.value = bySourceKey.values.sortedByDescending { it.createdAt }
    }

    override suspend fun insertIgnoringDuplicates(signal: SignalEntity): Long {
        if (bySourceKey.containsKey(signal.sourceKey)) return -1
        val withId = signal.copy(id = nextId++)
        bySourceKey[signal.sourceKey] = withId
        publish()
        return withId.id
    }

    override suspend fun update(signal: SignalEntity) {
        bySourceKey[signal.sourceKey] = signal
        publish()
    }

    override suspend fun findBySourceKey(sourceKey: String): SignalEntity? = bySourceKey[sourceKey]

    override suspend fun findUnclustered(): List<SignalEntity> =
        bySourceKey.values.filter { it.incidentId == null }.sortedBy { it.createdAt }

    override suspend fun findByIncidentId(incidentId: String): List<SignalEntity> =
        bySourceKey.values.filter { it.incidentId == incidentId }.sortedBy { it.createdAt }

    override suspend fun findByIncidentIds(incidentIds: List<String>): List<SignalEntity> =
        bySourceKey.values.filter { it.incidentId in incidentIds }

    override suspend fun assignIncident(sourceKey: String, incidentId: String) {
        bySourceKey[sourceKey]?.let { bySourceKey[sourceKey] = it.copy(incidentId = incidentId) }
        publish()
    }

    override fun observeAll(): StateFlow<List<SignalEntity>> = allFlow

    override fun observeReplyableReviews(): StateFlow<List<SignalEntity>> = allFlow
}

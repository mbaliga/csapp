package com.mbaliga.csapp.fakes

import com.mbaliga.csapp.data.db.dao.IncidentDao
import com.mbaliga.csapp.data.db.entities.IncidentEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** In-memory fake of [IncidentDao] for pure-JVM unit tests. */
class FakeIncidentDao : IncidentDao {
    private val byId = linkedMapOf<String, IncidentEntity>()
    private val allFlow = MutableStateFlow<List<IncidentEntity>>(emptyList())

    private fun publish() {
        allFlow.value = byId.values.sortedByDescending { it.updatedAt }
    }

    override suspend fun upsert(incident: IncidentEntity) {
        byId[incident.id] = incident
        publish()
    }

    override suspend fun update(incident: IncidentEntity) {
        byId[incident.id] = incident
        publish()
    }

    override suspend fun findById(id: String): IncidentEntity? = byId[id]

    override suspend fun findOpen(): List<IncidentEntity> =
        byId.values.filter { it.status !in setOf("DISMISSED", "MERGED") }

    override fun observeAll(): StateFlow<List<IncidentEntity>> = allFlow

    override suspend fun findAll(): List<IncidentEntity> = byId.values.toList()
}

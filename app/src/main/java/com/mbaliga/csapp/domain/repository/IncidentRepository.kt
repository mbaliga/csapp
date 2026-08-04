package com.mbaliga.csapp.domain.repository

import com.mbaliga.csapp.data.db.dao.IncidentDao
import com.mbaliga.csapp.data.db.dao.SignalDao
import com.mbaliga.csapp.domain.clustering.AnchorIdGenerator
import com.mbaliga.csapp.domain.clustering.ClusteringEngine
import com.mbaliga.csapp.domain.model.Incident
import com.mbaliga.csapp.domain.model.IncidentStatus
import com.mbaliga.csapp.domain.model.Severity
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Owns [Incident] lifecycle: running the deterministic clustering pass over freshly ingested
 * signals, manual incident authoring, and the human-driven edit operations (severity/status,
 * split, merge, dismiss, mark-recurring). Every mutation here is the result of an explicit call
 * from a UI action or the polling pipeline - nothing here runs automatically on a timer other
 * than clustering itself, which only groups signals; it never sends anything externally.
 */
class IncidentRepository(
    private val incidentDao: IncidentDao,
    private val signalDao: SignalDao,
    private val clusteringEngine: ClusteringEngine = ClusteringEngine(),
    private val now: () -> Long = { System.currentTimeMillis() },
) {

    fun observeAll(): Flow<List<Incident>> = incidentDao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun getAll(): List<Incident> = incidentDao.findAll().map { it.toDomain() }

    suspend fun getById(id: String): Incident? = incidentDao.findById(id)?.toDomain()

    /** Runs the clustering pass over currently-unclustered signals and persists the result. */
    suspend fun runClustering() {
        val unclustered = signalDao.findUnclustered().map { it.toDomain() }
        if (unclustered.isEmpty()) return

        val openIncidents = incidentDao.findOpen().map { it.toDomain() }
        val membersByIncident = openIncidents.associate { incident ->
            incident.id to signalDao.findByIncidentId(incident.id).map { it.toDomain() }
        }

        val result = clusteringEngine.cluster(unclustered, openIncidents, membersByIncident)

        for ((sourceKey, incidentId) in result.signalAssignments) {
            signalDao.assignIncident(sourceKey, incidentId)
        }
        for (incident in result.newIncidents) {
            incidentDao.upsert(incident.toEntity())
        }
        // Existing incidents that received new members get their updatedAt bumped.
        val touchedExistingIds = result.signalAssignments.values.toSet() -
            result.newIncidents.map { it.id }.toSet()
        for (incidentId in touchedExistingIds) {
            val entity = incidentDao.findById(incidentId) ?: continue
            incidentDao.update(entity.copy(updatedAt = now()))
        }
    }

    /** Manually authors a new incident. Its id is a UUID minted once, at creation time. */
    suspend fun createManual(title: String, summary: String, severity: Severity): Incident {
        val incident = Incident(
            id = "inc_manual_${UUID.randomUUID()}",
            title = title,
            summary = summary,
            severity = severity,
            status = IncidentStatus.OPEN,
            isManual = true,
            createdAt = now(),
            updatedAt = now(),
        )
        incidentDao.upsert(incident.toEntity())
        return incident
    }

    suspend fun updateSeverity(id: String, severity: Severity) {
        val entity = incidentDao.findById(id) ?: return
        incidentDao.update(entity.copy(severity = severity.name, updatedAt = now()))
    }

    suspend fun updateStatus(id: String, status: IncidentStatus) {
        val entity = incidentDao.findById(id) ?: return
        incidentDao.update(entity.copy(status = status.name, updatedAt = now()))
    }

    suspend fun dismiss(id: String) = updateStatus(id, IncidentStatus.DISMISSED)

    suspend fun markRecurring(id: String, recurringOfIncidentId: String) {
        val entity = incidentDao.findById(id) ?: return
        incidentDao.update(entity.copy(isRecurringOf = recurringOfIncidentId, updatedAt = now()))
    }

    /**
     * Splits [signalSourceKeys] out of [fromIncidentId] into a brand-new incident, anchored
     * deterministically on the earliest of the moved signals.
     */
    suspend fun split(fromIncidentId: String, signalSourceKeys: List<String>): Incident? {
        if (signalSourceKeys.isEmpty()) return null
        val movingSignals = signalDao.findByIncidentId(fromIncidentId)
            .map { it.toDomain() }
            .filter { it.sourceKey in signalSourceKeys }
        if (movingSignals.isEmpty()) return null

        val anchorKey = AnchorIdGenerator.chooseAnchor(movingSignals.map { it.sourceKey to it.createdAt })
        val newIncidentId = AnchorIdGenerator.fromAnchorKey(anchorKey)
        val anchorSignal = movingSignals.first { it.sourceKey == anchorKey }

        val newIncident = Incident(
            id = newIncidentId,
            title = anchorSignal.title.ifBlank { anchorSignal.body }.take(80),
            summary = "${movingSignals.size} signal(s) split from $fromIncidentId",
            severity = Severity.MEDIUM,
            status = IncidentStatus.OPEN,
            isManual = false,
            createdAt = now(),
            updatedAt = now(),
        )
        incidentDao.upsert(newIncident.toEntity())
        for (signal in movingSignals) {
            signalDao.assignIncident(signal.sourceKey, newIncidentId)
        }

        val remaining = signalDao.findByIncidentId(fromIncidentId)
        if (remaining.isEmpty()) {
            val original = incidentDao.findById(fromIncidentId)
            if (original != null) incidentDao.update(original.copy(updatedAt = now()))
        }
        return newIncident
    }

    /**
     * Merges [incidentIds] into a single surviving incident. The survivor is the incident with
     * the earliest [Incident.createdAt] (tie-broken by id) so the merge outcome is deterministic
     * regardless of the order ids are passed in. All signals from the other incidents are
     * reassigned to the survivor; the losers are marked [IncidentStatus.MERGED] and their
     * [Incident.mergedInto] points at the survivor.
     */
    suspend fun merge(incidentIds: List<String>): Incident? {
        if (incidentIds.size < 2) return null
        val incidents = incidentIds.mapNotNull { incidentDao.findById(it)?.toDomain() }
        if (incidents.size < 2) return null

        val survivor = incidents.sortedWith(compareBy({ it.createdAt }, { it.id })).first()
        val losers = incidents.filter { it.id != survivor.id }

        for (loser in losers) {
            val members = signalDao.findByIncidentId(loser.id)
            for (member in members) {
                signalDao.assignIncident(member.sourceKey, survivor.id)
            }
            val loserEntity = incidentDao.findById(loser.id) ?: continue
            incidentDao.update(loserEntity.copy(status = IncidentStatus.MERGED.name, mergedInto = survivor.id, updatedAt = now()))
        }

        val survivorEntity = incidentDao.findById(survivor.id) ?: return null
        val updatedSurvivor = survivorEntity.copy(updatedAt = now())
        incidentDao.update(updatedSurvivor)
        return updatedSurvivor.toDomain()
    }
}

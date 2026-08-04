package com.mbaliga.csapp.domain.repository

import com.mbaliga.csapp.data.db.dao.SignalDao
import com.mbaliga.csapp.domain.model.ReplyState
import com.mbaliga.csapp.domain.model.Signal
import java.security.MessageDigest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class IngestOutcome { INSERTED, UPDATED_EDITED, UNCHANGED }

data class IngestResult(val signal: Signal, val outcome: IngestOutcome)

/**
 * Owns persistence of [Signal]s, including detecting when a previously-ingested signal's source
 * content has been edited upstream (e.g. a reporter editing a GitHub issue body after the fact)
 * so downstream consumers (clustering, exports) see the latest text while still retaining the
 * original-body hash for audit purposes.
 */
class SignalRepository(private val signalDao: SignalDao) {

    fun observeAll(): Flow<List<Signal>> = signalDao.observeAll().map { list -> list.map { it.toDomain() } }

    fun observeReplyableReviews(): Flow<List<Signal>> =
        signalDao.observeReplyableReviews().map { list -> list.map { it.toDomain() } }

    suspend fun getUnclustered(): List<Signal> = signalDao.findUnclustered().map { it.toDomain() }

    suspend fun getByIncidentId(incidentId: String): List<Signal> =
        signalDao.findByIncidentId(incidentId).map { it.toDomain() }

    suspend fun getByIncidentIds(incidentIds: List<String>): Map<String, List<Signal>> =
        signalDao.findByIncidentIds(incidentIds).map { it.toDomain() }.groupBy { it.incidentId.orEmpty() }

    suspend fun assignIncident(sourceKey: String, incidentId: String) {
        signalDao.assignIncident(sourceKey, incidentId)
    }

    /**
     * Inserts a new signal, or - if a signal with the same [Signal.sourceKey] already exists -
     * updates it in place, detecting a content edit by comparing body hashes. Returns which of
     * the two happened (or [IngestOutcome.UNCHANGED] if nothing about the content differs).
     */
    suspend fun ingestOrUpdate(incoming: Signal): IngestResult {
        val existing = signalDao.findBySourceKey(incoming.sourceKey)
        if (existing == null) {
            val withHash = incoming.copy(originalBodyHash = sha256(incoming.body))
            signalDao.insertIgnoringDuplicates(withHash.toEntity())
            val stored = signalDao.findBySourceKey(incoming.sourceKey)?.toDomain() ?: withHash
            return IngestResult(stored, IngestOutcome.INSERTED)
        }

        val currentBodyHash = sha256(existing.body)
        val incomingBodyHash = sha256(incoming.body)
        if (currentBodyHash == incomingBodyHash && existing.title == incoming.title) {
            return IngestResult(existing.toDomain(), IngestOutcome.UNCHANGED)
        }

        val updated = existing.copy(
            title = incoming.title,
            body = incoming.body,
            sourceUpdatedAt = incoming.sourceUpdatedAt,
            editedAt = incoming.sourceUpdatedAt,
            // originalBodyHash is preserved from first ingestion for audit purposes.
        )
        signalDao.update(updated)
        return IngestResult(updated.toDomain(), IngestOutcome.UPDATED_EDITED)
    }

    suspend fun updateReplyDraft(sourceKey: String, draftText: String) {
        val existing = signalDao.findBySourceKey(sourceKey) ?: return
        signalDao.update(existing.copy(replyDraft = draftText, replyState = ReplyState.DRAFTED.name))
    }

    suspend fun markReplyConfirmedSending(sourceKey: String) {
        val existing = signalDao.findBySourceKey(sourceKey) ?: return
        signalDao.update(existing.copy(replyState = ReplyState.SEND_CONFIRMED.name))
    }

    suspend fun markReplySent(sourceKey: String, sentAtMillis: Long) {
        val existing = signalDao.findBySourceKey(sourceKey) ?: return
        signalDao.update(existing.copy(replyState = ReplyState.SENT.name, repliedAt = sentAtMillis))
    }

    suspend fun markReplyFailed(sourceKey: String) {
        val existing = signalDao.findBySourceKey(sourceKey) ?: return
        signalDao.update(existing.copy(replyState = ReplyState.FAILED.name))
    }

    private fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}

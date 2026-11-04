package com.mbaliga.csapp.domain.repository

import com.mbaliga.csapp.data.db.dao.SignalDao
import com.mbaliga.csapp.domain.model.ReplyState
import com.mbaliga.csapp.domain.model.Signal
import com.mbaliga.csapp.data.mail.MailMessage
import com.mbaliga.csapp.data.mail.mailSignal
import java.security.MessageDigest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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

    suspend fun markReplySent(sourceKey: String, sentAtMillis: Long, text: String? = null) {
        val existing = signalDao.findBySourceKey(sourceKey) ?: return
        signalDao.update(
            existing.copy(
                replyState = ReplyState.SENT.name,
                repliedAt = sentAtMillis,
                sentReplyText = text ?: existing.sentReplyText,
            )
        )
    }

    /** Records a reply already present on the platform (e.g. made in Play Console). */
    suspend fun recordExistingReply(sourceKey: String, text: String, atMillis: Long) {
        val existing = signalDao.findBySourceKey(sourceKey) ?: return
        if (existing.sentReplyText == text && existing.replyState == ReplyState.SENT.name) return
        signalDao.update(existing.copy(replyState = ReplyState.SENT.name, repliedAt = atMillis, sentReplyText = text))
    }

    /** Moves a signal to another project and releases it so the next clustering pass re-homes it. */
    suspend fun moveToProject(sourceKey: String, projectId: String) {
        val existing = signalDao.findBySourceKey(sourceKey) ?: return
        if (existing.projectId == projectId) return
        signalDao.update(existing.copy(projectId = projectId, incidentId = null))
    }

    /**
     * Ingests an email: a new thread becomes a signal; a later message in a known thread is
     * appended to that signal (deduped on Message-ID). Returns true if anything changed.
     */
    suspend fun ingestMailMessage(mailboxId: String, projectId: String, msg: MailMessage, nowMillis: Long): Boolean {
        val existing = signalDao.findEmailByThreadId(msg.threadId)
            ?.takeIf { it.metadataJson?.let { m -> runCatching { Json.parseToJsonElement(m).jsonObject["threadId"]?.jsonPrimitive?.content }.getOrNull() } == msg.threadId }
            ?: signalDao.findBySourceKey("mail:$mailboxId:${msg.messageId}")
        if (existing == null) {
            ingestOrUpdate(mailSignal(mailboxId, projectId, msg, nowMillis))
            return true
        }
        val meta = runCatching { Json.parseToJsonElement(existing.metadataJson.orEmpty()).jsonObject }.getOrDefault(JsonObject(emptyMap()))
        val known = meta["messageIds"]?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty()
        if (msg.messageId in known) return false
        val appended = existing.body + "\n\n— reply from ${msg.from} (${java.time.Instant.ofEpochMilli(msg.dateMillis)}):\n" + msg.bodyText
        val newMeta = JsonObject(meta + ("messageIds" to JsonArray((known + msg.messageId).map { JsonPrimitive(it) })))
        signalDao.update(
            existing.copy(
                body = appended,
                sourceUpdatedAt = maxOf(existing.sourceUpdatedAt, msg.dateMillis),
                metadataJson = newMeta.toString(),
            )
        )
        return true
    }

    suspend fun markReplyFailed(sourceKey: String) {
        val existing = signalDao.findBySourceKey(sourceKey) ?: return
        signalDao.update(existing.copy(replyState = ReplyState.FAILED.name))
    }

    private fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}

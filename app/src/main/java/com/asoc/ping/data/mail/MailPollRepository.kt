package com.asoc.ping.data.mail

import com.asoc.ping.data.credentials.CredentialStore
import com.asoc.ping.data.db.dao.ProjectDao
import com.asoc.ping.data.db.entities.MailCheckpointEntity
import com.asoc.ping.domain.model.INBOX_PROJECT_ID
import com.asoc.ping.domain.model.Signal
import com.asoc.ping.domain.model.SignalType
import com.asoc.ping.domain.model.SourceKind
import com.asoc.ping.domain.repository.SignalRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.add
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.add

/** Source of new messages for one mailbox. IMAP and Gmail implement this. */
fun interface MailSource {
    fun fetchNew(mailbox: MailboxConfig, secret: String, checkpoint: MailCheckpointState?): MailFetchResult
}

/**
 * Pulls new mail for every enabled mailbox, routes each message to a project via mailbox-filter
 * sources (unmatched mail lands in the Inbox project), and writes one EMAIL signal per thread.
 * Replies in a thread are appended to the thread's signal; `Message-ID` dedupes.
 */
class MailPollRepository(
    private val dao: ProjectDao,
    private val signals: SignalRepository,
    private val credentials: CredentialStore,
    private val sourceFor: (MailboxKind) -> MailSource,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val now: () -> Long = { System.currentTimeMillis() },
) {
    /** Returns the number of mailboxes polled; appends failures to [failures]. */
    suspend fun pollAll(failures: MutableList<String>): Int {
        var n = 0
        for (box in dao.enabledMailboxes()) {
            val cfg = MailboxConfig(box.id, MailboxKind.valueOf(box.kind), box.host, box.port, box.username, box.folder)
            try { pollMailbox(cfg); n++ } catch (e: Exception) { failures += "mailbox ${box.label}: ${e.message ?: e::class.simpleName}" }
        }
        return n
    }

    suspend fun pollMailbox(cfg: MailboxConfig) {
        val secret = credentials.getSecret(secretName(cfg.id)) ?: throw MailException("No credential saved for mailbox")
        val key = "${cfg.id}|${cfg.folder}"
        val prior = dao.findMailCheckpoint(key)?.let { MailCheckpointState(it.uidValidity, it.lastUid) }
        val result = withContext(io) { sourceFor(cfg.kind).fetchNew(cfg, secret, prior) }

        val filters = dao.allEnabledSources().filter { it.kind == SourceKind.MAILBOX_FILTER.name }
        for (msg in result.messages.sortedBy { it.dateMillis }) ingest(cfg, msg, filters.map { it.projectId to it.value })
        dao.upsertMailCheckpoint(MailCheckpointEntity(key, result.checkpoint.uidValidity, result.checkpoint.lastUid, now()))
    }

    private suspend fun ingest(cfg: MailboxConfig, msg: MailMessage, filters: List<Pair<String, String>>) {
        val projectId = filters.firstOrNull { MailFilter.matches(it.second, msg) }?.first ?: INBOX_PROJECT_ID
        signals.ingestMailMessage(cfg.id, projectId, msg, now())
    }

    companion object {
        fun secretName(mailboxId: String) = "mailbox_$mailboxId"
    }
}

/** Builds the EMAIL [Signal] for the first message of a thread. */
fun mailSignal(mailboxId: String, projectId: String, msg: MailMessage, ingestedAt: Long): Signal = Signal(
    sourceKey = "mail:$mailboxId:${msg.messageId}",
    type = SignalType.EMAIL,
    title = msg.subject.ifBlank { "(no subject)" },
    body = msg.bodyText,
    authorName = msg.from.ifBlank { null },
    rating = null,
    createdAt = msg.dateMillis,
    sourceUpdatedAt = msg.dateMillis,
    ingestedAt = ingestedAt,
    metadataJson = buildJsonObject {
        put("threadId", msg.threadId)
        putJsonArray("messageIds") { add(msg.messageId) }
        put("from", msg.from)
        put("to", msg.to.joinToString(", "))
        put("attachments", msg.attachmentNames.joinToString(", "))
    }.toString(),
    projectId = projectId,
)

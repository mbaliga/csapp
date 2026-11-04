package com.mbaliga.csapp.data.mail

data class MailMessage(
    val uid: Long,
    val messageId: String,
    val inReplyTo: String?,
    val references: List<String>,
    val from: String,
    val to: List<String>,
    val subject: String,
    val dateMillis: Long,
    val bodyText: String,
    val attachmentNames: List<String>,
) {
    /** Root of the conversation: first References entry, else In-Reply-To, else this message. */
    val threadId: String get() = references.firstOrNull() ?: inReplyTo ?: messageId
}

data class MailboxConfig(
    val id: String,
    val kind: MailboxKind,
    val host: String,
    val port: Int,
    val username: String,
    val folder: String = "INBOX",
)

enum class MailboxKind { IMAP, GMAIL }

data class MailCheckpointState(val uidValidity: Long, val lastUid: Long)

data class MailFetchResult(val messages: List<MailMessage>, val checkpoint: MailCheckpointState)

class MailException(message: String, cause: Throwable? = null) : Exception(message, cause)

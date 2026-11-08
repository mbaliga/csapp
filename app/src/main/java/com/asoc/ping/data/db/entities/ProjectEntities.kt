package com.asoc.ping.data.db.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val id: String,
    val name: String,
    val externalId: String,
    val createdAt: Long,
    val archived: Boolean,
)

@Entity(tableName = "project_sources", indices = [Index(value = ["projectId"])])
data class ProjectSourceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: String,
    val kind: String,
    val value: String,
    val enabled: Boolean,
)

/** Non-secret mailbox settings; the password / refresh token lives in the Keystore. */
@Entity(tableName = "mailboxes")
data class MailboxEntity(
    @PrimaryKey val id: String,
    val label: String,
    val kind: String, // IMAP | GMAIL
    val host: String,
    val port: Int,
    val username: String,
    val folder: String,
    val enabled: Boolean,
)

@Entity(tableName = "mail_checkpoints")
data class MailCheckpointEntity(
    @PrimaryKey val key: String, // "<mailboxId>|<folder>"
    val uidValidity: Long,
    val lastUid: Long,
    val lastPollAt: Long,
)

/** Append-only audit log of every reply sent (written at the single send site). */
@Entity(tableName = "reply_log", indices = [Index(value = ["signalSourceKey"])])
data class ReplyLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val signalSourceKey: String,
    val textSent: String,
    val sentAt: Long,
    val outcome: String,
    val httpStatus: Int?,
)

/** Fixed-window API quota counter (per package + method), persisted so it survives restarts. */
@Entity(tableName = "quota_buckets")
data class QuotaEntity(
    @PrimaryKey val bucketKey: String,
    val windowStart: Long,
    val count: Int,
)

@Entity(tableName = "suggestions", indices = [Index(value = ["targetId"]), Index(value = ["state"])])
data class SuggestionEntity(
    @PrimaryKey val id: String,
    val targetType: String, // INCIDENT | SIGNAL
    val targetId: String,
    val kind: String, // TRIAGE | MERGE | REPLY_DRAFT | DIGEST
    val payloadJson: String,
    val provenance: String, // LOCAL | CLOUD:<providerId>
    val model: String,
    val promptVersion: String,
    val createdAt: Long,
    val state: String, // PENDING | ACCEPTED | REJECTED
)

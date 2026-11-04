package com.mbaliga.csapp.data.sync

import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** The three kinds of source a sync pass polls. */
enum class PollChannel { GITHUB, PLAY, MAIL }

/** What one sync pass attempted and how many of those attempts failed, per channel. */
data class PollPass(
    val at: Long,
    val attempted: Map<PollChannel, Int>,
    val failed: Map<PollChannel, Int>,
)

/**
 * The latest known outcome for one channel. [attempted] and [failed] describe the most recent pass
 * that polled this channel at all; [lastAllOkAt] is the most recent pass in which every source of the
 * channel succeeded (null: never).
 */
@Serializable
data class ChannelHealth(
    val lastAttemptAt: Long? = null,
    val attempted: Int = 0,
    val failed: Int = 0,
    val lastAllOkAt: Long? = null,
)

/**
 * A tiny record of sync outcomes, kept so the "Sync status" widget can say when each channel was last
 * polled and whether it worked. It holds no message text, no source names and no credentials.
 */
@Serializable
data class PollHealth(val channels: Map<PollChannel, ChannelHealth> = emptyMap()) {
    fun of(channel: PollChannel): ChannelHealth? = channels[channel]

    /** Folds one pass in. Channels the pass didn't poll keep their previous record. */
    fun merge(pass: PollPass): PollHealth {
        val next = channels.toMutableMap()
        for (channel in PollChannel.entries) {
            val attempted = pass.attempted[channel] ?: 0
            if (attempted <= 0) continue
            val failed = (pass.failed[channel] ?: 0).coerceIn(0, attempted)
            val prev = channels[channel]
            next[channel] = ChannelHealth(
                lastAttemptAt = pass.at,
                attempted = attempted,
                failed = failed,
                lastAllOkAt = if (failed == 0) pass.at else prev?.lastAllOkAt,
            )
        }
        return PollHealth(next)
    }
}

/** Receives the outcome of each sync pass. */
fun interface PollHealthSink {
    fun record(pass: PollPass)
}

/**
 * File-backed [PollHealth], written under `noBackupFilesDir` so it never reaches a cloud backup.
 * Reads return an empty record when the file is missing or unreadable; writes go through a temp file
 * and a rename so a crash mid-write can't leave half a file. [onChanged] runs after every write
 * (the widgets redraw from it).
 */
class PollHealthStore(
    private val file: File,
    private val onChanged: () -> Unit = {},
) : PollHealthSink {
    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Any()

    fun read(): PollHealth = synchronized(lock) {
        runCatching { if (file.exists()) json.decodeFromString(PollHealth.serializer(), file.readText()) else PollHealth() }
            .getOrDefault(PollHealth())
    }

    override fun record(pass: PollPass) {
        synchronized(lock) {
            val merged = read().merge(pass)
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(json.encodeToString(PollHealth.serializer(), merged))
            if (!tmp.renameTo(file)) {
                file.writeText(tmp.readText())
                tmp.delete()
            }
        }
        onChanged()
    }
}

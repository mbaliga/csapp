package com.mbaliga.csapp.widget

import com.mbaliga.csapp.data.sync.ChannelHealth
import com.mbaliga.csapp.data.sync.PollChannel

/** How one channel's polling looks right now. */
enum class ChannelState { NOT_SET_UP, NEVER, OK, FAILED, STALE }

data class ChannelStatus(val channel: PollChannel, val state: ChannelState, val configured: Int, val health: ChannelHealth?)

/**
 * How fresh the synced data behind the incident, reply and today widgets is. Measured by the last
 * pass in which every source of a channel worked ([ChannelHealth.lastAllOkAt]): a channel whose polls
 * keep failing is not fresh just because it was attempted recently.
 */
sealed interface DataFreshness {
    /** Every set-up channel fully synced within the stale window; [at] is the oldest of those syncs. */
    data class Synced(val at: Long) : DataFreshness

    /** A set-up channel hasn't fully synced within the stale window. [since]: oldest full sync, null if one never fully worked. */
    data class Stale(val since: Long?) : DataFreshness

    /** Sources are set up but no pass has polled them yet. */
    data object NotSynced : DataFreshness
}

object SyncFreshness {
    /**
     * A channel is stale once its last poll is older than two poll intervals plus 15 minutes: the
     * periodic sync has then missed at least one run (offline, Doze, an OEM battery manager).
     */
    fun staleAfterMillis(intervalMinutes: Int): Long = (2L * intervalMinutes.coerceAtLeast(15) + 15L) * 60_000L

    fun status(channel: PollChannel, sync: SyncInfo, now: Long): ChannelStatus {
        val configured = sync.configured[channel] ?: 0
        val h = sync.health.of(channel)
        val last = h?.lastAttemptAt
        val state = when {
            configured <= 0 -> ChannelState.NOT_SET_UP
            last == null -> ChannelState.NEVER
            now - last > staleAfterMillis(sync.intervalMinutes) -> ChannelState.STALE
            h.failed > 0 -> ChannelState.FAILED
            else -> ChannelState.OK
        }
        return ChannelStatus(channel, state, configured, h)
    }

    fun all(sync: SyncInfo, now: Long): List<ChannelStatus> = PollChannel.entries.map { status(it, sync, now) }

    /**
     * Freshness of the synced data, or null when no source is set up (then everything the widgets
     * show is local and can't lapse). Channels set up but not polled yet are left out until a pass
     * reaches them, unless no channel has been polled at all.
     */
    fun data(sync: SyncInfo, now: Long): DataFreshness? {
        val setUp = PollChannel.entries.filter { (sync.configured[it] ?: 0) > 0 }
        if (setUp.isEmpty()) return null
        val polled = setUp.mapNotNull { sync.health.of(it)?.takeIf { h -> h.lastAttemptAt != null } }
        if (polled.isEmpty()) return DataFreshness.NotSynced
        val okTimes = polled.map { it.lastAllOkAt }
        val staleAfter = staleAfterMillis(sync.intervalMinutes)
        val oldest = okTimes.filterNotNull().minOrNull()
        return if (okTimes.any { it == null || now - it > staleAfter }) DataFreshness.Stale(oldest) else DataFreshness.Synced(oldest!!)
    }

    /**
     * When the widgets next need a redraw although no data changed: the next local midnight (printed
     * times switch from "14:05" to "Tue 14:05", and "today" restarts) and the moment a set-up channel
     * turns stale, either for the Sync status widget (last poll) or for the data widgets' freshness
     * line (last full sync). Null when nothing is placed.
     */
    fun nextRedraw(now: Long, nextMidnight: Long, sync: SyncInfo, anyPlaced: Boolean): Long? {
        if (!anyPlaced) return null
        val candidates = mutableListOf(nextMidnight)
        val staleAfter = staleAfterMillis(sync.intervalMinutes)
        for (channel in PollChannel.entries) {
            if ((sync.configured[channel] ?: 0) <= 0) continue
            val h = sync.health.of(channel) ?: continue
            for (last in listOfNotNull(h.lastAttemptAt, h.lastAllOkAt)) {
                val turnsStale = last + staleAfter + 1_000L
                if (turnsStale > now) candidates += turnsStale
            }
        }
        return candidates.filter { it > now }.minOrNull()
    }
}

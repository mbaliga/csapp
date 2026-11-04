package com.mbaliga.csapp.widget

import com.mbaliga.csapp.data.sync.ChannelHealth
import com.mbaliga.csapp.data.sync.PollChannel
import com.mbaliga.csapp.data.sync.PollHealth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SyncFreshnessTest {
    private val now = TestClock.NOW
    private val min = 60_000L

    private fun info(configured: Int = 1, last: Long? = null, attempted: Int = 1, failed: Int = 0, interval: Int = 30) = SyncInfo(
        health = if (last == null) PollHealth() else PollHealth(mapOf(PollChannel.GITHUB to ChannelHealth(last, attempted, failed, if (failed == 0) last else null))),
        configured = mapOf(PollChannel.GITHUB to configured),
        intervalMinutes = interval,
    )

    private fun state(i: SyncInfo) = SyncFreshness.status(PollChannel.GITHUB, i, now).state

    @Test fun `stale after two intervals plus fifteen minutes, with a 15-minute floor`() {
        assertEquals(75 * min, SyncFreshness.staleAfterMillis(30))
        assertEquals(135 * min, SyncFreshness.staleAfterMillis(60))
        assertEquals(45 * min, SyncFreshness.staleAfterMillis(5))
    }

    @Test fun `states and their precedence`() {
        assertEquals(ChannelState.NOT_SET_UP, state(info(configured = 0, last = now - min)))
        assertEquals(ChannelState.NEVER, state(info()))
        assertEquals(ChannelState.OK, state(info(last = now - 10 * min)))
        assertEquals(ChannelState.FAILED, state(info(last = now - 10 * min, attempted = 3, failed = 1)))
        assertEquals(ChannelState.STALE, state(info(last = now - 76 * min)))
        assertEquals(ChannelState.STALE, state(info(last = now - 76 * min, failed = 1)))
        assertEquals(ChannelState.OK, state(info(last = now - 76 * min, interval = 60)))
    }

    @Test fun `next redraw is the next midnight, or sooner when a set-up channel turns stale`() {
        val midnight = TestClock.at(2026, 11, 5, 0, 0)
        assertEquals(midnight, TestClock.clock().nextDayStart())
        assertNull(SyncFreshness.nextRedraw(now, midnight, info(last = now), anyPlaced = false))
        assertEquals(now + 65 * min + 1_000, SyncFreshness.nextRedraw(now, midnight, info(last = now - 10 * min), anyPlaced = true))
        // A channel polled recently but last fully synced earlier turns the data widgets stale sooner.
        val failing = SyncInfo(
            health = PollHealth(mapOf(PollChannel.GITHUB to ChannelHealth(now - 10 * min, 2, 1, now - 60 * min))),
            configured = mapOf(PollChannel.GITHUB to 2),
        )
        assertEquals(now + 15 * min + 1_000, SyncFreshness.nextRedraw(now, midnight, failing, anyPlaced = true))
        // Already stale, or not set up: nothing earlier than midnight.
        assertEquals(midnight, SyncFreshness.nextRedraw(now, midnight, info(last = now - 80 * min), anyPlaced = true))
        assertEquals(midnight, SyncFreshness.nextRedraw(now, midnight, info(configured = 0, last = now), anyPlaced = true))
    }

    @Test fun `data freshness follows the last full sync of every set-up channel`() {
        assertNull("nothing set up: nothing can lapse", SyncFreshness.data(info(configured = 0, last = now), now))
        assertEquals(DataFreshness.NotSynced, SyncFreshness.data(info(), now))
        assertEquals(DataFreshness.Synced(now - 10 * min), SyncFreshness.data(info(last = now - 10 * min), now))
        assertEquals(DataFreshness.Stale(now - 76 * min), SyncFreshness.data(info(last = now - 76 * min), now))
        // Polled a minute ago, but every pass fails: stale, and never fully synced.
        assertEquals(DataFreshness.Stale(null), SyncFreshness.data(info(last = now - min, failed = 1), now))
        // A recent partial failure after a recent full sync is still fresh (as of that full sync).
        val partial = SyncInfo(
            health = PollHealth(mapOf(PollChannel.GITHUB to ChannelHealth(now - min, 2, 1, now - 20 * min))),
            configured = mapOf(PollChannel.GITHUB to 2),
        )
        assertEquals(DataFreshness.Synced(now - 20 * min), SyncFreshness.data(partial, now))
        // A channel set up after the last pass is left out until a pass reaches it.
        val added = SyncInfo(
            health = PollHealth(mapOf(PollChannel.GITHUB to ChannelHealth(now - min, 1, 0, now - min))),
            configured = mapOf(PollChannel.GITHUB to 1, PollChannel.MAIL to 1),
        )
        assertEquals(DataFreshness.Synced(now - min), SyncFreshness.data(added, now))
    }

    @Test fun `printed times stay true without a redraw`() {
        val c = TestClock.clock()
        assertEquals("14:05", c.time(now))
        assertEquals("Mon 09:12", c.time(TestClock.at(2026, 11, 2, 9, 12)))
        assertEquals("28 Oct", c.time(TestClock.at(2026, 10, 28, 9, 0)))
        assertEquals("6 Nov", c.time(TestClock.at(2026, 11, 6, 9, 0)))
        assertEquals("2:05 pm", WidgetClock(now, TestClock.zone, java.util.Locale.UK, is24h = false).time(now).lowercase())
        assertEquals(TestClock.at(2026, 11, 4, 0, 0), c.dayStart())
    }
}

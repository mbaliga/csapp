package com.mbaliga.csapp.widget

import com.mbaliga.csapp.data.sync.ChannelHealth
import com.mbaliga.csapp.data.sync.PollChannel
import com.mbaliga.csapp.data.sync.PollHealth
import com.mbaliga.csapp.data.sync.PollPass
import com.mbaliga.csapp.data.sync.PollHealthStore
import com.mbaliga.csapp.domain.model.IncidentStatus
import com.mbaliga.csapp.domain.model.Severity
import com.mbaliga.csapp.domain.model.SignalType
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetModelTest {

    // ---- sizes ----

    @Test fun `size class is the largest breakpoint that fits, else the one-line summary`() {
        val k = WidgetKind.INCIDENT_LIST
        assertEquals(WidgetSize.TINY, k.sizeFor(40f, 40f))       // below the minimum: still the single summary
        assertEquals(WidgetSize.TINY, k.sizeFor(276f, 51f))      // 4x1 landscape
        assertEquals(WidgetSize.SMALL, k.sizeFor(130f, 220f))    // 2x2 portrait
        assertEquals(WidgetSize.SMALL, k.sizeFor(276f, 102f))    // 4x1 portrait
        assertEquals(WidgetSize.SMALL, k.sizeFor(554f, 117f))    // 4x2 landscape: the 3-row list would clip
        assertEquals(WidgetSize.MEDIUM, k.sizeFor(276f, 220f))   // 4x2
        assertEquals(WidgetSize.LARGE, k.sizeFor(276f, 338f))    // 4x3
        assertEquals(WidgetSize.SMALL, WidgetKind.OPEN_INCIDENTS.sizeFor(269f, 117f)) // 2x2 landscape: no clipped severity row
        assertEquals(WidgetSize.MEDIUM, WidgetKind.OPEN_INCIDENTS.sizeFor(269f, 184f))
        assertEquals(WidgetSize.MEDIUM, WidgetKind.TOP_INCIDENT.sizeFor(203f, 134f))
        assertEquals(WidgetSize.SMALL, WidgetKind.TOP_INCIDENT.sizeFor(203f, 110f))
        assertEquals(WidgetSize.LARGE, WidgetKind.TOP_INCIDENT.sizeFor(276f, 260f))
    }

    @Test fun `larger text picks a smaller layout instead of clipping`() {
        val k = WidgetKind.INCIDENT_LIST
        assertEquals(WidgetSize.MEDIUM, k.sizeFor(276f, 220f, fontScale = 1f))
        assertEquals(WidgetSize.SMALL, k.sizeFor(276f, 220f, fontScale = 1.3f))
        assertEquals(WidgetSize.MEDIUM, k.sizeFor(276f, 300f, fontScale = 1.3f))
        assertEquals(WidgetSize.TINY, k.sizeFor(276f, 102f, fontScale = 2f))
        // Smaller-than-default text never lowers a breakpoint below what the layout needs at 1.0.
        assertEquals(k.breakpoints(), k.breakpoints(fontScale = 0.85f))
    }

    @Test fun `below API 31 the portrait size is used, whatever the orientation`() {
        val k = WidgetKind.INCIDENT_LIST
        assertEquals(WidgetSize.LARGE, k.legacySizeFor(minWidthDp = 276, maxHeightDp = 338))
        assertEquals(WidgetSize.SMALL, k.legacySizeFor(minWidthDp = 0, maxHeightDp = 338))
        assertEquals(WidgetSize.SMALL, k.legacySizeFor(minWidthDp = 276, maxHeightDp = 0))
    }

    @Test fun `every widget has four increasing breakpoints and four distinct layouts`() {
        for (k in WidgetKind.entries) for (scale in listOf(1f, 1.3f, 2f)) {
            val bps = k.breakpoints(scale)
            assertEquals(WidgetSize.entries, bps.map { it.size })
            assertTrue(bps.zipWithNext().all { (a, b) -> b.widthDp >= a.widthDp && b.heightDp >= a.heightDp && (b.widthDp > a.widthDp || b.heightDp > a.heightDp) })
            assertEquals(4, WidgetSize.entries.map { k.skeleton(it) }.toSet().size)
        }
        assertEquals(6, WidgetKind.entries.size)
    }

    // ---- taps ----

    @Test fun `taps round-trip through the two extras`() {
        for (tap in listOf(WidgetTap.Home(), WidgetTap.Home("fotoz"), WidgetTap.Incident("inc_fotoz_ab12"), WidgetTap.Reply("play:com.x:r1"), WidgetTap.Projects)) {
            val (dest, arg) = tap.encode()
            assertEquals(tap, WidgetTap.parse(dest, arg))
        }
    }

    @Test fun `malformed or unknown extras are ignored`() {
        assertNull(WidgetTap.parse(null, null))
        assertNull(WidgetTap.parse("send", "play:x:1"))
        assertNull(WidgetTap.parse(WidgetTap.DEST_INCIDENT, null))
        assertNull(WidgetTap.parse(WidgetTap.DEST_INCIDENT, "   "))
        assertNull(WidgetTap.parse(WidgetTap.DEST_REPLY, "x".repeat(513)))
        assertNull(WidgetTap.parse(WidgetTap.DEST_REPLY, "play:x\n1"))
        assertEquals(WidgetTap.Home(), WidgetTap.parse(WidgetTap.DEST_HOME, "\u0000"))
    }

    @Test fun `pending intent request codes are unique per widget, size and slot`() {
        val codes = (1..50).flatMap { id -> WidgetSize.entries.flatMap { s -> (0..4).map { slot -> WidgetRenderer.requestCode(id, s, slot) } } }
        assertEquals(codes.size, codes.toSet().size)
    }

    // ---- ranking ----

    @Test fun `ranking - severity, then open before acknowledged, then signals, then recency, then id`() {
        val list = listOf(
            incident("z", Severity.HIGH, IncidentStatus.ACKNOWLEDGED, counts = mapOf(SignalType.EMAIL to 9)),
            incident("y", Severity.HIGH, counts = mapOf(SignalType.EMAIL to 1), updatedAt = 1),
            incident("x", Severity.HIGH, counts = mapOf(SignalType.EMAIL to 1), updatedAt = 2),
            incident("w", Severity.HIGH, counts = mapOf(SignalType.EMAIL to 3)),
            incident("v", Severity.CRITICAL, IncidentStatus.ACKNOWLEDGED),
            incident("u", Severity.HIGH, counts = mapOf(SignalType.EMAIL to 1), updatedAt = 2),
        )
        assertEquals(listOf("v", "w", "u", "x", "y", "z"), list.sortedWith(WidgetRanking.comparator).map { it.id })
    }

    // ---- poll health ----

    @Test fun `merging a pass updates only the channels it polled`() {
        val t1 = 1_000L; val t2 = 2_000L
        val h1 = PollHealth().merge(PollPass(t1, mapOf(PollChannel.GITHUB to 2, PollChannel.PLAY to 1), mapOf(PollChannel.PLAY to 1)))
        assertEquals(ChannelHealth(t1, 2, 0, t1), h1.of(PollChannel.GITHUB))
        assertEquals(ChannelHealth(t1, 1, 1, null), h1.of(PollChannel.PLAY))
        assertNull(h1.of(PollChannel.MAIL))
        val h2 = h1.merge(PollPass(t2, mapOf(PollChannel.PLAY to 1), mapOf(PollChannel.PLAY to 0)))
        assertEquals(ChannelHealth(t1, 2, 0, t1), h2.of(PollChannel.GITHUB))
        assertEquals(ChannelHealth(t2, 1, 0, t2), h2.of(PollChannel.PLAY))
        val h3 = h2.merge(PollPass(3_000L, mapOf(PollChannel.GITHUB to 2), mapOf(PollChannel.GITHUB to 5)))
        assertEquals(ChannelHealth(3_000L, 2, 2, t1), h3.of(PollChannel.GITHUB))
    }

    @Test fun `poll health store round-trips, survives a corrupt file and announces changes`() {
        val dir = Files.createTempDirectory("pollhealth").toFile()
        val file = File(dir, "poll_health.json")
        var changed = 0
        val store = PollHealthStore(file, onChanged = { changed++ })
        assertEquals(PollHealth(), store.read())
        store.record(PollPass(5L, mapOf(PollChannel.MAIL to 1), emptyMap()))
        assertEquals(ChannelHealth(5L, 1, 0, 5L), PollHealthStore(file).read().of(PollChannel.MAIL))
        assertEquals(1, changed)
        file.writeText("{not json")
        assertEquals(PollHealth(), store.read())
        assertTrue(!File(dir, "poll_health.json.tmp").exists())
        dir.deleteRecursively()
    }
}

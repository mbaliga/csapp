package com.mbaliga.csapp.widget

import com.mbaliga.csapp.data.sync.ChannelHealth
import com.mbaliga.csapp.data.sync.PollChannel
import com.mbaliga.csapp.data.sync.PollHealth
import com.mbaliga.csapp.domain.model.ReplyState
import com.mbaliga.csapp.domain.model.Severity
import com.mbaliga.csapp.domain.model.SignalType
import com.mbaliga.csapp.ui.components.Marks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** What each widget shows at each size, on csapp's own copy (res/values/strings.xml). */
class WidgetFacesTest {
    private val text = XmlWidgetText()
    private val faces = WidgetFaces(text, TestClock.clock())
    private val rich = richSnapshot()
    private val empty = WidgetSnapshot(projects = PROJECTS)

    /** Both set-up channels fully synced at 13:50 (the rich snapshot's Play channel last fully synced yesterday). */
    private val fresh = rich.copy(
        sync = rich.sync.copy(
            health = PollHealth(
                mapOf(
                    PollChannel.GITHUB to ChannelHealth(TestClock.at(2026, 11, 4, 13, 50), 2, 0, TestClock.at(2026, 11, 4, 13, 50)),
                    PollChannel.PLAY to ChannelHealth(TestClock.at(2026, 11, 4, 13, 55), 2, 0, TestClock.at(2026, 11, 4, 13, 55)),
                )
            ),
        ),
    )
    private val dataKinds = WidgetKind.entries - WidgetKind.SYNC_STATUS

    private fun f(kind: WidgetKind, size: WidgetSize, snap: WidgetSnapshot = rich) = faces.face(kind, size, snap)

    // ---- every widget, every size ----

    @Test fun `every face fits its skeleton and says where a tap goes`() {
        for (snap in listOf(rich, empty)) for (kind in WidgetKind.entries) for (size in WidgetSize.entries) {
            val face = f(kind, size, snap)
            val sk = face.skeleton
            assertEquals("$kind $size", kind.skeleton(size), sk)
            assertTrue("$kind $size rows", face.rows.size <= sk.rows)
            assertTrue("$kind $size cells", face.cells.size <= sk.cells)
            assertTrue("$kind $size lines", face.lines.size <= sk.lines)
            if (!sk.rowsTappable) assertTrue("$kind $size: compact rows are not tap targets", face.rows.all { it.tap == null })
            assertTrue(face.description.isNotBlank())
            assertTrue("$kind $size description", face.description.endsWith("Opens ${text.appName}"))
        }
    }

    @Test fun `larger sizes show more`() {
        for (kind in listOf(WidgetKind.INCIDENT_LIST, WidgetKind.REPLIES)) {
            val counts = WidgetSize.entries.map { f(kind, it).rows.size }
            assertEquals("$kind", listOf(0, 0, 3, 4), counts)
        }
        assertEquals(listOf(0, 0, 4, 4), WidgetSize.entries.map { f(WidgetKind.OPEN_INCIDENTS, it).cells.size })
        assertEquals(listOf(0, 0, 0, 3), WidgetSize.entries.map { f(WidgetKind.OPEN_INCIDENTS, it).rows.size })
        assertEquals(listOf(0, 0, 2, 4), WidgetSize.entries.map { f(WidgetKind.TOP_INCIDENT, it).lines.size })
        assertEquals(listOf(0, 0, 3, 3), WidgetSize.entries.map { f(WidgetKind.SYNC_STATUS, it).rows.size })
    }

    @Test fun `first run asks for a source and taps through to Projects, at every size`() {
        val snap = WidgetSnapshot(firstRun = true)
        for (kind in WidgetKind.entries) for (size in WidgetSize.entries) {
            val face = f(kind, size, snap)
            assertEquals(Skeleton.message(size), face.skeleton)
            assertEquals(WidgetTap.Projects, face.tap)
            assertEquals("No sources set up yet", face.text)
        }
        assertEquals("Add a Play app, a GitHub repo or a mail filter to a project.", f(WidgetKind.TODAY, WidgetSize.MEDIUM, snap).caption)
        assertNull("the one-line and small sizes drop the help line", f(WidgetKind.TODAY, WidgetSize.SMALL, snap).caption)
    }

    @Test fun `error state says so in words and invents nothing, at every size`() {
        for (kind in WidgetKind.entries) for (size in WidgetSize.entries) {
            val face = faces.error(kind, size)
            assertEquals(Skeleton.message(size), face.skeleton)
            assertEquals("Can't read the data", face.text)
            assertNull(face.value)
            assertTrue(face.rows.isEmpty() && face.cells.isEmpty())
        }
        assertEquals("Open csapp to see what went wrong. Nothing was changed.", faces.error(WidgetKind.REPLIES, WidgetSize.LARGE).caption)
    }

    @Test fun `copy names the product only through app_name`() {
        for ((name, value) in text.strings) {
            if (name != "app_name" && name.startsWith("widget_")) assertFalse("$name spells the product name", value.contains(text.appName))
        }
    }

    // ---- freshness ("as of") ----

    @Test fun `data widgets print how fresh the synced data is, not when they were drawn`() {
        for (kind in dataKinds) {
            assertEquals("$kind", "Synced 13:50", f(kind, WidgetSize.MEDIUM, fresh).asOf)
            assertEquals("$kind", "Synced 13:50", f(kind, WidgetSize.LARGE, fresh).asOf)
            assertNull("$kind: small sizes stay quiet while fresh", f(kind, WidgetSize.SMALL, fresh).asOf)
            assertNull("$kind", f(kind, WidgetSize.TINY, fresh).asOf)
        }
    }

    @Test fun `a channel that keeps failing makes every data widget say stale, at every size`() {
        // Play was polled at 13:50 but last worked fully yesterday at 09:00: attempted is not fresh.
        for (kind in dataKinds) {
            assertEquals("$kind", "◌ Stale · Tue 09:00", f(kind, WidgetSize.MEDIUM).asOf)
            assertEquals("$kind", "◌ Stale · Tue 09:00", f(kind, WidgetSize.SMALL).asOf)
            assertEquals("$kind", "◌ Stale", f(kind, WidgetSize.TINY).asOf)
        }
        // Offline: the last full sync of every channel is older than two intervals + 15 min.
        val later = WidgetFaces(text, TestClock.clock(TestClock.at(2026, 11, 4, 16, 0)))
        assertEquals("◌ Stale · 13:50", later.face(WidgetKind.OPEN_INCIDENTS, WidgetSize.MEDIUM, fresh).asOf)
        // A channel that never worked fully.
        val never = fresh.copy(sync = fresh.sync.copy(health = PollHealth(mapOf(PollChannel.PLAY to ChannelHealth(TestClock.NOW, 1, 1, null)))))
        assertEquals("◌ Stale · sync failing", f(WidgetKind.TODAY, WidgetSize.SMALL, never).asOf)
    }

    @Test fun `not synced yet and no sources`() {
        val notYet = empty.copy(sync = SyncInfo(configured = mapOf(PollChannel.GITHUB to 1)))
        assertEquals("○ Not synced yet", f(WidgetKind.INCIDENT_LIST, WidgetSize.MEDIUM, notYet).asOf)
        assertEquals("○ Not synced yet", f(WidgetKind.TOP_INCIDENT, WidgetSize.SMALL, notYet).asOf)
        assertEquals("○ Not synced", f(WidgetKind.REPLIES, WidgetSize.TINY, notYet).asOf)
        // Nothing set up: everything shown is local and can't lapse, so no line at all.
        for (kind in WidgetKind.entries) for (size in WidgetSize.entries) assertNull("$kind $size", f(kind, size, empty).asOf)
        // Sync status prints a time per channel instead of a header line.
        assertNull(f(WidgetKind.SYNC_STATUS, WidgetSize.MEDIUM).asOf)
    }

    // ---- one line ----

    @Test fun `the one-line size is a single tappable summary, severity word first`() {
        for (kind in WidgetKind.entries) {
            val face = f(kind, WidgetSize.TINY)
            assertEquals(Skeleton.LINE_TINY, face.skeleton)
            assertTrue(face.lead == null && face.caption == null && face.rows.isEmpty())
        }
        assertEquals("6 open incidents · ◆ 1 Critical", f(WidgetKind.OPEN_INCIDENTS, WidgetSize.TINY).text)
        val list = f(WidgetKind.INCIDENT_LIST, WidgetSize.TINY)
        assertEquals("◆ Critical · 6 open · Title of a", list.text)
        assertEquals(WidgetTap.Incident("a"), list.tap)
        assertEquals("◆ Critical · Title of a", f(WidgetKind.TOP_INCIDENT, WidgetSize.TINY).text)
        assertEquals("5 replies need you · ✕ Failed to send: 2", f(WidgetKind.REPLIES, WidgetSize.TINY).text)
        val sync = f(WidgetKind.SYNC_STATUS, WidgetSize.TINY)
        assertEquals("✕ 1 channel failed", sync.text)
        assertEquals("13:50", sync.asOf)
        assertEquals("8 signals today", f(WidgetKind.TODAY, WidgetSize.TINY).text)
        assertEquals("Nothing open", f(WidgetKind.TOP_INCIDENT, WidgetSize.TINY, empty).text)
        assertEquals(WidgetTap.Home(), f(WidgetKind.INCIDENT_LIST, WidgetSize.TINY, empty).tap)
    }

    // ---- Open incidents ----

    @Test fun `open incidents counts live incidents by severity with glyph and word`() {
        val small = f(WidgetKind.OPEN_INCIDENTS, WidgetSize.SMALL)
        assertEquals("6", small.value)
        assertEquals("open incidents", small.label)
        assertEquals("◆ 1 Critical", small.caption)

        val medium = f(WidgetKind.OPEN_INCIDENTS, WidgetSize.MEDIUM)
        assertEquals(listOf("1" to "◆ Critical", "2" to "◇ High", "2" to "▲ Medium", "1" to "○ Low"), medium.cells.map { it.value to it.label })

        val large = f(WidgetKind.OPEN_INCIDENTS, WidgetSize.LARGE)
        assertEquals(listOf("Fotoz", "Notes", "Inbox"), large.rows.map { it.text })
        assertEquals("◆ 1 Critical · ◇ 1 High", large.rows[0].meta)
        assertEquals("2", large.rows[0].trail)
        assertEquals(WidgetTap.Home("fotoz"), large.rows[0].tap)
        assertEquals("+1 more", large.footer)
    }

    @Test fun `open incidents empty state`() {
        val small = f(WidgetKind.OPEN_INCIDENTS, WidgetSize.SMALL, empty)
        assertEquals("0", small.value)
        assertEquals("Nothing open", small.caption)
        val medium = f(WidgetKind.OPEN_INCIDENTS, WidgetSize.MEDIUM, empty)
        assertTrue(medium.cells.isEmpty())
        assertEquals("Nothing open", medium.empty)
        assertEquals("Open or acknowledged, all projects", f(WidgetKind.OPEN_INCIDENTS, WidgetSize.LARGE, empty).footer)
    }

    // ---- Incidents list ----

    @Test fun `incident list ranks by severity and opens the incident`() {
        val small = f(WidgetKind.INCIDENT_LIST, WidgetSize.SMALL)
        assertEquals("◆ Critical · 6 open", small.lead)
        assertEquals("Title of a", small.text)
        assertEquals(WidgetTap.Incident("a"), small.tap)

        val medium = f(WidgetKind.INCIDENT_LIST, WidgetSize.MEDIUM)
        assertEquals("Incidents · 6 open", medium.title)
        assertEquals(listOf("Title of a", "Title of c", "Title of b"), medium.rows.map { it.text })
        assertEquals("◇ High · ◐ Acknowledged · Notes", medium.rows[2].meta)
        assertEquals(WidgetTap.Home(), medium.tap)

        val large = f(WidgetKind.INCIDENT_LIST, WidgetSize.LARGE)
        assertEquals(WidgetTap.Incident("d"), large.rows[3].tap)
        assertEquals("Untitled incident", large.rows[3].text)
        assertEquals("+2 more", large.footer)
    }

    @Test fun `withheld titles are replaced by the reason, never the text`() {
        val snap = rich.copy(live = listOf(incident("x", Severity.HIGH).copy(title = ShownTitle.Withheld(WithheldReason.QUOTES_REVIEW))))
        assertEquals("Title hidden: it quotes a Play review", f(WidgetKind.TOP_INCIDENT, WidgetSize.SMALL, snap).text)
        assertEquals("Title hidden: it quotes a Play review", f(WidgetKind.INCIDENT_LIST, WidgetSize.LARGE, snap).rows.single().text)
        val unverified = rich.copy(live = listOf(incident("y", Severity.HIGH).copy(title = ShownTitle.Withheld(WithheldReason.UNVERIFIED))))
        assertEquals("Title hidden: it may quote a review or an email", f(WidgetKind.TOP_INCIDENT, WidgetSize.MEDIUM, unverified).text)
    }

    @Test fun `incident list empty state`() {
        assertEquals("Nothing open", f(WidgetKind.INCIDENT_LIST, WidgetSize.SMALL, empty).text)
        val large = f(WidgetKind.INCIDENT_LIST, WidgetSize.LARGE, empty)
        assertTrue(large.rows.isEmpty())
        assertEquals("Nothing open", large.empty)
        assertNull(large.footer)
    }

    // ---- Top incident ----

    @Test fun `top incident card grows from a mark and a title to channels and dates`() {
        val small = f(WidgetKind.TOP_INCIDENT, WidgetSize.SMALL)
        assertEquals("◆ Critical", small.lead)
        assertEquals(WidgetTap.Incident("a"), small.tap)

        val medium = f(WidgetKind.TOP_INCIDENT, WidgetSize.MEDIUM)
        assertEquals(listOf("◆ Critical · ◯ Open · ↻ Recurring", "Fotoz · 4 signals"), medium.lines)

        val large = f(WidgetKind.TOP_INCIDENT, WidgetSize.LARGE)
        assertEquals("★ 3 Play reviews · ◉ 1 GitHub issue", large.lines[2])
        assertEquals("First seen Mon 09:12 · updated 13:00", large.lines[3])
        assertEquals("+5 more", large.footer)
    }

    @Test fun `top incident empty state`() {
        assertEquals("Nothing open. The most severe incident will show here.", f(WidgetKind.TOP_INCIDENT, WidgetSize.MEDIUM, empty).text)
        assertEquals(WidgetTap.Home(), f(WidgetKind.TOP_INCIDENT, WidgetSize.LARGE, empty).tap)
    }

    // ---- Replies ----

    @Test fun `replies counts what needs the developer, failures first`() {
        val small = f(WidgetKind.REPLIES, WidgetSize.SMALL)
        assertEquals("5", small.value)
        assertEquals("replies need you", small.label)
        assertEquals("✕ Failed to send: 2", small.caption)

        val medium = f(WidgetKind.REPLIES, WidgetSize.MEDIUM)
        assertEquals(
            listOf("✕ Failed to send" to "2", "⋯ Send not finished" to "1", "✎ Waiting for your confirmation" to "2"),
            medium.rows.map { it.text to it.trail },
        )
    }

    @Test fun `reply rows name a star rating and a project only, and open the reply screen`() {
        val large = f(WidgetKind.REPLIES, WidgetSize.LARGE)
        assertEquals(listOf("2★ review", "1★ review", "Play review", "5★ review"), large.rows.map { it.text })
        assertEquals("✕ Failed to send · Fotoz", large.rows[0].meta)
        assertEquals("⋯ Send not finished · Notes", large.rows[2].meta)
        assertEquals(WidgetTap.Reply("play:p:5"), large.rows[0].tap)
        assertEquals("+1 more · ✧ AI drafts to review: 2", large.footer)
    }

    @Test fun `replies empty state and the never-sends footer`() {
        assertEquals("No replies waiting on you", f(WidgetKind.REPLIES, WidgetSize.SMALL, empty).caption)
        assertEquals("No replies waiting on you", f(WidgetKind.REPLIES, WidgetSize.MEDIUM, empty).empty)
        val one = empty.copy(replies = RepliesInfo(listOf(reply("play:p:9", ReplyState.DRAFTED))))
        assertEquals("Opens the reply screen. Sending still asks you to confirm.", f(WidgetKind.REPLIES, WidgetSize.LARGE, one).footer)
    }

    @Test fun `only AI drafts pending - medium shows them, not the empty state`() {
        val snap = empty.copy(replies = RepliesInfo(emptyList(), aiDrafts = 1))
        val medium = f(WidgetKind.REPLIES, WidgetSize.MEDIUM, snap)
        assertNull(medium.empty)
        assertEquals("✧ AI drafts to review", medium.rows.single().text)
        assertEquals("✧ AI drafts to review: 1", f(WidgetKind.REPLIES, WidgetSize.SMALL, snap).caption)
    }

    // ---- Sync status ----

    @Test fun `sync status per channel, in words`() {
        val small = f(WidgetKind.SYNC_STATUS, WidgetSize.SMALL)
        assertEquals("Sync · 13:50", small.lead)
        assertEquals("✕ 1 channel failed", small.text)
        assertNull("the lead already carries the time", small.asOf)

        val medium = f(WidgetKind.SYNC_STATUS, WidgetSize.MEDIUM)
        assertEquals(listOf("◉ GitHub", "★ Play", "✉ Mail"), medium.rows.map { it.text })
        assertEquals(listOf("✓ OK · 13:50", "✕ 1 of 2 failed · 13:50", "— Not set up"), medium.rows.map { it.meta })

        val large = f(WidgetKind.SYNC_STATUS, WidgetSize.LARGE)
        assertEquals("✕ 1 of 2 failed · 13:50 · 2 sources · all OK Tue 09:00", large.rows[1].meta)
        assertEquals("Polls every 30 min while online", large.footer)
    }

    @Test fun `sync small summary - none set up, never polled, stale, all ok`() {
        assertEquals("— No sources set up", f(WidgetKind.SYNC_STATUS, WidgetSize.SMALL, empty).text)
        val configured = SyncInfo(configured = mapOf(com.mbaliga.csapp.data.sync.PollChannel.GITHUB to 1))
        assertEquals("○ Not polled yet", f(WidgetKind.SYNC_STATUS, WidgetSize.SMALL, empty.copy(sync = configured)).text)
        val stale = rich.sync.copy(configured = rich.sync.configured - com.mbaliga.csapp.data.sync.PollChannel.PLAY)
        val later = WidgetFaces(text, TestClock.clock(TestClock.at(2026, 11, 4, 16, 0)))
        assertEquals("◌ Stale: no recent poll", later.face(WidgetKind.SYNC_STATUS, WidgetSize.SMALL, rich.copy(sync = stale)).text)
        assertEquals("◌ Stale · last poll 13:50", later.face(WidgetKind.SYNC_STATUS, WidgetSize.MEDIUM, rich.copy(sync = stale)).rows[0].meta)
        assertEquals("✓ All sources OK", f(WidgetKind.SYNC_STATUS, WidgetSize.SMALL, rich.copy(sync = stale)).text)
    }

    // ---- Today ----

    @Test fun `today counts by channel and project`() {
        val small = f(WidgetKind.TODAY, WidgetSize.SMALL)
        assertEquals("8", small.value)
        assertEquals("signals today", small.label)
        assertEquals("since 00:00", small.caption)

        val medium = f(WidgetKind.TODAY, WidgetSize.MEDIUM)
        assertEquals(listOf("3" to "★ Play", "1" to "◉ GitHub", "3" to "✉ Email", "1" to "✎ Manual"), medium.cells.map { it.value to it.label })

        val large = f(WidgetKind.TODAY, WidgetSize.LARGE)
        assertEquals(listOf("Fotoz", "Notes", "Inbox"), large.rows.map { it.text })
        assertEquals("★ 3 Play reviews · ◉ 1 GitHub issue", large.rows[0].meta)
        assertEquals("+1 more · 4 signals not yet grouped", large.footer)
    }

    @Test fun `today empty state`() {
        val one = f(WidgetKind.TODAY, WidgetSize.SMALL, empty)
        assertEquals("0", one.value)
        assertEquals("signals today", one.label)
        val medium = f(WidgetKind.TODAY, WidgetSize.MEDIUM, empty)
        assertTrue(medium.cells.isEmpty())
        assertEquals("Nothing new today", medium.empty)
        assertEquals("since 00:00", f(WidgetKind.TODAY, WidgetSize.LARGE, empty).footer)
    }

    @Test fun `singular forms`() {
        val snap = empty.copy(live = listOf(incident("only", Severity.LOW)), today = listOf(TodayCount("fotoz", SignalType.MANUAL, 1)))
        assertEquals("open incident", f(WidgetKind.OPEN_INCIDENTS, WidgetSize.SMALL, snap).label)
        assertEquals("signal today", f(WidgetKind.TODAY, WidgetSize.SMALL, snap).label)
        assertEquals("Fotoz · 1 signal", f(WidgetKind.TOP_INCIDENT, WidgetSize.MEDIUM, snap).lines[1])
    }

    // ---- colour never alone ----

    @Test fun `every severity and status a widget prints carries its word`() {
        val printed = WidgetKind.entries.flatMap { k -> WidgetSize.entries.map { f(k, it) } }
            .flatMap { listOfNotNull(it.lead, it.caption, it.text) + it.lines + it.rows.mapNotNull { r -> r.meta } + it.cells.map { c -> c.label } }
        for (sev in Severity.entries) {
            val m = Marks.severity(sev)
            val withGlyph = printed.filter { it.contains(m.glyph) }
            assertTrue("$sev never printed", withGlyph.isNotEmpty())
            assertTrue("$sev glyph without its word: $withGlyph", withGlyph.all { it.contains(m.label) })
        }
        assertFalse(printed.any { it.contains("CRITICAL") || it.contains("ACKNOWLEDGED") })
    }
}

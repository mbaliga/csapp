package com.mbaliga.csapp.ui

import com.mbaliga.csapp.domain.model.IncidentStatus
import com.mbaliga.csapp.domain.model.Severity
import com.mbaliga.csapp.domain.model.SignalType
import com.mbaliga.csapp.ui.components.Marks
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Colour never carries meaning alone: every state has its own glyph AND a word. */
class MarksTest {
    @Test fun `every severity, status and channel has a distinct glyph and a non-blank label`() {
        val sev = Severity.entries.map { Marks.severity(it) }
        val st = IncidentStatus.entries.map { Marks.status(it) }
        val ch = SignalType.entries.map { Marks.channel(it) }
        for (group in listOf(sev, st, ch)) {
            assertTrue(group.all { it.glyph.isNotBlank() && it.label.isNotBlank() })
            assertEquals(group.size, group.map { it.glyph }.toSet().size)
            assertEquals(group.size, group.map { it.label }.toSet().size)
        }
        assertEquals(listOf("◆", "◇", "▲", "○"), listOf(Severity.CRITICAL, Severity.HIGH, Severity.MEDIUM, Severity.LOW).map { Marks.severity(it).glyph })
        assertEquals(listOf(1.0f, 0.8f, 0.6f, 0.4f), listOf(Severity.CRITICAL, Severity.HIGH, Severity.MEDIUM, Severity.LOW).map { Marks.severityLuminance(it) })
    }

    @Test fun `ui sources render severity and status only through the Tag composables or Marks (never raw enum names or colour)`() {
        val offenders = File("src/main/java/com/mbaliga/csapp/ui").walkTopDown().filter { it.extension == "kt" && it.name != "Tags.kt" && it.name != "Marks.kt" }
            .filter { f -> Regex("""Text\("\$\{[a-z.]*\.(severity|status)\}""").containsMatchIn(f.readText()) }.map { it.name }.toList()
        assertTrue("raw enum text in: $offenders", offenders.isEmpty())
    }

    @Test fun `no single-colour state - UI never maps severity or status to Color directly`() {
        val bad = File("src/main/java/com/mbaliga/csapp/ui").walkTopDown().filter { it.extension == "kt" }
            .filter { Regex("""Severity\.\w+\s*->\s*Color\(""").containsMatchIn(it.readText()) || Regex("""IncidentStatus\.\w+\s*->\s*Color\(""").containsMatchIn(it.readText()) }.toList()
        assertTrue(bad.toString(), bad.isEmpty())
    }
}

package com.mbaliga.csapp.ui.components

import com.mbaliga.csapp.domain.model.IncidentStatus
import com.mbaliga.csapp.domain.model.Severity
import com.mbaliga.csapp.domain.model.SignalType

/** Colour never carries meaning alone: every state is glyph + word (+ luminance for severity). */
data class Mark(val glyph: String, val label: String)

object Marks {
    fun severity(s: Severity) = when (s) {
        Severity.CRITICAL -> Mark("◆", "Critical")
        Severity.HIGH -> Mark("◇", "High")
        Severity.MEDIUM -> Mark("▲", "Medium")
        Severity.LOW -> Mark("○", "Low")
    }

    /** Luminance step (alpha of the text colour): 100 / 80 / 60 / 40 %. */
    fun severityLuminance(s: Severity) = when (s) {
        Severity.CRITICAL -> 1.0f; Severity.HIGH -> 0.8f; Severity.MEDIUM -> 0.6f; Severity.LOW -> 0.4f
    }

    fun status(s: IncidentStatus) = when (s) {
        IncidentStatus.OPEN -> Mark("◯", "Open")
        IncidentStatus.ACKNOWLEDGED -> Mark("◐", "Acknowledged")
        IncidentStatus.RESOLVED -> Mark("✓", "Resolved")
        IncidentStatus.DISMISSED -> Mark("✕", "Dismissed")
        IncidentStatus.MERGED -> Mark("⇢", "Merged")
    }

    fun channel(t: SignalType) = when (t) {
        SignalType.PLAY_REVIEW -> Mark("★", "Play review")
        SignalType.GITHUB_ISSUE -> Mark("◉", "GitHub issue")
        SignalType.EMAIL -> Mark("✉", "Email")
        SignalType.MANUAL -> Mark("✎", "Manual")
    }
}

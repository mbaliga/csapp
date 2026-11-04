package com.mbaliga.csapp.widget

import com.mbaliga.csapp.domain.model.SignalType

/**
 * A home screen is a public surface. The app's widgets show incident titles and counts only: never a
 * reviewer's name, an email address, or review / mail text.
 *
 * Clustering titles a new incident with its anchor signal's title, or its body when the title is blank
 * (`ClusteringEngine.deriveTitle`, `IncidentRepository.split`). For a Play review fetched from the API
 * that title is a generic "4★ review", but a monthly-report backfill carries the reviewer's own review
 * title, and an email's title is its subject line. The title stays when its anchor signal later leaves
 * the incident (a split, or a move to another project), so the signals still in the incident can't prove
 * a title is safe.
 *
 * So this is an allow-list, checked in this order:
 * 1. a title holding an email address is withheld; the generic star label of an API-fetched review is shown;
 * 2. a title matching the title or body of a Play review or email still in the incident is withheld;
 * 3. a title that is (a truncation of) the title, or blank-title body, of a GitHub issue or manual signal
 *    still in the incident is shown (GitHub issues are public already);
 * 4. a title that starts any Play review's or email's title or body anywhere in the database is withheld
 *    (signals are never deleted, so this also catches an anchor that was split or moved out, and an
 *    accepted AI triage that kept the old title or echoed a subject line);
 * 5. a title the developer wrote (a manual incident) or accepted (an accepted AI triage's title) is shown;
 * 6. anything else is withheld.
 */
object WidgetPrivacy {
    private val GENERIC_REVIEW_TITLE = Regex("""^[0-9?]★ review$""")
    private val EMAIL_ADDRESS = Regex("""[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(\.[A-Za-z0-9-]+)+""")

    /** A member signal's channel and text, as far as the comparison needs it. */
    data class Member(val type: SignalType, val title: String, val bodyStart: String)

    /**
     * @param isManual the incident was created by hand (`IncidentEntity.isManual`).
     * @param members the signals currently in the incident.
     * @param acceptedTitles titles of AI triage suggestions the developer accepted for this incident.
     * @param quotedElsewhere the channel of a Play review or email anywhere whose title or body starts
     *   with this title (a lookup by [likePrefix]), or null when there is none.
     */
    fun shownTitle(
        title: String,
        isManual: Boolean = false,
        members: List<Member> = emptyList(),
        acceptedTitles: List<String> = emptyList(),
        quotedElsewhere: SignalType? = null,
    ): ShownTitle {
        val t = normalise(title)
        if (t.isEmpty()) return ShownTitle.Text("")
        if (EMAIL_ADDRESS.containsMatchIn(title)) return ShownTitle.Withheld(WithheldReason.CONTAINS_ADDRESS)
        if (GENERIC_REVIEW_TITLE.matches(title.trim())) return ShownTitle.Text(title.trim())

        // Deny: the title matches review or mail text still in the incident.
        for (m in members) {
            if (m.type != SignalType.PLAY_REVIEW && m.type != SignalType.EMAIL) continue
            val memberTitle = m.title.trim()
            if (m.type == SignalType.PLAY_REVIEW && GENERIC_REVIEW_TITLE.matches(memberTitle)) continue
            val candidates = listOf(memberTitle, m.bodyStart.trim()).map(::normalise).filter { it.isNotEmpty() }
            if (candidates.any { c -> c.startsWith(t) || t.startsWith(c) }) {
                return ShownTitle.Withheld(if (m.type == SignalType.EMAIL) WithheldReason.QUOTES_EMAIL else WithheldReason.QUOTES_REVIEW)
            }
        }

        // Allow: a public or developer-written signal still in the incident carries this title.
        val fromMember = members.any { m ->
            (m.type == SignalType.GITHUB_ISSUE || m.type == SignalType.MANUAL) &&
                normalise(m.title.ifBlank { m.bodyStart }).let { it.isNotEmpty() && it.startsWith(t) }
        }
        if (fromMember) return ShownTitle.Text(title.trim())

        // Deny: review or mail text elsewhere starts with this title.
        when (quotedElsewhere) {
            SignalType.PLAY_REVIEW -> return ShownTitle.Withheld(WithheldReason.QUOTES_REVIEW)
            SignalType.EMAIL -> return ShownTitle.Withheld(WithheldReason.QUOTES_EMAIL)
            else -> Unit
        }

        // Allow: the developer wrote or accepted this title.
        val vouched = isManual || acceptedTitles.any { normalise(it) == t }
        return if (vouched) ShownTitle.Text(title.trim()) else ShownTitle.Withheld(WithheldReason.UNVERIFIED)
    }

    /**
     * A SQL LIKE pattern (escape character `\`) matching text that starts with [title] as clustering
     * stored it (trimmed, without the "..." of a truncation). Null for a blank title.
     */
    fun likePrefix(title: String): String? {
        val p = title.trim().removeSuffix("...").trim()
        if (p.isEmpty()) return null
        return p.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"
    }

    /** Case- and whitespace-insensitive, without the "..." clustering appends when it truncates. */
    private fun normalise(s: String): String =
        s.trim().removeSuffix("...").trim().replace(Regex("""\s+"""), " ").lowercase()
}

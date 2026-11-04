package com.mbaliga.csapp.domain.ai

/** Post-filter for AI reply drafts: 350 chars, no PII echo, no promises. */
object DraftFilter {
    const val MAX = 350
    private val email = Regex("""[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}""")
    private val phone = Regex("""\+?\d[\d\s().-]{7,}\d""")
    private val promise = Regex(
        """(?i)\b(refund|reimburse|compensat|we (will|'ll) (fix|release|ship|deliver)|will be (fixed|released|resolved)|within \d+ ?(hours?|days?|weeks?)|by (monday|tuesday|wednesday|thursday|friday|tomorrow|next week|end of))\b"""
    )

    class Rejected(message: String) : Exception(message)

    fun clean(raw: String): String {
        var t = raw.trim().removeSurrounding("\"").trim()
        t = email.replace(t, "[contact removed]")
        t = phone.replace(t, "[number removed]")
        if (promise.containsMatchIn(t)) throw Rejected("Draft contained a promise (date/refund/fix); not offered")
        if (t.length > MAX) {
            val cut = t.take(MAX)
            val end = maxOf(cut.lastIndexOf('.'), cut.lastIndexOf('!'), cut.lastIndexOf('?'))
            t = if (end > MAX / 2) cut.take(end + 1) else cut.take(MAX - 1).trimEnd() + "…"
        }
        if (t.isBlank()) throw Rejected("Draft was empty")
        return t
    }
}

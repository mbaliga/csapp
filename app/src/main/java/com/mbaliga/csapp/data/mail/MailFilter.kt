package com.mbaliga.csapp.data.mail

/**
 * Routes mail to a project. Filter grammar (case-insensitive), terms joined by `OR`:
 *   subject contains "[Fotoz]"   from:alice@x.com   to:fotoz@x.com   body contains "crash"
 *   "[Fotoz]"  (bare quoted string = subject contains)
 * A message matches when ANY term matches.
 */
object MailFilter {
    fun matches(filter: String, m: MailMessage): Boolean {
        val terms = filter.split(Regex("\\s+OR\\s+", RegexOption.IGNORE_CASE)).map { it.trim() }.filter { it.isNotEmpty() }
        return terms.any { termMatches(it, m) }
    }

    private fun termMatches(term: String, m: MailMessage): Boolean {
        Regex("""(?i)^(subject|body)\s+contains\s+"(.*)"$""").find(term)?.let {
            val hay = if (it.groupValues[1].equals("subject", true)) m.subject else m.bodyText
            return hay.contains(it.groupValues[2], ignoreCase = true)
        }
        Regex("""(?i)^from:\s*(\S+)$""").find(term)?.let { return m.from.contains(it.groupValues[1], true) }
        Regex("""(?i)^to:\s*(\S+)$""").find(term)?.let { v -> return m.to.any { it.contains(v.groupValues[1], true) } }
        Regex("""^"(.*)"$""").find(term)?.let { return m.subject.contains(it.groupValues[1], true) }
        return false
    }
}

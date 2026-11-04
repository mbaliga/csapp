package com.mbaliga.csapp.widget

import com.mbaliga.csapp.data.sync.PollChannel
import com.mbaliga.csapp.domain.model.ReplyState
import com.mbaliga.csapp.domain.model.Severity
import com.mbaliga.csapp.domain.model.SignalType
import com.mbaliga.csapp.ui.components.Marks

/**
 * Turns a [WidgetSnapshot] into what each widget shows at each size. Severity, status and channel
 * always appear as the app's own glyph + word ([Marks]); nothing relies on colour. Larger sizes show
 * more: one line, then rows grow 1 -> 3 -> 4, a stat gains its breakdown, then per-project rows.
 *
 * Every widget fed by synced data says how fresh that data is ([SyncFreshness.data]): medium and large
 * print it in the header ("Synced 14:05", "◌ Stale · Tue 14:05", "○ Not synced yet"); the small and
 * one-line sizes print it only when it is stale or not synced yet.
 */
class WidgetFaces(private val t: WidgetText, private val clock: WidgetClock) {

    fun face(kind: WidgetKind, size: WidgetSize, snap: WidgetSnapshot): WidgetFace {
        if (snap.firstRun) return firstRun(kind, size)
        val sk = kind.skeleton(size)
        return when (kind) {
            WidgetKind.OPEN_INCIDENTS -> openIncidents(sk, size, snap)
            WidgetKind.INCIDENT_LIST -> incidentList(sk, size, snap)
            WidgetKind.TOP_INCIDENT -> topIncident(sk, size, snap)
            WidgetKind.REPLIES -> replies(sk, size, snap)
            WidgetKind.SYNC_STATUS -> syncStatus(sk, size, snap)
            WidgetKind.TODAY -> today(sk, size, snap)
        }
    }

    fun name(kind: WidgetKind): String = t.get(
        when (kind) {
            WidgetKind.OPEN_INCIDENTS -> WS.OPEN_NAME
            WidgetKind.INCIDENT_LIST -> WS.LIST_NAME
            WidgetKind.TOP_INCIDENT -> WS.TOP_NAME
            WidgetKind.REPLIES -> WS.REPLIES_NAME
            WidgetKind.SYNC_STATUS -> WS.SYNC_NAME
            WidgetKind.TODAY -> WS.TODAY_NAME
        }
    )

    /** The app's data couldn't be read; says so instead of showing old or invented numbers. */
    fun error(kind: WidgetKind, size: WidgetSize): WidgetFace =
        message(kind, size, t.get(WS.ERROR_TITLE), t.get(WS.ERROR_BODY, t.appName), WidgetTap.Home())

    private fun firstRun(kind: WidgetKind, size: WidgetSize): WidgetFace =
        message(kind, size, t.get(WS.FIRST_RUN_TITLE), t.get(WS.FIRST_RUN_BODY), WidgetTap.Projects)

    /** Name, headline and help; the small size drops the help, the one-line size the name as well. */
    private fun message(kind: WidgetKind, size: WidgetSize, headline: String, body: String, tap: WidgetTap): WidgetFace {
        val sk = Skeleton.message(size)
        return build(
            when (size) {
                WidgetSize.TINY -> WidgetFace(sk, name(kind), tap, "", text = headline)
                WidgetSize.SMALL -> WidgetFace(sk, name(kind), tap, "", lead = name(kind), text = headline)
                else -> WidgetFace(sk, name(kind), tap, "", lead = name(kind), text = headline, caption = body)
            }
        )
    }

    // ---- Open incidents (stat) ----

    private fun openIncidents(sk: Skeleton, size: WidgetSize, snap: WidgetSnapshot): WidgetFace {
        val live = snap.live
        val n = live.size
        val counts = SEVERITY_ORDER.map { sev -> sev to live.count { it.severity == sev } }
        val base = WidgetFace(sk, name(WidgetKind.OPEN_INCIDENTS), WidgetTap.Home(), "", value = num(n), label = t.plural(WP.OPEN_COUNT, n))
        val caption = counts.firstOrNull { it.second > 0 }?.let { (sev, c) -> severityCount(sev, c) } ?: t.get(WS.OPEN_EMPTY)
        if (size == WidgetSize.TINY) return tiny(base, t.get(WS.JOIN, valueLabel(base), caption), snap)
        if (size == WidgetSize.SMALL) return build(base.copy(caption = caption, asOf = problemAsOf(snap)))
        val cells = if (n == 0) emptyList() else counts.map { (sev, c) -> FaceCell(num(c), mark(sev)) }
        var face = base.copy(asOf = headerAsOf(snap), cells = cells, empty = if (n == 0) t.get(WS.OPEN_EMPTY) else null)
        if (size == WidgetSize.LARGE) {
            val byProject = live.groupBy { it.projectId }.entries.sortedWith(
                compareByDescending<Map.Entry<String, List<LiveIncident>>> { e -> e.value.maxOf { it.severity.ordinal } }
                    .thenByDescending { it.value.size }
                    .thenBy { snap.projectName(it.key).lowercase() }
            )
            val rows = byProject.take(sk.rows).map { (pid, incs) ->
                FaceRow(
                    text = snap.projectName(pid),
                    meta = SEVERITY_ORDER.mapNotNull { sev -> incs.count { it.severity == sev }.takeIf { it > 0 }?.let { severityCount(sev, it) } }.joinToString(" · "),
                    trail = num(incs.size),
                    tap = WidgetTap.Home(pid),
                )
            }
            val more = byProject.size - rows.size
            face = face.copy(rows = rows, footer = if (more > 0) t.get(WS.MORE, more) else t.get(WS.OPEN_SCOPE))
        }
        return build(face)
    }

    // ---- Incidents (list) ----

    private fun incidentList(sk: Skeleton, size: WidgetSize, snap: WidgetSnapshot): WidgetFace {
        val live = snap.live
        val name = name(WidgetKind.INCIDENT_LIST)
        if (size == WidgetSize.TINY || size == WidgetSize.SMALL) {
            val top = live.firstOrNull()
            val lead = top?.let { t.get(WS.JOIN, mark(it.severity), t.get(WS.N_OPEN, live.size)) }
            val text = top?.let(::title) ?: t.get(WS.LIST_EMPTY)
            val face = WidgetFace(sk, name, top?.let { WidgetTap.Incident(it.id) } ?: WidgetTap.Home(), "")
            return if (size == WidgetSize.TINY) tiny(face, listOfNotNull(lead, text).joinToString(" · "), snap)
            else build(face.copy(lead = lead ?: name, text = text, asOf = problemAsOf(snap)))
        }
        val header = if (live.isEmpty()) name else t.get(WS.JOIN, name, t.get(WS.N_OPEN, live.size))
        val shown = live.take(sk.rows)
        val rows = shown.map { inc ->
            FaceRow(
                text = title(inc),
                meta = listOf(mark(inc.severity), statusMark(inc), snap.projectName(inc.projectId)).joinToString(" · "),
                tap = if (sk.rowsTappable) WidgetTap.Incident(inc.id) else null,
            )
        }
        val more = live.size - shown.size
        return build(
            WidgetFace(
                sk, header, WidgetTap.Home(), "", asOf = headerAsOf(snap), rows = rows,
                empty = if (live.isEmpty()) t.get(WS.LIST_EMPTY) else null,
                footer = if (sk.footer && more > 0) t.get(WS.MORE, more) else null,
            )
        )
    }

    // ---- Top incident (card) ----

    private fun topIncident(sk: Skeleton, size: WidgetSize, snap: WidgetSnapshot): WidgetFace {
        val name = name(WidgetKind.TOP_INCIDENT)
        val top = snap.live.firstOrNull()
        if (top == null) {
            val home = WidgetFace(sk, name, WidgetTap.Home(), "")
            return when (size) {
                WidgetSize.TINY -> tiny(home, t.get(WS.OPEN_EMPTY), snap)
                WidgetSize.SMALL -> build(home.copy(lead = name, text = t.get(WS.TOP_EMPTY), asOf = problemAsOf(snap)))
                else -> build(home.copy(asOf = headerAsOf(snap), text = t.get(WS.TOP_EMPTY)))
            }
        }
        val tap = WidgetTap.Incident(top.id)
        if (size == WidgetSize.TINY) return tiny(WidgetFace(sk, name, tap, ""), t.get(WS.JOIN, mark(top.severity), title(top)), snap)
        if (size == WidgetSize.SMALL) return build(WidgetFace(sk, name, tap, "", lead = mark(top.severity), text = title(top), asOf = problemAsOf(snap)))
        val marks = buildList {
            add(mark(top.severity)); add(statusMark(top))
            if (top.isRecurring) add(t.get(WS.TOP_RECURRING))
        }.joinToString(" · ")
        val project = snap.projectName(top.projectId)
        val lines = if (size == WidgetSize.MEDIUM) {
            listOf(marks, t.get(WS.JOIN, project, t.plural(WP.SIGNALS, top.signalCount, top.signalCount)))
        } else {
            listOf(
                marks,
                project,
                channelCounts(top.channelCounts).ifEmpty { t.plural(WP.SIGNALS, 0, 0) },
                t.get(WS.JOIN, t.get(WS.TOP_FIRST_SEEN, clock.time(top.createdAt)), t.get(WS.TOP_UPDATED, clock.time(top.updatedAt))),
            )
        }
        val more = snap.live.size - 1
        return build(
            WidgetFace(
                sk, name, tap, "", asOf = headerAsOf(snap), text = title(top), lines = lines.take(sk.lines),
                footer = if (sk.footer && more > 0) t.get(WS.MORE, more) else null,
            )
        )
    }

    // ---- Replies (stat / list). Read-only: taps open the app, never send. ----

    private fun replies(sk: Skeleton, size: WidgetSize, snap: WidgetSnapshot): WidgetFace {
        val r = snap.replies
        val name = name(WidgetKind.REPLIES)
        val categories = listOf(
            t.get(WS.REPLY_FAILED) to r.failed,
            t.get(WS.REPLY_SENDING) to r.sending,
            t.get(WS.REPLY_AWAITING) to r.awaiting,
            t.get(WS.REPLY_AI) to r.aiDrafts,
        ).filter { it.second > 0 }
        if (size == WidgetSize.TINY || size == WidgetSize.SMALL) {
            val first = categories.firstOrNull()
            val face = WidgetFace(
                sk, name, WidgetTap.Home(), "", value = num(r.needYou), label = t.plural(WP.REPLIES_COUNT, r.needYou),
                caption = first?.let { t.get(WS.COUNT_OF, it.first, num(it.second)) } ?: t.get(WS.REPLIES_EMPTY),
            )
            return if (size == WidgetSize.TINY) tiny(face, t.get(WS.JOIN, valueLabel(face), face.caption!!), snap)
            else build(face.copy(asOf = problemAsOf(snap)))
        }
        val empty = if (r.needYou == 0 && r.aiDrafts == 0) t.get(WS.REPLIES_EMPTY) else null
        if (size == WidgetSize.MEDIUM) {
            val rows = categories.take(sk.rows).map { (label, n) -> FaceRow(text = label, trail = num(n)) }
            return build(WidgetFace(sk, name, WidgetTap.Home(), "", asOf = headerAsOf(snap), rows = rows, empty = empty))
        }
        val items = r.items.sortedWith(compareBy<ReplyItem> { REPLY_ORDER.indexOf(it.state) }.thenByDescending { it.createdAt })
        val rows = items.take(sk.rows).map { item ->
            FaceRow(
                text = item.rating?.let { t.get(WS.REPLY_RATED, it) } ?: t.get(WS.REPLY_UNRATED),
                meta = t.get(WS.JOIN, replyStateLabel(item.state), snap.projectName(item.projectId)),
                tap = WidgetTap.Reply(item.sourceKey),
            )
        }
        val more = items.size - rows.size
        val footer = listOfNotNull(
            if (more > 0) t.get(WS.MORE, more) else null,
            if (r.aiDrafts > 0) t.get(WS.COUNT_OF, t.get(WS.REPLY_AI), num(r.aiDrafts)) else null,
        ).joinToString(" · ").ifEmpty { t.get(WS.REPLY_NEVER_SENDS) }
        return build(
            WidgetFace(
                sk, name, WidgetTap.Home(), "", asOf = headerAsOf(snap), rows = rows,
                empty = if (rows.isEmpty()) t.get(WS.REPLIES_EMPTY) else null, footer = footer,
            )
        )
    }

    private fun replyStateLabel(state: ReplyState): String = when (state) {
        ReplyState.FAILED -> t.get(WS.REPLY_FAILED)
        ReplyState.SEND_CONFIRMED -> t.get(WS.REPLY_SENDING)
        else -> t.get(WS.REPLY_AWAITING)
    }

    // ---- Sync status (list) ----

    private fun syncStatus(sk: Skeleton, size: WidgetSize, snap: WidgetSnapshot): WidgetFace {
        val name = name(WidgetKind.SYNC_STATUS)
        val statuses = SyncFreshness.all(snap.sync, clock.now)
        if (size == WidgetSize.TINY || size == WidgetSize.SMALL) {
            val lastAttempt = statuses.filter { it.configured > 0 }.mapNotNull { it.health?.lastAttemptAt }.maxOrNull()
            val failed = statuses.count { it.state == ChannelState.FAILED }
            val text = when {
                statuses.all { it.state == ChannelState.NOT_SET_UP } -> t.get(WS.SYNC_NONE)
                failed > 0 -> t.plural(WP.SYNC_CHANNELS_FAILED, failed, failed)
                statuses.any { it.state == ChannelState.STALE } -> t.get(WS.SYNC_SOME_STALE)
                statuses.any { it.state == ChannelState.NEVER } -> t.get(WS.SYNC_NEVER)
                else -> t.get(WS.SYNC_ALL_OK)
            }
            // The one-line size puts the state first and the last poll time at the end.
            if (size == WidgetSize.TINY) return build(WidgetFace(sk, name, WidgetTap.Home(), "", text = text, asOf = lastAttempt?.let(clock::time)))
            return build(WidgetFace(sk, name, WidgetTap.Home(), "", lead = lastAttempt?.let { t.get(WS.SYNC_LEAD, clock.time(it)) } ?: name, text = text))
        }
        val rows = statuses.take(sk.rows).map { st ->
            val state = stateText(st)
            val meta = if (size == WidgetSize.LARGE && st.state != ChannelState.NOT_SET_UP) {
                listOf(
                    state,
                    t.plural(WP.SYNC_SOURCES, st.configured, st.configured),
                    st.health?.lastAllOkAt?.let { t.get(WS.SYNC_LAST_OK, clock.time(it)) } ?: t.get(WS.SYNC_LAST_OK_NEVER),
                ).joinToString(" · ")
            } else state
            FaceRow(text = channelName(st.channel), meta = meta)
        }
        return build(
            // No header time: every row prints its own channel's last poll.
            WidgetFace(
                sk, name, WidgetTap.Home(), "", rows = rows,
                footer = if (sk.footer) t.get(WS.SYNC_EVERY, snap.sync.intervalMinutes) else null,
            )
        )
    }

    private fun stateText(st: ChannelStatus): String {
        val h = st.health
        return when (st.state) {
            ChannelState.NOT_SET_UP -> t.get(WS.SYNC_NOT_SET_UP)
            ChannelState.NEVER -> t.get(WS.SYNC_NEVER)
            ChannelState.STALE -> t.get(WS.SYNC_STALE, clock.time(h!!.lastAttemptAt!!))
            ChannelState.FAILED -> t.get(WS.SYNC_FAILED, h!!.failed, h.attempted, clock.time(h.lastAttemptAt!!))
            ChannelState.OK -> t.get(WS.SYNC_OK, clock.time(h!!.lastAttemptAt!!))
        }
    }

    private fun channelName(c: PollChannel): String = when (c) {
        PollChannel.GITHUB -> "${Marks.channel(SignalType.GITHUB_ISSUE).glyph} ${t.get(WS.SYNC_GITHUB)}"
        PollChannel.PLAY -> "${Marks.channel(SignalType.PLAY_REVIEW).glyph} ${t.get(WS.SYNC_PLAY)}"
        PollChannel.MAIL -> "${Marks.channel(SignalType.EMAIL).glyph} ${t.get(WS.SYNC_MAIL)}"
    }

    // ---- Today (stat) ----

    private fun today(sk: Skeleton, size: WidgetSize, snap: WidgetSnapshot): WidgetFace {
        val total = snap.today.sumOf { it.n }
        val since = t.get(WS.TODAY_SINCE, clock.time(clock.dayStart()))
        val base = WidgetFace(sk, name(WidgetKind.TODAY), WidgetTap.Home(), "", value = num(total), label = t.plural(WP.TODAY_COUNT, total))
        if (size == WidgetSize.TINY) return tiny(base, valueLabel(base), snap)
        if (size == WidgetSize.SMALL) return build(base.copy(caption = since, asOf = problemAsOf(snap)))
        val cells = if (total == 0) emptyList() else CHANNEL_ORDER.map { ch ->
            FaceCell(num(snap.today.filter { it.channel == ch }.sumOf { it.n }), "${Marks.channel(ch).glyph} ${channelShort(ch)}")
        }
        var face = base.copy(asOf = headerAsOf(snap), cells = cells, empty = if (total == 0) t.get(WS.TODAY_EMPTY) else null)
        if (size == WidgetSize.LARGE) {
            val byProject = snap.today.groupBy { it.projectId }.entries
                .sortedWith(compareByDescending<Map.Entry<String, List<TodayCount>>> { e -> e.value.sumOf { it.n } }.thenBy { snap.projectName(it.key).lowercase() })
            val rows = byProject.take(sk.rows).map { (pid, list) ->
                FaceRow(
                    text = snap.projectName(pid),
                    meta = channelCounts(list.groupBy { it.channel }.mapValues { e -> e.value.sumOf { it.n } }),
                    trail = num(list.sumOf { it.n }),
                    tap = WidgetTap.Home(pid),
                )
            }
            val more = byProject.size - rows.size
            val footer = listOfNotNull(
                if (more > 0) t.get(WS.MORE, more) else null,
                if (snap.ungrouped > 0) t.plural(WP.UNGROUPED, snap.ungrouped, snap.ungrouped) else null,
            ).joinToString(" · ").ifEmpty { since }
            face = face.copy(rows = rows, footer = footer)
        }
        return build(face)
    }

    // ---- shared ----

    private fun title(inc: LiveIncident): String = when (val s = inc.title) {
        is ShownTitle.Text -> s.text.ifBlank { t.get(WS.UNTITLED) }
        is ShownTitle.Withheld -> t.get(
            when (s.reason) {
                WithheldReason.QUOTES_REVIEW -> WS.WITHHELD_REVIEW
                WithheldReason.QUOTES_EMAIL -> WS.WITHHELD_EMAIL
                WithheldReason.CONTAINS_ADDRESS -> WS.WITHHELD_ADDRESS
                WithheldReason.UNVERIFIED -> WS.WITHHELD_UNVERIFIED
            }
        )
        // Only the top-ranked incidents carry a title; anything else is never shown, but be safe.
        null -> t.get(WS.UNTITLED)
    }

    private fun mark(sev: Severity) = Marks.severity(sev).let { "${it.glyph} ${it.label}" }
    private fun statusMark(inc: LiveIncident) = Marks.status(inc.status).let { "${it.glyph} ${it.label}" }
    private fun severityCount(sev: Severity, n: Int) = Marks.severity(sev).let { "${it.glyph} ${num(n)} ${it.label}" }

    private fun channelShort(ch: SignalType) = t.get(
        when (ch) {
            SignalType.PLAY_REVIEW -> WS.CH_PLAY
            SignalType.GITHUB_ISSUE -> WS.CH_GITHUB
            SignalType.EMAIL -> WS.CH_EMAIL
            SignalType.MANUAL -> WS.CH_MANUAL
        }
    )

    /** "★ 3 Play reviews · ◉ 1 GitHub issue": glyph and words, zero counts left out. */
    private fun channelCounts(counts: Map<SignalType, Int>): String = CHANNEL_ORDER.mapNotNull { ch ->
        val n = counts[ch] ?: 0
        if (n <= 0) return@mapNotNull null
        val p = when (ch) {
            SignalType.PLAY_REVIEW -> WP.CH_PLAY
            SignalType.GITHUB_ISSUE -> WP.CH_GITHUB
            SignalType.EMAIL -> WP.CH_EMAIL
            SignalType.MANUAL -> WP.CH_MANUAL
        }
        "${Marks.channel(ch).glyph} ${t.plural(p, n, n)}"
    }.joinToString(" · ")

    /** The header's freshness line: always, unless no source is set up (then nothing can lapse). */
    private fun headerAsOf(snap: WidgetSnapshot): String? = when (val f = SyncFreshness.data(snap.sync, clock.now)) {
        null -> null
        is DataFreshness.Synced -> t.get(WS.FRESH_SYNCED, clock.time(f.at))
        is DataFreshness.Stale -> f.since?.let { t.get(WS.FRESH_STALE, clock.time(it)) } ?: t.get(WS.FRESH_FAILING)
        DataFreshness.NotSynced -> t.get(WS.FRESH_NEVER)
    }

    /** The small sizes' freshness line: only when the data is stale or not synced yet. */
    private fun problemAsOf(snap: WidgetSnapshot): String? =
        if (SyncFreshness.data(snap.sync, clock.now).let { it == null || it is DataFreshness.Synced }) null else headerAsOf(snap)

    /** The one-line size's freshness mark (short, so the summary keeps its room). */
    private fun tinyAsOf(snap: WidgetSnapshot): String? = when (SyncFreshness.data(snap.sync, clock.now)) {
        null, is DataFreshness.Synced -> null
        is DataFreshness.Stale -> t.get(WS.FRESH_STALE_SHORT)
        DataFreshness.NotSynced -> t.get(WS.FRESH_NEVER_SHORT)
    }

    /** The one-line face: [text] on the TINY skeleton, keeping [face]'s name and tap. */
    private fun tiny(face: WidgetFace, text: String, snap: WidgetSnapshot): WidgetFace =
        build(WidgetFace(Skeleton.LINE_TINY, face.title, face.tap, "", text = text, asOf = tinyAsOf(snap)))

    /** "6 open incidents". */
    private fun valueLabel(face: WidgetFace): String = t.get(WS.VALUE_LABEL, face.value.orEmpty(), face.label.orEmpty())
    private fun num(n: Int) = String.format(clock.locale, "%d", n)

    /** Fills in the spoken description (TalkBack) from what the face shows, plus where a tap goes. */
    private fun build(face: WidgetFace): WidgetFace {
        val parts = buildList {
            add(face.title)
            if (face.value != null) add(listOfNotNull(face.value, face.label).joinToString(" "))
            face.caption?.let(::add)
            face.lead?.takeIf { it != face.title }?.let(::add)
            face.text?.let(::add)
            face.cells.forEach { add("${it.label}: ${it.value}") }
            face.lines.forEach(::add)
            face.rows.forEach { add(it.description) }
            face.empty?.let(::add)
            face.footer?.let(::add)
            face.asOf?.let(::add)
            add(t.get(WS.OPENS_APP, t.appName))
        }
        return face.copy(description = parts.filter { it.isNotBlank() }.joinToString(". "))
    }

    private companion object {
        val SEVERITY_ORDER = listOf(Severity.CRITICAL, Severity.HIGH, Severity.MEDIUM, Severity.LOW)
        val CHANNEL_ORDER = listOf(SignalType.PLAY_REVIEW, SignalType.GITHUB_ISSUE, SignalType.EMAIL, SignalType.MANUAL)
        val REPLY_ORDER = listOf(ReplyState.FAILED, ReplyState.SEND_CONFIRMED, ReplyState.DRAFTED)
    }
}

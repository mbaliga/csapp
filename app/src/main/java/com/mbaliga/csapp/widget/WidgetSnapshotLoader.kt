package com.mbaliga.csapp.widget

import com.mbaliga.csapp.data.db.dao.WidgetDao
import com.mbaliga.csapp.data.sync.PollChannel
import com.mbaliga.csapp.data.sync.PollHealth
import com.mbaliga.csapp.domain.model.IncidentStatus
import com.mbaliga.csapp.domain.model.ReplyState
import com.mbaliga.csapp.domain.model.Severity
import com.mbaliga.csapp.domain.model.SignalType
import com.mbaliga.csapp.domain.model.SourceKind
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/**
 * Reads everything the widgets show in one pass over [WidgetDao] (read-only queries). Titles are
 * privacy-checked here, before they reach any widget, and only for the incidents a widget can show.
 */
class WidgetSnapshotLoader(
    private val dao: WidgetDao,
    private val pollHealth: () -> PollHealth,
    private val pollIntervalMinutes: () -> Int,
) {
    suspend fun load(clock: WidgetClock): WidgetSnapshot {
        val projects = dao.activeProjects().map { ProjectRef(it.id, it.name) }

        val channelCounts = dao.liveIncidentChannelCounts()
            .groupBy { it.incidentId }
            .mapValues { (_, rows) -> rows.mapNotNull { r -> signalType(r.type)?.let { it to r.n } }.toMap() }
        val ranked = dao.liveIncidents().mapNotNull { e ->
            val severity = enumOrNull<Severity>(e.severity) ?: return@mapNotNull null
            val status = enumOrNull<IncidentStatus>(e.status) ?: return@mapNotNull null
            LiveIncident(
                id = e.id, projectId = e.projectId, severity = severity, status = status,
                isRecurring = e.isRecurringOf != null, createdAt = e.createdAt, updatedAt = e.updatedAt,
                channelCounts = channelCounts[e.id].orEmpty(),
            ) to e
        }.sortedWith { a, b -> WidgetRanking.comparator.compare(a.first, b.first) }

        val titledIds = ranked.take(WidgetRanking.TITLED).map { it.first.id }
        val members = if (titledIds.isEmpty()) emptyMap() else dao.memberTexts(titledIds).groupBy { it.incidentId }
        val accepted = if (titledIds.isEmpty()) emptyMap() else dao.acceptedTriagePayloads(titledIds)
            .mapNotNull { p -> triageTitle(p.payloadJson)?.let { p.incidentId to it } }
            .groupBy({ it.first }, { it.second })
        val live = ranked.mapIndexed { i, (inc, entity) ->
            // Only the incidents a widget can show get a title, and only after the privacy check.
            if (i >= WidgetRanking.TITLED) inc
            else inc.copy(
                title = WidgetPrivacy.shownTitle(
                    entity.title,
                    isManual = entity.isManual,
                    members = members[inc.id].orEmpty().mapNotNull { m -> signalType(m.type)?.let { WidgetPrivacy.Member(it, m.title, m.bodyStart) } },
                    acceptedTitles = accepted[inc.id].orEmpty(),
                    quotedElsewhere = WidgetPrivacy.likePrefix(entity.title)?.let { dao.reviewOrMailStartingWith(it) }?.let(::signalType),
                )
            )
        }

        val replies = RepliesInfo(
            items = dao.repliesNeedingYou().mapNotNull { r ->
                val state = enumOrNull<ReplyState>(r.replyState) ?: return@mapNotNull null
                ReplyItem(r.sourceKey, r.projectId, r.rating, state, r.createdAt)
            },
            aiDrafts = dao.pendingAiReplyDrafts(),
        )

        val today = dao.signalCountsCreatedBetween(clock.dayStart(), clock.nextDayStart())
            .mapNotNull { r -> signalType(r.type)?.let { TodayCount(r.projectId, it, r.n) } }

        val sources = dao.enabledSourceCounts().associate { it.kind to it.n }
        val mailboxes = dao.enabledMailboxCount()
        val configured = mapOf(
            PollChannel.GITHUB to (sources[SourceKind.GITHUB_REPO.name] ?: 0),
            PollChannel.PLAY to (sources[SourceKind.PLAY_PACKAGE.name] ?: 0),
            PollChannel.MAIL to mailboxes,
        )
        val signalTotal = dao.signalCount()
        val firstRun = configured.values.sum() == 0 && signalTotal == 0 && live.isEmpty()

        return WidgetSnapshot(
            projects = projects,
            live = live,
            replies = replies,
            today = today,
            ungrouped = dao.ungroupedSignalCount(),
            sync = SyncInfo(pollHealth(), configured, pollIntervalMinutes()),
            firstRun = firstRun,
        )
    }

    private fun signalType(name: String): SignalType? = enumOrNull<SignalType>(name)

    /** The "title" of a TRIAGE payload (see SuggestionService.suggestTriage); null if absent or unreadable. */
    private fun triageTitle(payloadJson: String): String? = runCatching {
        (Json.parseToJsonElement(payloadJson).jsonObject["title"] as? JsonPrimitive)?.takeIf { it.isString }?.content
    }.getOrNull()?.takeIf { it.isNotBlank() }

    private inline fun <reified E : Enum<E>> enumOrNull(name: String): E? = runCatching { enumValueOf<E>(name) }.getOrNull()
}

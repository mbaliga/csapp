package com.mbaliga.csapp.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import com.mbaliga.csapp.data.db.entities.IncidentEntity

/** An active (not archived) project: id and display name only. */
data class WidgetProjectRow(val id: String, val name: String)

/** Number of signals of one channel attached to one incident. */
data class WidgetIncidentChannelCount(val incidentId: String, val type: String, val n: Int)

/** Number of signals of one channel in one project (for "today"). */
data class WidgetProjectChannelCount(val projectId: String, val type: String, val n: Int)

/**
 * What the title-privacy check needs to know about a member signal: its channel, its title and the
 * start of its body. Never shown on a widget; only compared against the incident title.
 */
data class WidgetMemberText(val incidentId: String, val type: String, val title: String, val bodyStart: String)

/**
 * A Play review whose reply needs the developer. Deliberately carries no author, no review text and
 * no draft text: only identity, project, star rating and reply state.
 */
data class WidgetReplyRow(val sourceKey: String, val projectId: String, val rating: Int?, val replyState: String, val createdAt: Long)

/**
 * An accepted AI triage suggestion's payload, read only to learn the title the developer accepted (the
 * privacy check shows an incident title that equals it). Never shown on a widget.
 */
data class WidgetTriagePayload(val incidentId: String, val payloadJson: String)

/** Enabled sources of one kind (GITHUB_REPO / PLAY_PACKAGE / MAILBOX_FILTER). */
data class WidgetSourceCount(val kind: String, val n: Int)

/**
 * Read-only queries for the home-screen widgets. Nothing here writes; the widgets never change data
 * (BUILD-SPEC §1 rule 1: a widget may open the app, never send, approve or change a reply).
 * No query selects an author name, a review/mail body (beyond the short prefix used only for the
 * title-privacy comparison), a reply draft's text or metadata. The one suggestion payload read is an
 * accepted TRIAGE payload, for its title (never a reply draft's payload).
 */
@Dao
interface WidgetDao {
    @Query("SELECT id, name FROM projects WHERE archived = 0 ORDER BY createdAt ASC")
    suspend fun activeProjects(): List<WidgetProjectRow>

    /** Live = OPEN or ACKNOWLEDGED (the Home screen's own definition), outside archived projects. */
    @Query(
        "SELECT * FROM incidents WHERE status IN ('OPEN', 'ACKNOWLEDGED') " +
            "AND projectId NOT IN (SELECT id FROM projects WHERE archived = 1)"
    )
    suspend fun liveIncidents(): List<IncidentEntity>

    @Query(
        "SELECT s.incidentId AS incidentId, s.type AS type, COUNT(*) AS n FROM signals s " +
            "JOIN incidents i ON i.id = s.incidentId WHERE i.status IN ('OPEN', 'ACKNOWLEDGED') " +
            "GROUP BY s.incidentId, s.type"
    )
    suspend fun liveIncidentChannelCounts(): List<WidgetIncidentChannelCount>

    @Query("SELECT incidentId AS incidentId, type, title, substr(body, 1, 160) AS bodyStart FROM signals WHERE incidentId IN (:incidentIds)")
    suspend fun memberTexts(incidentIds: List<String>): List<WidgetMemberText>

    @Query(
        "SELECT targetId AS incidentId, payloadJson FROM suggestions WHERE kind = 'TRIAGE' AND state = 'ACCEPTED' " +
            "AND targetId IN (:incidentIds)"
    )
    suspend fun acceptedTriagePayloads(incidentIds: List<String>): List<WidgetTriagePayload>

    /**
     * The channel of any Play review or email, in any project, whose title or body starts with
     * [likePrefix] (a pattern from `WidgetPrivacy.likePrefix`); null when there is none. Only the type
     * comes back: the privacy check needs to know that a title quotes review or mail text, not the text.
     */
    @Query(
        "SELECT type FROM signals WHERE type IN ('PLAY_REVIEW', 'EMAIL') " +
            "AND (ltrim(title) LIKE :likePrefix ESCAPE '\\' OR ltrim(body) LIKE :likePrefix ESCAPE '\\') " +
            "ORDER BY type LIMIT 1"
    )
    suspend fun reviewOrMailStartingWith(likePrefix: String): String?

    @Query(
        "SELECT sourceKey, projectId, rating, replyState, createdAt FROM signals WHERE type = 'PLAY_REVIEW' " +
            "AND (replyState IN ('FAILED', 'SEND_CONFIRMED') " +
            "OR (replyState = 'DRAFTED' AND replyDraft IS NOT NULL AND TRIM(replyDraft) != '')) " +
            "AND projectId NOT IN (SELECT id FROM projects WHERE archived = 1) " +
            "ORDER BY createdAt DESC"
    )
    suspend fun repliesNeedingYou(): List<WidgetReplyRow>

    @Query(
        "SELECT COUNT(*) FROM suggestions WHERE state = 'PENDING' AND kind = 'REPLY_DRAFT' " +
            "AND targetId IN (SELECT sourceKey FROM signals WHERE projectId NOT IN (SELECT id FROM projects WHERE archived = 1))"
    )
    suspend fun pendingAiReplyDrafts(): Int

    /** Signals whose own source timestamp falls in [fromMillis, untilMillis). */
    @Query(
        "SELECT projectId, type, COUNT(*) AS n FROM signals WHERE createdAt >= :fromMillis AND createdAt < :untilMillis " +
            "AND projectId NOT IN (SELECT id FROM projects WHERE archived = 1) GROUP BY projectId, type"
    )
    suspend fun signalCountsCreatedBetween(fromMillis: Long, untilMillis: Long): List<WidgetProjectChannelCount>

    @Query("SELECT COUNT(*) FROM signals WHERE incidentId IS NULL AND projectId NOT IN (SELECT id FROM projects WHERE archived = 1)")
    suspend fun ungroupedSignalCount(): Int

    @Query("SELECT COUNT(*) FROM signals WHERE projectId NOT IN (SELECT id FROM projects WHERE archived = 1)")
    suspend fun signalCount(): Int

    /** Mirrors what [com.mbaliga.csapp.data.sync.PollCoordinator] polls: every enabled source. */
    @Query("SELECT kind, COUNT(*) AS n FROM project_sources WHERE enabled = 1 GROUP BY kind")
    suspend fun enabledSourceCounts(): List<WidgetSourceCount>

    @Query("SELECT COUNT(*) FROM mailboxes WHERE enabled = 1")
    suspend fun enabledMailboxCount(): Int
}

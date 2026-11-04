package com.mbaliga.csapp.widget

import com.mbaliga.csapp.R
import com.mbaliga.csapp.data.sync.PollChannel
import com.mbaliga.csapp.data.sync.PollHealth
import com.mbaliga.csapp.domain.model.IncidentStatus
import com.mbaliga.csapp.domain.model.ReplyState
import com.mbaliga.csapp.domain.model.Severity
import com.mbaliga.csapp.domain.model.SignalType

/*
 * Pure model for csapp's home-screen widgets: what each widget shows, at which size, and where a tap
 * lands. No android.* imports here, so all of it is JVM-tested. The Android side (WidgetRenderer)
 * only copies a WidgetFace into RemoteViews.
 */

/**
 * The size classes every widget renders. TINY is the one-line summary used when a widget is shorter
 * than its small layout (a 2x1 in landscape, say): below a template's minimum the widget collapses to a
 * single tappable line instead of squeezing.
 */
enum class WidgetSize { TINY, SMALL, MEDIUM, LARGE }

/** Smallest width x height (dp) at which a size class is used. */
data class Breakpoint(val size: WidgetSize, val widthDp: Float, val heightDp: Float)

/**
 * A layout skeleton (template x size) and how many of each repeated slot it has. Layouts are shared
 * between widgets of the same template; WidgetRenderer only touches the slots declared here.
 *
 * [needDp] is the height the layout needs at font scale 1.0 with every slot it can show at once filled
 * to its maxLines. WidgetResourcesTest recomputes it from the layout XML, so a layout can't outgrow
 * the breakpoint that selects it.
 */
enum class Skeleton(
    val layout: Int,
    val needDp: Float,
    /** The widget name (w_title). */
    val header: Boolean = false,
    /** How fresh the data is (w_asof): in the header, or under the text in the small layouts. */
    val asOf: Boolean = false,
    val value: Boolean = false,
    val caption: Boolean = false,
    val lead: Boolean = false,
    val text: Boolean = false,
    val cells: Int = 0,
    val rows: Int = 0,
    val rowsTappable: Boolean = false,
    val lines: Int = 0,
    val empty: Boolean = false,
    val footer: Boolean = false,
    /** w_text's maxLines in the layout, and the lines it keeps while w_asof shows (0: unchanged). */
    val textLines: Int = 0,
    val textLinesWithAsOf: Int = 0,
) {
    /** One line: the widget's summary, and a short freshness mark when the data is stale. */
    LINE_TINY(R.layout.widget_line_tiny, 40f, text = true, asOf = true),
    STAT_SMALL(R.layout.widget_stat_small, 76f, asOf = true, value = true, caption = true),
    STAT_MEDIUM(R.layout.widget_stat_medium, 138f, header = true, asOf = true, value = true, cells = 4, empty = true),
    STAT_LARGE(R.layout.widget_stat_large, 302f, header = true, asOf = true, value = true, cells = 4, rows = 3, rowsTappable = true, empty = true, footer = true),
    LINE_SMALL(R.layout.widget_line_small, 80f, asOf = true, lead = true, text = true, textLines = 2, textLinesWithAsOf = 1),
    LIST_MEDIUM(R.layout.widget_list_medium, 170f, header = true, asOf = true, rows = 3, empty = true),
    LIST_LARGE(R.layout.widget_list_large, 256f, header = true, asOf = true, rows = 4, rowsTappable = true, empty = true, footer = true),
    CARD_MEDIUM(R.layout.widget_card_medium, 134f, header = true, asOf = true, text = true, lines = 2),
    CARD_LARGE(R.layout.widget_card_large, 254f, header = true, asOf = true, text = true, lines = 4, footer = true),
    /** First-run and error states at medium and large sizes: widget name, a headline, two lines of help. */
    MESSAGE(R.layout.widget_message, 120f, lead = true, text = true, caption = true),
    ;

    companion object {
        /** The skeleton the first-run and error states use at [size] (smaller sizes drop the help line). */
        fun message(size: WidgetSize): Skeleton = when (size) {
            WidgetSize.TINY -> LINE_TINY
            WidgetSize.SMALL -> LINE_SMALL
            WidgetSize.MEDIUM, WidgetSize.LARGE -> MESSAGE
        }
    }
}

/**
 * The six widgets. Each names its small, medium and large skeletons (TINY is the shared one-line
 * summary) and the widths at which medium and large start. Heights come from the skeletons.
 */
enum class WidgetKind(
    val small: Skeleton,
    val medium: Skeleton,
    val large: Skeleton,
    val mediumWidthDp: Float,
    val largeWidthDp: Float,
) {
    OPEN_INCIDENTS(Skeleton.STAT_SMALL, Skeleton.STAT_MEDIUM, Skeleton.STAT_LARGE, 180f, 250f),
    INCIDENT_LIST(Skeleton.LINE_SMALL, Skeleton.LIST_MEDIUM, Skeleton.LIST_LARGE, 180f, 250f),
    TOP_INCIDENT(Skeleton.LINE_SMALL, Skeleton.CARD_MEDIUM, Skeleton.CARD_LARGE, 180f, 250f),
    REPLIES(Skeleton.STAT_SMALL, Skeleton.LIST_MEDIUM, Skeleton.LIST_LARGE, 180f, 250f),
    SYNC_STATUS(Skeleton.LINE_SMALL, Skeleton.LIST_MEDIUM, Skeleton.LIST_LARGE, 180f, 250f),
    TODAY(Skeleton.STAT_SMALL, Skeleton.STAT_MEDIUM, Skeleton.STAT_LARGE, 180f, 250f),
    ;

    fun skeleton(size: WidgetSize) = when (size) {
        WidgetSize.TINY -> Skeleton.LINE_TINY
        WidgetSize.SMALL -> small
        WidgetSize.MEDIUM -> medium
        WidgetSize.LARGE -> large
    }

    /**
     * Smallest first. A size class starts where both its own skeleton and the first-run / error
     * skeleton of that size fit, at [fontScale] (text grows with it; the margins and 48dp rows don't, so
     * scaling the whole height is a safe upper bound). TINY has no minimum: below every breakpoint the
     * one-line summary is still used.
     */
    fun breakpoints(fontScale: Float = 1f): List<Breakpoint> {
        val scale = fontScale.coerceIn(1f, MAX_FONT_SCALE)
        fun height(size: WidgetSize) = maxOf(skeleton(size).needDp, Skeleton.message(size).needDp) * scale
        return listOf(
            Breakpoint(WidgetSize.TINY, MIN_WIDTH_DP, MIN_HEIGHT_DP),
            Breakpoint(WidgetSize.SMALL, MIN_WIDTH_DP, height(WidgetSize.SMALL)),
            Breakpoint(WidgetSize.MEDIUM, mediumWidthDp, height(WidgetSize.MEDIUM)),
            Breakpoint(WidgetSize.LARGE, largeWidthDp, height(WidgetSize.LARGE)),
        )
    }

    /**
     * The size class for a widget [widthDp] x [heightDp] big: the largest one that fits, else TINY.
     * This is the same rule API 31+ launchers apply to the size-mapped RemoteViews.
     */
    fun sizeFor(widthDp: Float, heightDp: Float, fontScale: Float = 1f): WidgetSize =
        breakpoints(fontScale).lastOrNull { widthDp >= it.widthDp && heightDp >= it.heightDp }?.size ?: WidgetSize.TINY

    /**
     * Below API 31 the app picks the layout itself, from the widget's options. It uses the portrait
     * size (minWidth x maxHeight) whatever the phone's orientation at that moment, because a redraw
     * often runs in the background (after a sync, at midnight) while some other app is in landscape,
     * and older hosts don't ask again on rotation. Unknown options: small.
     */
    fun legacySizeFor(minWidthDp: Int, maxHeightDp: Int, fontScale: Float = 1f): WidgetSize =
        if (minWidthDp <= 0 || maxHeightDp <= 0) WidgetSize.SMALL else sizeFor(minWidthDp.toFloat(), maxHeightDp.toFloat(), fontScale)

    companion object {
        const val MIN_WIDTH_DP = 110f
        const val MIN_HEIGHT_DP = 40f
        /** Android's largest font scale; anything larger is treated as this. */
        const val MAX_FONT_SCALE = 2f
    }
}

/** Where a tap lands. Taps only ever open a screen; none of them changes anything. */
sealed interface WidgetTap {
    /** Home; [projectId] (if any) becomes the selected project. */
    data class Home(val projectId: String? = null) : WidgetTap
    data class Incident(val incidentId: String) : WidgetTap
    /** The reply screen of one Play review. Sending still needs the screen's own confirm dialog. */
    data class Reply(val sourceKey: String) : WidgetTap
    data object Projects : WidgetTap

    /** The two intent extras that carry this tap (destination, argument). */
    fun encode(): Pair<String, String?> = when (this) {
        is Home -> DEST_HOME to projectId
        is Incident -> DEST_INCIDENT to incidentId
        is Reply -> DEST_REPLY to sourceKey
        Projects -> DEST_PROJECTS to null
    }

    companion object {
        const val EXTRA_DEST = "com.mbaliga.csapp.widget.DEST"
        const val EXTRA_ARG = "com.mbaliga.csapp.widget.ARG"
        const val DEST_HOME = "home"
        const val DEST_INCIDENT = "incident"
        const val DEST_REPLY = "reply"
        const val DEST_PROJECTS = "projects"
        private const val MAX_ARG = 512

        /**
         * Parses the extras of a launch intent. MainActivity is exported (it's the launcher entry),
         * so anything may arrive here: unknown destinations and malformed arguments return null and
         * the app simply opens as usual.
         */
        fun parse(dest: String?, arg: String?): WidgetTap? {
            val a = arg?.trim()?.takeIf { it.isNotEmpty() && it.length <= MAX_ARG && it.none(Char::isISOControl) }
            return when (dest) {
                DEST_HOME -> Home(a)
                DEST_INCIDENT -> a?.let(::Incident)
                DEST_REPLY -> a?.let(::Reply)
                DEST_PROJECTS -> Projects
                else -> null
            }
        }
    }
}

data class FaceCell(val value: String, val label: String)

data class FaceRow(
    val text: String,
    val meta: String? = null,
    val trail: String? = null,
    val tap: WidgetTap? = null,
    val description: String = listOfNotNull(text, meta, trail).joinToString(". "),
)

/** Everything one widget shows at one size. Null / empty slots are hidden. */
data class WidgetFace(
    val skeleton: Skeleton,
    val title: String,
    val tap: WidgetTap,
    val description: String,
    val asOf: String? = null,
    val value: String? = null,
    val label: String? = null,
    val caption: String? = null,
    val lead: String? = null,
    val text: String? = null,
    val cells: List<FaceCell> = emptyList(),
    val rows: List<FaceRow> = emptyList(),
    val lines: List<String> = emptyList(),
    val empty: String? = null,
    val footer: String? = null,
)

// ---- Snapshot: what the widgets know, read once per redraw ----

data class ProjectRef(val id: String, val name: String)

/**
 * Why a title is not shown on the home screen. UNVERIFIED: nothing shows the title is safe (it can
 * be the subject of an email or a review's own title that has since been split or moved out).
 */
enum class WithheldReason { QUOTES_REVIEW, QUOTES_EMAIL, CONTAINS_ADDRESS, UNVERIFIED }

/** A title as the home screen may show it: the text, or the reason it is withheld. */
sealed interface ShownTitle {
    data class Text(val text: String) : ShownTitle
    data class Withheld(val reason: WithheldReason) : ShownTitle
}

data class LiveIncident(
    val id: String,
    val projectId: String,
    val severity: Severity,
    val status: IncidentStatus,
    val isRecurring: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val channelCounts: Map<SignalType, Int>,
    /** Set (after the privacy check) for the top-ranked incidents only; null means never shown. */
    val title: ShownTitle? = null,
) {
    val signalCount: Int get() = channelCounts.values.sum()
}

data class ReplyItem(val sourceKey: String, val projectId: String, val rating: Int?, val state: ReplyState, val createdAt: Long)

data class RepliesInfo(val items: List<ReplyItem> = emptyList(), val aiDrafts: Int = 0) {
    val failed get() = items.count { it.state == ReplyState.FAILED }
    val sending get() = items.count { it.state == ReplyState.SEND_CONFIRMED }
    val awaiting get() = items.count { it.state == ReplyState.DRAFTED }
    /** Replies whose state needs the developer (AI drafts are counted separately). */
    val needYou get() = failed + sending + awaiting
}

data class TodayCount(val projectId: String, val channel: SignalType, val n: Int)

data class SyncInfo(
    val health: PollHealth = PollHealth(),
    /** Enabled sources per channel (repos, packages, mailboxes). */
    val configured: Map<PollChannel, Int> = emptyMap(),
    val intervalMinutes: Int = 30,
)

data class WidgetSnapshot(
    val projects: List<ProjectRef> = emptyList(),
    /** Ranked most severe first (see [WidgetRanking]). */
    val live: List<LiveIncident> = emptyList(),
    val replies: RepliesInfo = RepliesInfo(),
    val today: List<TodayCount> = emptyList(),
    val ungrouped: Int = 0,
    val sync: SyncInfo = SyncInfo(),
    /** No source set up and no data at all: the widgets invite the user to add a source to a project. */
    val firstRun: Boolean = false,
) {
    fun projectName(id: String): String = projects.firstOrNull { it.id == id }?.name ?: id
}

/** The one ordering every widget uses for "most severe". */
object WidgetRanking {
    val comparator: Comparator<LiveIncident> =
        compareByDescending<LiveIncident> { it.severity.ordinal }
            .thenBy { if (it.status == IncidentStatus.OPEN) 0 else 1 }
            .thenByDescending { it.signalCount }
            .thenByDescending { it.updatedAt }
            .thenBy { it.id }

    /** How many top-ranked incidents get a (privacy-checked) title: the most any widget shows. */
    const val TITLED = 5
}

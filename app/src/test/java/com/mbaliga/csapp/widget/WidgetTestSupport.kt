package com.mbaliga.csapp.widget

import com.mbaliga.csapp.R
import com.mbaliga.csapp.data.sync.ChannelHealth
import com.mbaliga.csapp.data.sync.PollChannel
import com.mbaliga.csapp.data.sync.PollHealth
import com.mbaliga.csapp.domain.model.IncidentStatus
import com.mbaliga.csapp.domain.model.ReplyState
import com.mbaliga.csapp.domain.model.Severity
import com.mbaliga.csapp.domain.model.SignalType
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

/**
 * [WidgetText] backed by the real res/values/strings.xml, so the tests read the same words the
 * widgets show. Resource ids are mapped back to names through the generated R class.
 */
class XmlWidgetText(file: File = File("src/main/res/values/strings.xml")) : WidgetText {
    val strings = mutableMapOf<String, String>()
    val plurals = mutableMapOf<String, Map<String, String>>()

    init {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val res = doc.documentElement.childNodes
        for (i in 0 until res.length) {
            val e = res.item(i) as? Element ?: continue
            when (e.tagName) {
                "string" -> strings[e.getAttribute("name")] = unescape(e.textContent)
                "plurals" -> {
                    val items = e.getElementsByTagName("item")
                    plurals[e.getAttribute("name")] = (0 until items.length).associate { j ->
                        val it = items.item(j) as Element
                        it.getAttribute("quantity") to unescape(it.textContent)
                    }
                }
            }
        }
    }

    override val appName: String get() = strings.getValue("app_name")

    override fun get(s: WS, vararg args: Any): String =
        String.format(Locale.UK, strings[nameOf(R.string::class.java, s.res)] ?: error("missing string for $s"), *args)

    override fun plural(p: WP, count: Int, vararg args: Any): String {
        val forms = plurals[nameOf(R.plurals::class.java, p.res)] ?: error("missing plurals for $p")
        val form = forms[if (count == 1) "one" else "other"] ?: error("missing quantity for $p")
        return String.format(Locale.UK, form, *args)
    }

    private fun unescape(s: String) = s.replace("\\'", "'").replace("\\\"", "\"")

    companion object {
        fun nameOf(rClass: Class<*>, id: Int): String =
            rClass.fields.firstOrNull { it.getInt(null) == id }?.name ?: error("no ${rClass.simpleName} resource with id $id")
    }
}

/** A fixed clock: Wednesday 4 Nov 2026, 14:05, UTC, 24-hour. */
object TestClock {
    val zone: ZoneId = ZoneId.of("UTC")
    fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int): Long = LocalDateTime.of(y, mo, d, h, mi).atZone(zone).toInstant().toEpochMilli()
    val NOW = at(2026, 11, 4, 14, 5)
    fun clock(now: Long = NOW) = WidgetClock(now, zone, Locale.UK, is24h = true)
}

fun incident(
    id: String,
    severity: Severity = Severity.MEDIUM,
    status: IncidentStatus = IncidentStatus.OPEN,
    project: String = "fotoz",
    title: String? = "Title of $id",
    counts: Map<SignalType, Int> = mapOf(SignalType.GITHUB_ISSUE to 1),
    recurring: Boolean = false,
    createdAt: Long = TestClock.at(2026, 11, 2, 9, 12),
    updatedAt: Long = TestClock.at(2026, 11, 4, 13, 0),
) = LiveIncident(
    id = id, projectId = project, severity = severity, status = status, isRecurring = recurring,
    createdAt = createdAt, updatedAt = updatedAt, channelCounts = counts, title = title?.let { ShownTitle.Text(it) },
)

fun reply(key: String, state: ReplyState, rating: Int? = 2, project: String = "fotoz", createdAt: Long = TestClock.NOW) =
    ReplyItem(key, project, rating, state, createdAt)

const val MIN = 60_000L

val PROJECTS = listOf(ProjectRef("fotoz", "Fotoz"), ProjectRef("notes", "Notes"), ProjectRef("inbox", "Inbox"))

/** A busy snapshot that fills every slot of every size. */
fun richSnapshot(): WidgetSnapshot {
    val live = listOf(
        incident("a", Severity.CRITICAL, counts = mapOf(SignalType.PLAY_REVIEW to 3, SignalType.GITHUB_ISSUE to 1), recurring = true),
        incident("b", Severity.HIGH, IncidentStatus.ACKNOWLEDGED, project = "notes"),
        incident("c", Severity.HIGH, project = "fotoz"),
        incident("d", Severity.MEDIUM, project = "inbox", title = null),
        incident("e", Severity.MEDIUM, project = "notes"),
        incident("f", Severity.LOW, project = "zeta"),
    ).sortedWith(WidgetRanking.comparator)
    return WidgetSnapshot(
        projects = PROJECTS + ProjectRef("zeta", "Zeta"),
        live = live,
        replies = RepliesInfo(
            items = listOf(
                reply("play:p:1", ReplyState.DRAFTED, 4, createdAt = TestClock.NOW - 60 * MIN),
                reply("play:p:2", ReplyState.FAILED, 1, createdAt = TestClock.NOW - 180 * MIN),
                reply("play:p:3", ReplyState.SEND_CONFIRMED, null, project = "notes", createdAt = TestClock.NOW - 120 * MIN),
                reply("play:p:4", ReplyState.DRAFTED, 5, createdAt = TestClock.NOW - 30 * MIN),
                reply("play:p:5", ReplyState.FAILED, 2, createdAt = TestClock.NOW - 60 * MIN),
            ),
            aiDrafts = 2,
        ),
        today = listOf(
            TodayCount("fotoz", SignalType.PLAY_REVIEW, 3),
            TodayCount("fotoz", SignalType.GITHUB_ISSUE, 1),
            TodayCount("notes", SignalType.EMAIL, 2),
            TodayCount("inbox", SignalType.EMAIL, 1),
            TodayCount("zeta", SignalType.MANUAL, 1),
        ),
        ungrouped = 4,
        sync = SyncInfo(
            health = PollHealth(
                mapOf(
                    PollChannel.GITHUB to ChannelHealth(TestClock.at(2026, 11, 4, 13, 50), 2, 0, TestClock.at(2026, 11, 4, 13, 50)),
                    PollChannel.PLAY to ChannelHealth(TestClock.at(2026, 11, 4, 13, 50), 2, 1, TestClock.at(2026, 11, 3, 9, 0)),
                )
            ),
            configured = mapOf(PollChannel.GITHUB to 2, PollChannel.PLAY to 2, PollChannel.MAIL to 0),
            intervalMinutes = 30,
        ),
    )
}

/** [richSnapshot] with every set-up channel fully synced a few minutes ago (no stale line anywhere). */
fun freshSnapshot(): WidgetSnapshot {
    val rich = richSnapshot()
    val at = TestClock.at(2026, 11, 4, 13, 50)
    return rich.copy(
        sync = rich.sync.copy(
            health = PollHealth(mapOf(PollChannel.GITHUB to ChannelHealth(at, 2, 0, at), PollChannel.PLAY to ChannelHealth(at, 2, 0, at))),
        ),
    )
}

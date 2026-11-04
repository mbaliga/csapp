package com.mbaliga.csapp.widget

import com.mbaliga.csapp.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** Every widget string, by resource. The words live in res/values/strings.xml. */
enum class WS(val res: Int) {
    FRESH_SYNCED(R.string.widget_fresh_synced),
    FRESH_STALE(R.string.widget_fresh_stale),
    FRESH_FAILING(R.string.widget_fresh_failing),
    FRESH_STALE_SHORT(R.string.widget_fresh_stale_short),
    FRESH_NEVER(R.string.widget_fresh_never),
    FRESH_NEVER_SHORT(R.string.widget_fresh_never_short),
    VALUE_LABEL(R.string.widget_value_label),
    MORE(R.string.widget_more),
    JOIN(R.string.widget_join),
    COUNT_OF(R.string.widget_count_of),
    OPENS_APP(R.string.widget_opens_app),
    ERROR_TITLE(R.string.widget_error_title),
    ERROR_BODY(R.string.widget_error_body),
    FIRST_RUN_TITLE(R.string.widget_first_run_title),
    FIRST_RUN_BODY(R.string.widget_first_run_body),
    UNTITLED(R.string.widget_untitled),
    WITHHELD_REVIEW(R.string.widget_title_withheld_review),
    WITHHELD_EMAIL(R.string.widget_title_withheld_email),
    WITHHELD_ADDRESS(R.string.widget_title_withheld_address),
    WITHHELD_UNVERIFIED(R.string.widget_title_withheld_unverified),
    N_OPEN(R.string.widget_n_open),
    CH_PLAY(R.string.widget_ch_play),
    CH_GITHUB(R.string.widget_ch_github),
    CH_EMAIL(R.string.widget_ch_email),
    CH_MANUAL(R.string.widget_ch_manual),

    OPEN_NAME(R.string.widget_open_name),
    OPEN_SCOPE(R.string.widget_open_scope),
    OPEN_EMPTY(R.string.widget_open_empty),

    LIST_NAME(R.string.widget_list_name),
    LIST_EMPTY(R.string.widget_list_empty),

    TOP_NAME(R.string.widget_top_name),
    TOP_EMPTY(R.string.widget_top_empty),
    TOP_FIRST_SEEN(R.string.widget_top_first_seen),
    TOP_UPDATED(R.string.widget_top_updated),
    TOP_RECURRING(R.string.widget_top_recurring),

    REPLIES_NAME(R.string.widget_replies_name),
    REPLIES_EMPTY(R.string.widget_replies_empty),
    REPLY_FAILED(R.string.widget_reply_failed),
    REPLY_SENDING(R.string.widget_reply_sending),
    REPLY_AWAITING(R.string.widget_reply_awaiting),
    REPLY_AI(R.string.widget_reply_ai),
    REPLY_RATED(R.string.widget_reply_rated),
    REPLY_UNRATED(R.string.widget_reply_unrated),
    REPLY_NEVER_SENDS(R.string.widget_reply_never_sends),

    SYNC_NAME(R.string.widget_sync_name),
    SYNC_GITHUB(R.string.widget_sync_github),
    SYNC_PLAY(R.string.widget_sync_play),
    SYNC_MAIL(R.string.widget_sync_mail),
    SYNC_OK(R.string.widget_sync_ok),
    SYNC_FAILED(R.string.widget_sync_failed),
    SYNC_STALE(R.string.widget_sync_stale),
    SYNC_NEVER(R.string.widget_sync_never),
    SYNC_NOT_SET_UP(R.string.widget_sync_not_set_up),
    SYNC_LAST_OK(R.string.widget_sync_last_ok),
    SYNC_LAST_OK_NEVER(R.string.widget_sync_last_ok_never),
    SYNC_EVERY(R.string.widget_sync_every),
    SYNC_LEAD(R.string.widget_sync_lead),
    SYNC_ALL_OK(R.string.widget_sync_all_ok),
    SYNC_SOME_STALE(R.string.widget_sync_some_stale),
    SYNC_NONE(R.string.widget_sync_none),

    TODAY_NAME(R.string.widget_today_name),
    TODAY_SINCE(R.string.widget_today_since),
    TODAY_EMPTY(R.string.widget_today_empty),
}

/** Plural widget strings. */
enum class WP(val res: Int) {
    OPEN_COUNT(R.plurals.widget_open_count),
    SIGNALS(R.plurals.widget_signals),
    CH_PLAY(R.plurals.widget_ch_play_n),
    CH_GITHUB(R.plurals.widget_ch_github_n),
    CH_EMAIL(R.plurals.widget_ch_email_n),
    CH_MANUAL(R.plurals.widget_ch_manual_n),
    REPLIES_COUNT(R.plurals.widget_replies_count),
    SYNC_SOURCES(R.plurals.widget_sync_sources),
    SYNC_CHANNELS_FAILED(R.plurals.widget_sync_channels_failed),
    TODAY_COUNT(R.plurals.widget_today_count),
    UNGROUPED(R.plurals.widget_ungrouped),
}

/** Resolves widget strings: Android resources on the device, the same strings.xml in tests. */
interface WidgetText {
    /** The product name, from `app_name` only (it is an open owner question; nothing else spells it). */
    val appName: String
    fun get(s: WS, vararg args: Any): String
    fun plural(p: WP, count: Int, vararg args: Any): String
}

/**
 * The time the widgets are drawn at, and how they print times. Every time a widget prints is
 * absolute ("14:05", "Tue 14:05"), so it stays true however late the next redraw comes.
 */
class WidgetClock(val now: Long, val zone: ZoneId, val locale: Locale, val is24h: Boolean) {
    private val timeFormat = DateTimeFormatter.ofPattern(if (is24h) "HH:mm" else "h:mm a", locale)
    private val dateFormat = DateTimeFormatter.ofPattern("d MMM", locale)

    private fun date(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()

    /** Today: "14:05". Within the past week: "Tue 14:05". Older or in the future: "3 Nov". */
    fun time(ms: Long): String {
        val at = Instant.ofEpochMilli(ms).atZone(zone)
        val today = date(now)
        val day = at.toLocalDate()
        return when {
            day == today -> at.format(timeFormat)
            day.isBefore(today) && !day.isBefore(today.minusDays(6)) ->
                day.dayOfWeek.getDisplayName(TextStyle.SHORT, locale) + " " + at.format(timeFormat)
            else -> at.format(dateFormat)
        }
    }

    /** Start of today (local midnight) in epoch millis. */
    fun dayStart(): Long = date(now).atStartOfDay(zone).toInstant().toEpochMilli()

    /** Start of tomorrow (the next local midnight) in epoch millis. */
    fun nextDayStart(): Long = date(now).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
}

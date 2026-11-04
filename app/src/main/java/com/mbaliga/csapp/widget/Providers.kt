package com.mbaliga.csapp.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle

/**
 * Shared behaviour of csapp's six App Widget providers. They are exported (launchers must bind them)
 * and only redraw: none of them reads extras, writes data or opens the network.
 */
abstract class CsWidgetProvider(private val kind: WidgetKind) : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        CsWidgets.updateAsync(this, context, mapOf(kind to appWidgetIds))
    }

    override fun onAppWidgetOptionsChanged(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle) {
        // API 31+ hosts pick from the size-mapped RemoteViews themselves; older ones need a redraw.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) CsWidgets.updateAsync(this, context, mapOf(kind to intArrayOf(appWidgetId)))
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            // System-only (protected) broadcasts: printed times and "today" depend on them.
            Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_LOCALE_CHANGED ->
                CsWidgets.updateAsync(this, context, mapOf(kind to CsWidgets.idsOf(context, kind)))
            else -> super.onReceive(context, intent)
        }
    }

    override fun onDisabled(context: Context) {
        // The last widget of this kind went away: re-arm (or cancel) the boundary alarm.
        CsWidgets.updateAsync(this, context, emptyMap())
    }
}

/** Open and acknowledged incidents by severity (`stat`). */
class OpenIncidentsWidget : CsWidgetProvider(WidgetKind.OPEN_INCIDENTS)

/** The most severe live incidents (`list`). */
class IncidentListWidget : CsWidgetProvider(WidgetKind.INCIDENT_LIST)

/** The single most severe live incident (`card`). */
class TopIncidentWidget : CsWidgetProvider(WidgetKind.TOP_INCIDENT)

/** Play replies that failed or await confirmation (`stat` / `list`). Never sends. */
class RepliesWidget : CsWidgetProvider(WidgetKind.REPLIES)

/** When each channel was last polled and whether it worked (`list`). */
class SyncStatusWidget : CsWidgetProvider(WidgetKind.SYNC_STATUS)

/** Signals created today, by channel and project (`stat`). */
class TodayWidget : CsWidgetProvider(WidgetKind.TODAY)

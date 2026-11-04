package com.mbaliga.csapp.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.text.format.DateFormat
import com.mbaliga.csapp.CsAppApplication
import com.mbaliga.csapp.data.db.AppDatabase
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Keeps csapp's home-screen widgets current. Updates are pushed, never polled
 * (`updatePeriodMillis = 0`):
 * - any write to the tables the widgets read (Room's invalidation tracker, so every write site in
 *   the app is covered: sync, triage, status changes, replies, projects) and every recorded sync pass
 *   ask for a redraw, coalesced to one per [COALESCE_MS];
 * - the providers redraw on add / resize / time, time-zone and locale changes;
 * - one inexact alarm (no exact-alarm permission) redraws at the next local midnight and when a
 *   source turns stale ([SyncFreshness.nextRedraw]).
 * Nothing here writes data or touches the network, and nothing here may crash the process: this runs
 * in every process start, including those started only for a widget broadcast or a background sync.
 */
object CsWidgets {
    // A failure in a widget coroutine is dropped (the widgets keep their last face, or show their own
    // error face on the next redraw); it must never reach the thread's uncaught-exception handler.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, _ -> })
    private val requests = Channel<Unit>(Channel.CONFLATED)
    private val started = AtomicBoolean(false)
    private const val COALESCE_MS = 1_500L
    private const val BUDGET_MS = 8_000L
    private const val RETRY_MIN_MS = 30_000L
    private const val RETRY_MAX_MS = 30 * 60_000L

    /** The tables any widget reads. */
    private val TABLES = arrayOf("incidents", "signals", "projects", "project_sources", "mailboxes", "suggestions")

    val providers: Map<WidgetKind, Class<out AppWidgetProvider>> = mapOf(
        WidgetKind.OPEN_INCIDENTS to OpenIncidentsWidget::class.java,
        WidgetKind.INCIDENT_LIST to IncidentListWidget::class.java,
        WidgetKind.TOP_INCIDENT to TopIncidentWidget::class.java,
        WidgetKind.REPLIES to RepliesWidget::class.java,
        WidgetKind.SYNC_STATUS to SyncStatusWidget::class.java,
        WidgetKind.TODAY to TodayWidget::class.java,
    )

    /** Called once per process from [CsAppApplication.onCreate]. */
    fun start(context: Context, database: AppDatabase) {
        if (!started.compareAndSet(false, true)) return
        val app = context.applicationContext
        scope.launch {
            // Opening the observer can fail (a failed migration, a full disk, an I/O error). Then ask for
            // a redraw (which shows the widgets' error face if the data really can't be read) and try
            // again later, backing off.
            var backoff = RETRY_MIN_MS
            while (true) {
                try {
                    database.invalidationTracker.createFlow(*TABLES, emitInitialState = false).collect {
                        backoff = RETRY_MIN_MS
                        requests.trySend(Unit)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    requests.trySend(Unit)
                }
                delay(backoff)
                backoff = (backoff * 2).coerceAtMost(RETRY_MAX_MS)
            }
        }
        scope.launch {
            while (true) {
                requests.receive()
                delay(COALESCE_MS)
                runCatching { withTimeoutOrNull(BUDGET_MS) { update(app, null) } }
            }
        }
    }

    /** Asks for a redraw of every placed widget (coalesced). No-op until [start] ran. */
    fun requestRefresh() {
        if (started.get()) requests.trySend(Unit)
    }

    /** Redraws from a broadcast receiver, off the main thread, within the receiver's time budget. */
    fun updateAsync(receiver: BroadcastReceiver, context: Context, only: Map<WidgetKind, IntArray>?) {
        val pending = receiver.goAsync()
        val app = context.applicationContext
        scope.launch {
            try {
                withTimeoutOrNull(BUDGET_MS) { update(app, only) }
            } catch (_: Exception) {
                // Nothing to report to: widgets stay as they were and the next update retries.
            } finally {
                pending.finish()
            }
        }
    }

    /** Redraws [only] (or every placed widget), then re-arms the boundary alarm. */
    suspend fun update(context: Context, only: Map<WidgetKind, IntArray>?) {
        val app = context.applicationContext
        val mgr = AppWidgetManager.getInstance(app) ?: return
        val placed = providers.mapValues { (_, cls) -> mgr.getAppWidgetIds(ComponentName(app, cls)) ?: IntArray(0) }
            .filterValues { it.isNotEmpty() }
        if (placed.isEmpty()) {
            WidgetAlarm.schedule(app, null)
            return
        }
        val targets = only ?: placed
        val clock = WidgetClock(
            now = System.currentTimeMillis(),
            zone = ZoneId.systemDefault(),
            locale = app.resources.configuration.locales[0],
            is24h = DateFormat.is24HourFormat(app),
        )
        val snapshot = try {
            (app as? CsAppApplication)?.containerOrNull?.widgetSnapshotLoader?.load(clock)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
        val faces = WidgetFaces(ResourceWidgetText(app), clock)
        for ((kind, ids) in targets) {
            for (id in ids) {
                val views = WidgetRenderer.views(
                    app, id, kind,
                    { size -> if (snapshot == null) faces.error(kind, size) else faces.face(kind, size, snapshot) },
                    mgr.getAppWidgetOptions(id),
                )
                runCatching { mgr.updateAppWidget(id, views) }
            }
        }
        WidgetAlarm.schedule(
            app,
            SyncFreshness.nextRedraw(now = clock.now, nextMidnight = clock.nextDayStart(), sync = snapshot?.sync ?: SyncInfo(), anyPlaced = true),
        )
    }

    fun idsOf(context: Context, kind: WidgetKind): IntArray =
        AppWidgetManager.getInstance(context)?.getAppWidgetIds(ComponentName(context, providers.getValue(kind))) ?: IntArray(0)
}

/** One inexact, non-waking alarm for the next time-based redraw. */
object WidgetAlarm {
    const val ACTION_TICK = "com.mbaliga.csapp.widget.TICK"

    fun schedule(context: Context, atMillis: Long?) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = PendingIntent.getBroadcast(
            context, 0,
            Intent(context, WidgetRefreshReceiver::class.java).setAction(ACTION_TICK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        if (atMillis == null) am.cancel(pi) else am.set(AlarmManager.RTC, atMillis, pi)
    }
}

/** Receives only the app's own boundary alarm (not exported). */
class WidgetRefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == WidgetAlarm.ACTION_TICK) CsWidgets.updateAsync(this, context, null)
    }
}

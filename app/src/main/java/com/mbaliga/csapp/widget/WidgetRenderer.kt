package com.mbaliga.csapp.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.BidiFormatter
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews
import com.mbaliga.csapp.R
import com.mbaliga.csapp.ui.MainActivity

/** Android strings for [WidgetText], resolved in the app's current locale. */
class ResourceWidgetText(private val context: Context) : WidgetText {
    override val appName: String get() = context.getString(R.string.app_name)
    override fun get(s: WS, vararg args: Any): String = context.getString(s.res, *args)
    override fun plural(p: WP, count: Int, vararg args: Any): String = context.resources.getQuantityString(p.res, count, *args)
}

/**
 * Copies [WidgetFace]s into RemoteViews. On API 31+ one RemoteViews carries every size and the
 * launcher picks one per size without waking the app; below 31 the size is read from the widget's
 * options (and re-rendered from onAppWidgetOptionsChanged). Both use breakpoints scaled by the
 * current font scale, so larger text picks a smaller layout instead of clipping.
 */
object WidgetRenderer {
    private val CELLS = arrayOf(
        intArrayOf(R.id.w_cell_1, R.id.w_cell_1_value, R.id.w_cell_1_label),
        intArrayOf(R.id.w_cell_2, R.id.w_cell_2_value, R.id.w_cell_2_label),
        intArrayOf(R.id.w_cell_3, R.id.w_cell_3_value, R.id.w_cell_3_label),
        intArrayOf(R.id.w_cell_4, R.id.w_cell_4_value, R.id.w_cell_4_label),
    )
    private val ROWS = arrayOf(
        intArrayOf(R.id.w_row_1, R.id.w_row_1_text, R.id.w_row_1_meta, R.id.w_row_1_trail),
        intArrayOf(R.id.w_row_2, R.id.w_row_2_text, R.id.w_row_2_meta, R.id.w_row_2_trail),
        intArrayOf(R.id.w_row_3, R.id.w_row_3_text, R.id.w_row_3_meta, R.id.w_row_3_trail),
        intArrayOf(R.id.w_row_4, R.id.w_row_4_text, R.id.w_row_4_meta, R.id.w_row_4_trail),
    )
    private val LINES = intArrayOf(R.id.w_line_1, R.id.w_line_2, R.id.w_line_3, R.id.w_line_4)

    /** Ids each skeleton's layout must contain (checked against the XML by WidgetResourcesTest). */
    fun idsFor(sk: Skeleton): List<Int> = buildList {
        if (sk.header) add(R.id.w_title)
        if (sk.asOf) add(R.id.w_asof)
        if (sk.value) { add(R.id.w_value); add(R.id.w_label) }
        if (sk.caption) add(R.id.w_caption)
        if (sk.lead) add(R.id.w_lead)
        if (sk.text) add(R.id.w_text)
        if (sk.cells > 0) { add(R.id.w_cells); CELLS.take(sk.cells).forEach { c -> c.forEach(::add) } }
        ROWS.take(sk.rows).forEach { r -> r.forEach(::add) }
        LINES.take(sk.lines).forEach(::add)
        if (sk.empty) add(R.id.w_empty)
        if (sk.footer) add(R.id.w_footer)
    }

    fun views(context: Context, appWidgetId: Int, kind: WidgetKind, faceFor: (WidgetSize) -> WidgetFace, options: Bundle?): RemoteViews {
        val fontScale = context.resources.configuration.fontScale
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val map = kind.breakpoints(fontScale).associate { bp ->
                SizeF(bp.widthDp, bp.heightDp) to bind(context, appWidgetId, bp.size, faceFor(bp.size))
            }
            return RemoteViews(map)
        }
        val size = legacySize(kind, options, fontScale)
        return bind(context, appWidgetId, size, faceFor(size))
    }

    /**
     * Pre-31 hosts report min/max width and height. The portrait size (minWidth x maxHeight) is used
     * whatever the app's own orientation is right now (see [WidgetKind.legacySizeFor]).
     */
    internal fun legacySize(kind: WidgetKind, options: Bundle?, fontScale: Float): WidgetSize =
        kind.legacySizeFor(
            options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH) ?: 0,
            options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT) ?: 0,
            fontScale,
        )

    internal fun bind(context: Context, appWidgetId: Int, size: WidgetSize, face: WidgetFace): RemoteViews {
        val sk = face.skeleton
        val rv = RemoteViews(context.packageName, sk.layout)
        val bidi = BidiFormatter.getInstance()
        rv.setContentDescription(android.R.id.background, face.description)
        rv.setOnClickPendingIntent(android.R.id.background, tapIntent(context, appWidgetId, size, 0, face.tap))
        if (sk.header) rv.setTextViewText(R.id.w_title, face.title)
        if (sk.asOf) text(rv, R.id.w_asof, face.asOf)
        if (sk.value) { text(rv, R.id.w_value, face.value); text(rv, R.id.w_label, face.label) }
        if (sk.caption) text(rv, R.id.w_caption, face.caption)
        if (sk.lead) text(rv, R.id.w_lead, face.lead)
        if (sk.text) text(rv, R.id.w_text, face.text?.let(bidi::unicodeWrap))
        // The small line layout gives the text one line less while the as-of line shows, so both fit.
        if (sk.textLinesWithAsOf > 0) rv.setInt(R.id.w_text, "setMaxLines", if (face.asOf.isNullOrBlank()) sk.textLines else sk.textLinesWithAsOf)
        if (sk.cells > 0) {
            rv.setViewVisibility(R.id.w_cells, if (face.cells.isEmpty()) View.GONE else View.VISIBLE)
            for (i in 0 until sk.cells) {
                val (container, value, label) = CELLS[i].let { Triple(it[0], it[1], it[2]) }
                val cell = face.cells.getOrNull(i)
                rv.setViewVisibility(container, if (cell == null) View.INVISIBLE else View.VISIBLE)
                if (cell != null) { rv.setTextViewText(value, cell.value); rv.setTextViewText(label, cell.label) }
            }
        }
        for (i in 0 until sk.rows) {
            val (container, textId, metaId, trailId) = ROWS[i].let { listOf(it[0], it[1], it[2], it[3]) }
            val row = face.rows.getOrNull(i)
            if (row == null) { rv.setViewVisibility(container, View.GONE); continue }
            rv.setViewVisibility(container, View.VISIBLE)
            rv.setTextViewText(textId, bidi.unicodeWrap(row.text))
            text(rv, metaId, row.meta?.let(bidi::unicodeWrap))
            text(rv, trailId, row.trail)
            if (sk.rowsTappable && row.tap != null) {
                rv.setOnClickPendingIntent(container, tapIntent(context, appWidgetId, size, i + 1, row.tap))
                rv.setContentDescription(container, row.description)
            }
        }
        for (i in 0 until sk.lines) text(rv, LINES[i], face.lines.getOrNull(i)?.let(bidi::unicodeWrap))
        if (sk.empty) text(rv, R.id.w_empty, face.empty)
        if (sk.footer) text(rv, R.id.w_footer, face.footer)
        return rv
    }

    private fun text(rv: RemoteViews, id: Int, value: String?) {
        if (value.isNullOrBlank()) {
            rv.setViewVisibility(id, View.GONE)
        } else {
            rv.setViewVisibility(id, View.VISIBLE)
            rv.setTextViewText(id, value)
        }
    }

    /**
     * An explicit, immutable intent to MainActivity naming the destination. The request code is
     * unique per widget, size and tap slot, so two sizes of one widget never share (and overwrite)
     * a PendingIntent.
     */
    private fun tapIntent(context: Context, appWidgetId: Int, size: WidgetSize, slot: Int, tap: WidgetTap): PendingIntent {
        val (dest, arg) = tap.encode()
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(WidgetTap.EXTRA_DEST, dest)
        if (arg != null) intent.putExtra(WidgetTap.EXTRA_ARG, arg)
        return PendingIntent.getActivity(
            context, requestCode(appWidgetId, size, slot), intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    fun requestCode(appWidgetId: Int, size: WidgetSize, slot: Int): Int = (appWidgetId * WidgetSize.entries.size + size.ordinal) * SLOTS + slot

    private const val SLOTS = 8
}

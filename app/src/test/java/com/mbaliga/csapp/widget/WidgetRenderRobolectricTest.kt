package com.mbaliga.csapp.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.mbaliga.csapp.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Inflates every widget at every size through real RemoteViews (as a launcher would) and checks the
 * views show exactly the face: texts, hidden slots, tappable rows. Rendering on a real launcher is
 * still owner-verified; this proves the binding, not the look.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class WidgetRenderRobolectricTest {
    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    private val faces = WidgetFaces(ResourceWidgetText(ctx), TestClock.clock())
    private val isolates = Regex("[⁦⁧⁨⁩‎‏‪-‮]")

    private fun inflate(face: WidgetFace, size: WidgetSize): View =
        WidgetRenderer.bind(ctx, 7, size, face).apply(ctx, FrameLayout(ctx))

    private fun View.text(id: Int): String? = findViewById<TextView>(id)?.takeIf { it.visibility == View.VISIBLE }?.text?.toString()?.replace(isolates, "")

    @Test fun `every widget, every size, rich and empty, renders its face`() {
        val rowIds = listOf(R.id.w_row_1, R.id.w_row_2, R.id.w_row_3, R.id.w_row_4)
        val rowText = listOf(R.id.w_row_1_text, R.id.w_row_2_text, R.id.w_row_3_text, R.id.w_row_4_text)
        val rowMeta = listOf(R.id.w_row_1_meta, R.id.w_row_2_meta, R.id.w_row_3_meta, R.id.w_row_4_meta)
        val cellValue = listOf(R.id.w_cell_1_value, R.id.w_cell_2_value, R.id.w_cell_3_value, R.id.w_cell_4_value)
        val lineIds = listOf(R.id.w_line_1, R.id.w_line_2, R.id.w_line_3, R.id.w_line_4)
        var rendered = 0
        for (snap in listOf(richSnapshot(), WidgetSnapshot(projects = PROJECTS), WidgetSnapshot(firstRun = true), freshSnapshot())) {
            for (kind in WidgetKind.entries) for (size in WidgetSize.entries) {
                val face = faces.face(kind, size, snap)
                val v = inflate(face, size)
                val where = "$kind $size"
                assertEquals(where, android.R.id.background, v.id)
                assertEquals(where, face.description, v.contentDescription?.toString())
                assertTrue(where, v.hasOnClickListeners())
                val sk = face.skeleton
                if (sk.header) assertEquals(where, face.title, v.text(R.id.w_title))
                if (sk.asOf) assertEquals(where, face.asOf, v.text(R.id.w_asof))
                if (sk.textLinesWithAsOf > 0) {
                    val lines = v.findViewById<TextView>(R.id.w_text).maxLines
                    assertEquals("$where text lines", if (face.asOf == null) sk.textLines else sk.textLinesWithAsOf, lines)
                }
                if (sk.value) { assertEquals(where, face.value, v.text(R.id.w_value)); assertEquals(where, face.label, v.text(R.id.w_label)) }
                if (sk.caption) assertEquals(where, face.caption, v.text(R.id.w_caption))
                if (sk.lead) assertEquals(where, face.lead, v.text(R.id.w_lead))
                if (sk.text) assertEquals(where, face.text, v.text(R.id.w_text))
                for (i in 0 until sk.cells) assertEquals("$where cell $i", face.cells.getOrNull(i)?.value, findCell(v, cellValue[i]))
                for (i in 0 until sk.rows) {
                    val row = face.rows.getOrNull(i)
                    val container = v.findViewById<View>(rowIds[i])
                    if (row == null) { assertEquals("$where row $i", View.GONE, container.visibility); continue }
                    assertEquals("$where row $i", View.VISIBLE, container.visibility)
                    assertEquals("$where row $i", row.text, v.text(rowText[i]))
                    assertEquals("$where row $i", row.meta, v.text(rowMeta[i]))
                    assertEquals("$where row $i tappable", sk.rowsTappable && row.tap != null, container.hasOnClickListeners())
                }
                for (i in 0 until sk.lines) assertEquals("$where line $i", face.lines.getOrNull(i), v.text(lineIds[i]))
                if (sk.empty) assertEquals(where, face.empty, v.text(R.id.w_empty))
                if (sk.footer) assertEquals(where, face.footer, v.text(R.id.w_footer))
                rendered++
            }
        }
        assertEquals(4 * 6 * 4, rendered)
    }

    @Test fun `API 31+ hands the launcher every size in one RemoteViews`() {
        val snap = richSnapshot()
        for (kind in WidgetKind.entries) {
            val rv = WidgetRenderer.views(ctx, 9, kind, { faces.face(kind, it, snap) }, null)
            // A size-mapped RemoteViews applies on its own (no app code runs in the launcher) and
            // inflates one of the kind's skeletons, filled.
            val v = rv.apply(ctx, FrameLayout(ctx))
            assertEquals(android.R.id.background, v.id)
            val filled = listOf(R.id.w_value, R.id.w_lead, R.id.w_title, R.id.w_text).mapNotNull { v.findViewById<TextView>(it) }
            assertTrue("$kind", filled.any { it.visibility == View.VISIBLE && it.text.isNotBlank() })
        }
    }

    @Test @Config(sdk = [26])
    fun `below API 31 the layout follows the portrait size, not the app's current orientation`() {
        val snap = richSnapshot()
        val kind = WidgetKind.INCIDENT_LIST
        // A 4x3 widget: portrait 276 x 338, landscape 554 x 184.
        val options = Bundle().apply {
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 276)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 338)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 554)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 184)
        }
        for (orientation in listOf("port", "land")) {
            RuntimeEnvironment.setQualifiers("+$orientation")
            val c = ApplicationProvider.getApplicationContext<Context>()
            val rv = WidgetRenderer.views(c, 3, kind, { faces.face(kind, it, snap) }, options)
            assertEquals(orientation, R.layout.widget_list_large, rv.layoutId)
            val v = rv.apply(c, FrameLayout(c))
            assertEquals(orientation, View.VISIBLE, v.findViewById<View>(R.id.w_row_4).visibility)
        }
        // A one-row widget gets the one-line summary; no options at all, the small layout.
        val oneRow = Bundle().apply {
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 276)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 51)
        }
        assertEquals(R.layout.widget_line_tiny, WidgetRenderer.views(ctx, 3, kind, { faces.face(kind, it, snap) }, oneRow).layoutId)
        assertEquals(R.layout.widget_line_small, WidgetRenderer.views(ctx, 3, kind, { faces.face(kind, it, snap) }, null).layoutId)
    }

    private fun findCell(v: View, valueId: Int): String? {
        val tv = v.findViewById<TextView>(valueId) ?: return null
        val cell = tv.parent as View
        if (cell.visibility != View.VISIBLE) return null
        val grid = v.findViewById<View>(R.id.w_cells)
        if (grid.visibility != View.VISIBLE) return null
        return tv.text.toString()
    }
}

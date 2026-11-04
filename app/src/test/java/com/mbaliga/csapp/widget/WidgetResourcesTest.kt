package com.mbaliga.csapp.widget

import com.mbaliga.csapp.R
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * The widget resources, provider declarations and manifest entries agree with the code, and follow
 * the home-screen rules (RemoteViews-safe views, text sizes, contrast, no new permissions).
 */
class WidgetResourcesTest {
    private val res = File("src/main/res")
    private val android = "http://schemas.android.com/apk/res/android"

    private fun parse(f: File): Element = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        .newDocumentBuilder().parse(f).documentElement

    private fun Element.all(): List<Element> {
        val out = mutableListOf(this)
        val kids = childNodes
        for (i in 0 until kids.length) (kids.item(i) as? Element)?.let { out += it.all() }
        return out
    }

    private fun Element.a(name: String) = getAttributeNS(android, name)
    private fun idName(id: Int) = XmlWidgetText.nameOf(R.id::class.java, id)
    private fun layoutName(id: Int) = XmlWidgetText.nameOf(R.layout::class.java, id)
    private fun widgetLayouts() = File(res, "layout").listFiles { f -> f.name.startsWith("widget_") }!!.sortedBy { it.name }

    @Test fun `each skeleton layout has every slot the renderer fills`() {
        for (sk in Skeleton.entries) {
            val file = File(res, "layout/${layoutName(sk.layout)}.xml")
            val ids = parse(file).all().map { it.a("id") }.filter { it.startsWith("@+id/") }.map { it.removePrefix("@+id/") }.toSet()
            val missing = WidgetRenderer.idsFor(sk).map(::idName).filter { it !in ids }
            assertTrue("${file.name} lacks $missing", missing.isEmpty())
        }
    }

    @Test fun `widget layouts are RemoteViews-safe, rounded, readable and ellipsized`() {
        val allowed = setOf("LinearLayout", "FrameLayout", "RelativeLayout", "TextView", "ImageView")
        val layouts = widgetLayouts()
        assertTrue(layouts.size >= 17)
        for (f in layouts) {
            val root = parse(f)
            assertEquals(f.name, "@android:id/background", root.a("id"))
            assertEquals(f.name, "@drawable/widget_bg", root.a("background"))
            for (e in root.all()) {
                assertTrue("${f.name}: <${e.tagName}> is not a RemoteViews view", e.tagName in allowed)
                if (e.tagName == "TextView") {
                    val sp = e.a("textSize").removeSuffix("sp").toFloat()
                    assertTrue("${f.name}: text under 12sp", sp >= 12f)
                    assertTrue("${f.name}: TextView without maxLines", e.a("maxLines").isNotEmpty())
                    assertEquals("${f.name}: TextView not ellipsized", "end", e.a("ellipsize"))
                    assertTrue("${f.name}: text not in sp", e.a("textSize").endsWith("sp"))
                }
                assertTrue("${f.name}: use start/end", e.a("layout_marginLeft").isEmpty() && e.a("layout_marginRight").isEmpty())
            }
        }
    }

    // ---- heights: a layout never needs more than the breakpoint that selects it ----

    private val dimens by lazy {
        parse(File(res, "values/widget_dimens.xml")).all().filter { it.tagName == "dimen" }.associate { it.getAttribute("name") to it.textContent.trim() }
    }

    private fun dp(v: String): Float = when {
        v.isEmpty() -> 0f
        v.startsWith("@dimen/") -> dp(dimens.getValue(v.removePrefix("@dimen/")))
        else -> v.removeSuffix("dp").toFloat()
    }

    private fun kids(e: Element): List<Element> = (0 until e.childNodes.length).mapNotNull { e.childNodes.item(it) as? Element }

    /**
     * Height (dp) [e] needs at font scale 1.0, with every view visible except [hidden] ids and with
     * [lines] overriding maxLines. Each text line counts as 1.4 x its size: Roboto with font padding
     * is about 1.33 for one line, and the symbol fallback fonts behind the glyphs (◆ ★ ◉ ✕) can raise a
     * line's height, so 1.4 is a deliberate upper bound.
     */
    private fun need(e: Element, hidden: Set<String>, lines: Map<String, Int>): Float {
        val id = e.a("id").removePrefix("@+id/")
        if (id in hidden) return 0f
        val pad = e.a("padding")
        val padding = dp(e.a("paddingTop").ifEmpty { pad }) + dp(e.a("paddingBottom").ifEmpty { pad })
        val content = if (e.tagName == "TextView") {
            LINE * e.a("textSize").removeSuffix("sp").toFloat() * (lines[id] ?: e.a("maxLines").toInt())
        } else {
            val sizes = kids(e).map { k -> need(k, hidden, lines).let { h -> if (h == 0f) 0f else h + dp(k.a("layout_marginTop")) + dp(k.a("layout_marginBottom")) } }
            if (e.a("orientation") == "horizontal") sizes.maxOrNull() ?: 0f else sizes.sum()
        }
        return maxOf(content + padding, dp(e.a("minHeight")))
    }

    /** The tallest the skeleton's layout gets: everything shown, then the empty state, then without the as-of line. */
    private fun need(sk: Skeleton): Float {
        val root = parse(File(res, "layout/${layoutName(sk.layout)}.xml"))
        val withAsOf = if (sk.textLinesWithAsOf > 0) mapOf("w_text" to sk.textLinesWithAsOf) else emptyMap()
        val listed = setOf("w_cells", "w_row_1", "w_row_2", "w_row_3", "w_row_4")
        return maxOf(need(root, setOf("w_empty"), withAsOf), need(root, setOf("w_empty", "w_asof"), emptyMap()), need(root, listed, withAsOf))
    }

    @Test fun `each skeleton's declared height covers its layout, closely`() {
        for (sk in Skeleton.entries) {
            val computed = need(sk)
            assertTrue("${sk.name} needs ${computed}dp but declares ${sk.needDp}", sk.needDp >= computed)
            assertTrue("${sk.name} declares ${sk.needDp}dp, far above the ${computed}dp it needs", sk.needDp <= computed + 6f)
        }
        // The one-line layout fits the smallest height a widget can be resized to.
        assertTrue(Skeleton.LINE_TINY.needDp <= WidgetKind.MIN_HEIGHT_DP)
    }

    @Test fun `every breakpoint leaves room for its layouts, the first-run and error ones too, at any font scale`() {
        for (kind in WidgetKind.entries) for (scale in listOf(1f, 1.3f, 2f)) {
            for (bp in kind.breakpoints(scale).drop(1)) {
                for (sk in listOf(kind.skeleton(bp.size), Skeleton.message(bp.size))) {
                    assertTrue("$kind ${bp.size} x$scale: ${sk.name} needs more than ${bp.heightDp}dp", bp.heightDp >= sk.needDp * scale)
                }
            }
        }
    }

    @Test fun `the small line layout's text keeps the lines the renderer assumes`() {
        for (sk in Skeleton.entries.filter { it.textLinesWithAsOf > 0 }) {
            val text = parse(File(res, "layout/${layoutName(sk.layout)}.xml")).all().single { it.a("id") == "@+id/w_text" }
            assertEquals(sk.name, sk.textLines.toString(), text.a("maxLines"))
            assertTrue(sk.name, sk.textLinesWithAsOf < sk.textLines)
        }
    }

    @Test fun `tappable rows are at least 48dp and every compact row stays a pass-through`() {
        val dimens = parse(File(res, "values/widget_dimens.xml")).all().filter { it.tagName == "dimen" }.associate { it.getAttribute("name") to it.textContent }
        assertEquals("48dp", dimens["widget_touch_min"])
        for (sk in Skeleton.entries.filter { it.rows > 0 }) {
            val root = parse(File(res, "layout/${layoutName(sk.layout)}.xml"))
            val rows = root.all().filter { it.a("id").matches(Regex("""@\+id/w_row_\d""")) }
            assertEquals(sk.name, sk.rows, rows.size)
            for (r in rows) {
                if (sk.rowsTappable) assertEquals(sk.name, "@dimen/widget_touch_min", r.a("minHeight"))
                else assertTrue(sk.name, r.a("minHeight").isEmpty())
            }
        }
    }

    @Test fun `provider declarations - resizable, three-size capable, pushed updates, previews, descriptions`() {
        val infos = File(res, "xml").listFiles { f -> f.name.startsWith("widget_info_") }!!
        assertEquals(6, infos.size)
        val strings = XmlWidgetText().strings
        for (f in infos) {
            val e = parse(f)
            assertEquals(f.name, "appwidget-provider", e.tagName)
            assertEquals(f.name, "horizontal|vertical", e.a("resizeMode"))
            assertEquals(f.name, "home_screen", e.a("widgetCategory"))
            assertEquals(f.name, "0", e.a("updatePeriodMillis"))
            assertEquals(f.name, "110dp", e.a("minResizeWidth"))
            assertEquals(f.name, "40dp", e.a("minResizeHeight"))
            for (attr in listOf("minWidth", "minHeight", "targetCellWidth", "targetCellHeight")) assertTrue("${f.name} $attr", e.a(attr).isNotEmpty())
            assertEquals(f.name, "@layout/widget_loading", e.a("initialLayout"))
            assertTrue(File(res, "layout/${e.a("previewLayout").removePrefix("@layout/")}.xml").exists())
            assertTrue(File(res, "drawable/${e.a("previewImage").removePrefix("@drawable/")}.xml").exists())
            val description = e.a("description").removePrefix("@string/")
            assertTrue(f.name, strings.containsKey(description))
            assertTrue("${f.name}: the product name lives in app_name only", !strings.getValue(description).contains(strings.getValue("app_name")))
        }
    }

    @Test fun `manifest - six exported providers, a private tick receiver, no new permissions`() {
        val manifest = parse(File("src/main/AndroidManifest.xml"))
        val receivers = manifest.all().filter { it.tagName == "receiver" }.associateBy { it.a("name") }
        for ((_, cls) in CsWidgets.providers) {
            val r = receivers[".widget.${cls.simpleName}"] ?: error("${cls.simpleName} not registered")
            assertEquals("true", r.a("exported"))
            val actions = r.all().filter { it.tagName == "action" }.map { it.a("name") }
            assertTrue(cls.simpleName, "android.appwidget.action.APPWIDGET_UPDATE" in actions)
            val meta = r.all().single { it.tagName == "meta-data" }
            assertEquals("android.appwidget.provider", meta.a("name"))
            assertTrue(File(res, "xml/${meta.a("resource").removePrefix("@xml/")}.xml").exists())
            assertTrue(r.a("label").startsWith("@string/widget_"))
        }
        assertEquals("false", receivers.getValue(".widget.WidgetRefreshReceiver").a("exported"))
        val permissions = manifest.all().filter { it.tagName == "uses-permission" }.map { it.a("name") }.toSet()
        assertEquals(setOf("android.permission.INTERNET", "android.permission.ACCESS_NETWORK_STATE"), permissions)
    }

    @Test fun `every widget string and plural the code names exists`() {
        val t = XmlWidgetText()
        WS.entries.forEach { s -> assertTrue("$s", t.strings.containsKey(XmlWidgetText.nameOf(R.string::class.java, s.res))) }
        WP.entries.forEach { p ->
            val forms = t.plurals[XmlWidgetText.nameOf(R.plurals::class.java, p.res)] ?: error("$p missing")
            assertTrue("$p needs one and other", forms.keys.containsAll(listOf("one", "other")))
        }
    }

    @Test fun `widget text colours pass WCAG AA on the widget surface, day and night`() {
        for (dir in listOf("values", "values-night")) {
            val colors = parse(File(res, "$dir/widget_colors.xml")).all().filter { it.tagName == "color" }
                .associate { it.getAttribute("name") to it.textContent.trim() }
            val bg = colors.getValue("widget_surface")
            for (fg in listOf("widget_text", "widget_muted", "widget_accent")) {
                val ratio = contrast(colors.getValue(fg), bg)
                assertTrue("$dir $fg on surface = $ratio", ratio >= 4.5)
            }
        }
    }

    private companion object {
        const val LINE = 1.4f
    }

    private fun contrast(a: String, b: String): Double {
        val la = luminance(a); val lb = luminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    private fun luminance(hex: String): Double {
        val v = hex.removePrefix("#").takeLast(6).chunked(2).map { it.toInt(16) / 255.0 }
        val lin = v.map { c -> if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4) }
        return 0.2126 * lin[0] + 0.7152 * lin[1] + 0.0722 * lin[2]
    }
}

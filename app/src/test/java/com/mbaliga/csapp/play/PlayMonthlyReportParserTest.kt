package com.mbaliga.csapp.play

import com.mbaliga.csapp.data.play.PlayMonthlyReportParser
import java.io.ByteArrayInputStream
import java.nio.charset.Charset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlayMonthlyReportParserTest {

    private val utf16le = Charset.forName("UTF-16LE")

    private fun utf16InputStreamWithBom(text: String): ByteArrayInputStream {
        val bom = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) // UTF-16LE BOM
        return ByteArrayInputStream(bom + text.toByteArray(utf16le))
    }

    @Test
    fun `parses a UTF-16 encoded monthly report with a BOM`() {
        val csv = "Package Name,Star Rating,Review Text,Review Submit Millis Since Epoch\n" +
            "com.example.app,4,\"Great app, works well\",1700000000000\n"

        val rows = PlayMonthlyReportParser.parse(utf16InputStreamWithBom(csv))

        assertEquals(1, rows.size)
        val row = rows.first()
        assertEquals("com.example.app", row.packageName)
        assertEquals(4, row.starRating)
        assertEquals("Great app, works well", row.reviewText)
        assertEquals(1700000000000L, row.submitMillisSinceEpoch)
    }

    @Test
    fun `preserves non-ASCII review text`() {
        val csv = "Package Name,Star Rating,Review Text\n" +
            "com.example.app,5,\"日本語のレビューです\"\n"

        val rows = PlayMonthlyReportParser.parse(utf16InputStreamWithBom(csv))

        assertEquals("日本語のレビューです", rows.first().reviewText)
    }

    @Test
    fun `handles embedded commas and escaped quotes inside quoted fields`() {
        val csv = "Package Name,Review Text\n" +
            "com.example.app,\"He said \"\"great, app\"\" today\"\n"

        val rows = PlayMonthlyReportParser.parse(utf16InputStreamWithBom(csv))

        assertEquals("He said \"great, app\" today", rows.first().reviewText)
    }

    @Test
    fun `missing optional columns become null rather than throwing`() {
        val csv = "Package Name,Review Text\ncom.example.app,hello\n"

        val rows = PlayMonthlyReportParser.parse(utf16InputStreamWithBom(csv))

        assertNull(rows.first().starRating)
        assertNull(rows.first().appVersionCode)
    }

    @Test
    fun `column order does not matter, lookup is by header name`() {
        val csv = "Review Text,Package Name,Star Rating\nhello,com.example.app,3\n"

        val rows = PlayMonthlyReportParser.parse(utf16InputStreamWithBom(csv))

        assertEquals("com.example.app", rows.first().packageName)
        assertEquals("hello", rows.first().reviewText)
        assertEquals(3, rows.first().starRating)
    }
}

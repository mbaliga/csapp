package com.mbaliga.csapp.data.play

import java.io.InputStream
import java.io.InputStreamReader

/**
 * A single row of a Play Console monthly "reviews" report export. These reports are the only way
 * to backfill review history older than the ~1 week window the reviews.list API exposes; they
 * are downloaded from Play Console > Statistics as CSV files encoded in UTF-16 (with a byte-order
 * mark) rather than UTF-8, using comma-separated, double-quoted fields (RFC 4180-ish).
 */
data class PlayMonthlyReportRow(
    val packageName: String,
    val appVersionCode: String?,
    val appVersionName: String?,
    val reviewerLanguage: String?,
    val device: String?,
    val submitMillisSinceEpoch: Long?,
    val lastUpdateMillisSinceEpoch: Long?,
    val starRating: Int?,
    val reviewTitle: String?,
    val reviewText: String,
    val developerReplyMillisSinceEpoch: Long?,
    val developerReplyText: String?,
    val reviewLink: String?,
)

/**
 * Parses a Play Console monthly review-report export. The report is UTF-16 encoded (Google's
 * export tooling writes it that way, unlike almost every other CSV export, to preserve review
 * text in all languages without UTF-8-vs-locale ambiguity); attempting to parse it as UTF-8
 * silently corrupts every non-ASCII character, so decoding as UTF-16 is not optional.
 *
 * Column lookup is done by header name (not position) so a reordering or an additional column
 * added by Google does not break parsing; unrecognized columns are ignored, missing optional
 * columns are surfaced as null.
 */
object PlayMonthlyReportParser {

    // Expressed as a code point, not a literal byte-order-mark character, so this source file
    // itself doesn't contain a raw BOM (which Android Lint flags as ByteOrderMark).
    private const val BOM_CHAR = '\uFEFF'

    private val COLUMN_ALIASES = mapOf(
        "packageName" to listOf("Package Name"),
        "appVersionCode" to listOf("App Version Code"),
        "appVersionName" to listOf("App Version Name"),
        "reviewerLanguage" to listOf("Reviewer Language"),
        "device" to listOf("Device"),
        "submitMillis" to listOf("Review Submit Millis Since Epoch"),
        "lastUpdateMillis" to listOf("Review Last Update Millis Since Epoch"),
        "starRating" to listOf("Star Rating"),
        "reviewTitle" to listOf("Review Title"),
        "reviewText" to listOf("Review Text"),
        "developerReplyMillis" to listOf("Developer Reply Millis Since Epoch"),
        "developerReplyText" to listOf("Developer Reply Text"),
        "reviewLink" to listOf("Review Link"),
    )

    fun parse(input: InputStream): List<PlayMonthlyReportRow> {
        val text = InputStreamReader(input, Charsets.UTF_16).use { it.readText() }
        return parse(text)
    }

    fun parse(text: String): List<PlayMonthlyReportRow> {
        val records = parseCsvRecords(text)
        if (records.isEmpty()) return emptyList()

        val header = records.first()
        val columnIndex = mutableMapOf<String, Int>()
        for ((field, aliases) in COLUMN_ALIASES) {
            val idx = header.indexOfFirst { headerCell -> aliases.any { it.equals(headerCell.trim(), ignoreCase = true) } }
            if (idx >= 0) columnIndex[field] = idx
        }

        fun cell(row: List<String>, field: String): String? {
            val idx = columnIndex[field] ?: return null
            return row.getOrNull(idx)?.trim()?.ifBlank { null }
        }

        return records.drop(1)
            .filter { it.isNotEmpty() && it.any { cellValue -> cellValue.isNotBlank() } }
            .mapNotNull { row ->
                val packageName = cell(row, "packageName") ?: return@mapNotNull null
                PlayMonthlyReportRow(
                    packageName = packageName,
                    appVersionCode = cell(row, "appVersionCode"),
                    appVersionName = cell(row, "appVersionName"),
                    reviewerLanguage = cell(row, "reviewerLanguage"),
                    device = cell(row, "device"),
                    submitMillisSinceEpoch = cell(row, "submitMillis")?.toLongOrNull(),
                    lastUpdateMillisSinceEpoch = cell(row, "lastUpdateMillis")?.toLongOrNull(),
                    starRating = cell(row, "starRating")?.toIntOrNull(),
                    reviewTitle = cell(row, "reviewTitle"),
                    reviewText = cell(row, "reviewText").orEmpty(),
                    developerReplyMillisSinceEpoch = cell(row, "developerReplyMillis")?.toLongOrNull(),
                    developerReplyText = cell(row, "developerReplyText"),
                    reviewLink = cell(row, "reviewLink"),
                )
            }
    }

    /** RFC-4180-ish CSV parser: handles quoted fields, embedded commas/newlines, and "" escapes. */
    private fun parseCsvRecords(text: String): List<List<String>> {
        val records = mutableListOf<List<String>>()
        var currentRow = mutableListOf<String>()
        val currentField = StringBuilder()
        var inQuotes = false
        var i = 0
        // Defensive: Charsets.UTF_16 already consumes the BOM while choosing byte order, but
        // strip a leftover U+FEFF if one somehow survives decoding (escape used, not a literal
        // BOM byte, to avoid embedding a raw byte-order-mark in this source file).
        val normalized = text.removePrefix(BOM_CHAR.toString())

        fun endField() {
            currentRow.add(currentField.toString())
            currentField.clear()
        }

        fun endRow() {
            endField()
            records.add(currentRow)
            currentRow = mutableListOf()
        }

        while (i < normalized.length) {
            val c = normalized[i]
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < normalized.length && normalized[i + 1] == '"') {
                        currentField.append('"')
                        i += 2
                        continue
                    } else {
                        inQuotes = false
                        i += 1
                        continue
                    }
                } else {
                    currentField.append(c)
                    i += 1
                    continue
                }
            } else {
                when (c) {
                    '"' -> {
                        inQuotes = true
                        i += 1
                    }
                    ',' -> {
                        endField()
                        i += 1
                    }
                    '\r' -> {
                        i += 1
                    }
                    '\n' -> {
                        endRow()
                        i += 1
                    }
                    else -> {
                        currentField.append(c)
                        i += 1
                    }
                }
            }
        }
        if (currentField.isNotEmpty() || currentRow.isNotEmpty()) endRow()
        return records
    }
}

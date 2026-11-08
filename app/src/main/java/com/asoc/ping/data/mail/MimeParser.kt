package com.asoc.ping.data.mail

import java.nio.charset.Charset
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Base64
import java.util.Locale

/** Minimal RFC 5322 / 2045 / 2047 parser: enough to turn a raw message into plain text. */
object MimeParser {

    class Part(val headers: Map<String, String>, val body: ByteArray) {
        fun header(name: String): String? = headers[name.lowercase()]
    }

    private val CRLF2 = byteArrayOf(13, 10, 13, 10)
    private val LF2 = byteArrayOf(10, 10)

    fun splitPart(raw: ByteArray): Part {
        var idx = indexOf(raw, CRLF2, 0)
        var skip = 4
        val lf = indexOf(raw, LF2, 0)
        if (idx < 0 || (lf in 0 until idx)) { idx = lf; skip = 2 }
        if (idx < 0) return Part(parseHeaders(String(raw, Charsets.ISO_8859_1)), ByteArray(0)).let {
            // headers only
            it
        }
        val headerText = String(raw, 0, idx, Charsets.ISO_8859_1)
        val body = raw.copyOfRange(minOf(raw.size, idx + skip), raw.size)
        return Part(parseHeaders(headerText), body)
    }

    fun parseHeaders(text: String): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        var name: String? = null
        val value = StringBuilder()
        fun flush() { name?.let { out.putIfAbsent(it, value.toString().trim()) }; name = null; value.clear() }
        for (line in text.split("\r\n", "\n")) {
            if (line.isEmpty()) continue
            if ((line[0] == ' ' || line[0] == '\t') && name != null) { value.append(' ').append(line.trim()); continue }
            flush()
            val colon = line.indexOf(':')
            if (colon > 0) { name = line.substring(0, colon).trim().lowercase(); value.append(line.substring(colon + 1)) }
        }
        flush()
        return out
    }

    /** RFC 2047 encoded-word decoding. */
    fun decodeHeader(value: String): String {
        val re = Regex("""=\?([^?]+)\?([bBqQ])\?([^?]*)\?=""")
        if (!re.containsMatchIn(value)) return value
        // whitespace between adjacent encoded words is dropped
        val collapsed = value.replace(Regex("""(\?=)\s+(=\?)"""), "$1$2")
        return re.replace(collapsed) { m ->
            val cs = charset(m.groupValues[1])
            val bytes = if (m.groupValues[2].equals("B", true)) {
                runCatching { Base64.getMimeDecoder().decode(m.groupValues[3]) }.getOrDefault(ByteArray(0))
            } else decodeQuotedPrintable(m.groupValues[3].replace('_', ' ').toByteArray(Charsets.ISO_8859_1))
            String(bytes, cs)
        }
    }

    fun decodeQuotedPrintable(input: ByteArray): ByteArray {
        val out = java.io.ByteArrayOutputStream(input.size)
        var i = 0
        while (i < input.size) {
            val b = input[i].toInt()
            if (b == '='.code) {
                if (i + 1 < input.size && (input[i + 1].toInt() == 13 || input[i + 1].toInt() == 10)) { // soft line break
                    i += if (input[i + 1].toInt() == 13 && i + 2 < input.size && input[i + 2].toInt() == 10) 3 else 2
                    continue
                }
                if (i + 2 < input.size) {
                    val h = Character.digit(input[i + 1].toInt().toChar(), 16)
                    val l = Character.digit(input[i + 2].toInt().toChar(), 16)
                    if (h >= 0 && l >= 0) { out.write(h * 16 + l); i += 3; continue }
                }
            }
            out.write(b); i++
        }
        return out.toByteArray()
    }

    private fun charset(name: String?): Charset =
        runCatching { Charset.forName(name?.trim('"', ' ') ?: "UTF-8") }.getOrDefault(Charsets.UTF_8)

    private fun param(headerValue: String?, key: String): String? {
        if (headerValue == null) return null
        val m = Regex("""(?i)\b${Regex.escape(key)}\s*=\s*("([^"]*)"|[^;\s]+)""").find(headerValue) ?: return null
        return (m.groups[2]?.value ?: m.groupValues[1]).trim()
    }

    private fun decodeBody(part: Part): String {
        val cte = part.header("content-transfer-encoding")?.trim()?.lowercase()
        val bytes = when (cte) {
            "base64" -> runCatching { Base64.getMimeDecoder().decode(part.body) }.getOrDefault(part.body)
            "quoted-printable" -> decodeQuotedPrintable(part.body)
            else -> part.body
        }
        return String(bytes, charset(param(part.header("content-type"), "charset")))
    }

    fun htmlToText(html: String): String {
        var t = html.replace(Regex("(?is)<(script|style)[^>]*>.*?</\\1>"), " ")
        t = t.replace(Regex("(?i)<br\\s*/?>|</p>|</div>|</li>|</tr>"), "\n")
        t = t.replace(Regex("<[^>]+>"), "")
        t = t.replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'").replace("&amp;", "&")
        return t.lines().joinToString("\n") { it.trim() }.replace(Regex("\n{3,}"), "\n\n").trim()
    }

    data class Content(val text: String, val attachments: List<String>)

    fun extractContent(part: Part): Content {
        val attachments = mutableListOf<String>()
        var plain: String? = null
        var html: String? = null
        fun walk(p: Part) {
            val ct = (p.header("content-type") ?: "text/plain").lowercase()
            val disp = p.header("content-disposition")?.lowercase().orEmpty()
            val filename = param(p.header("content-disposition"), "filename") ?: param(p.header("content-type"), "name")
            if (ct.startsWith("multipart/")) {
                val boundary = param(p.header("content-type"), "boundary") ?: return
                for (sub in splitMultipart(p.body, boundary)) walk(splitPart(sub))
            } else if (disp.startsWith("attachment") || (filename != null && !ct.startsWith("text/plain") && !ct.startsWith("text/html"))) {
                filename?.let { attachments += decodeHeader(it) }
            } else if (ct.startsWith("text/plain")) {
                if (plain == null) plain = decodeBody(p)
            } else if (ct.startsWith("text/html")) {
                if (html == null) html = decodeBody(p)
            } else if (filename != null) attachments += decodeHeader(filename)
        }
        walk(part)
        val text = plain?.trim() ?: html?.let { htmlToText(it) } ?: ""
        return Content(text, attachments)
    }

    private fun splitMultipart(body: ByteArray, boundary: String): List<ByteArray> {
        val text = String(body, Charsets.ISO_8859_1)
        val delim = "--$boundary"
        val parts = mutableListOf<ByteArray>()
        var pos = text.indexOf(delim)
        while (pos >= 0) {
            val lineEnd = text.indexOf('\n', pos)
            if (lineEnd < 0) break
            if (text.startsWith("--", pos + delim.length)) break
            val next = text.indexOf("\n$delim", lineEnd)
            val end = if (next < 0) text.length else next
            var chunk = text.substring(lineEnd + 1, end)
            if (chunk.endsWith("\r")) chunk = chunk.dropLast(1)
            parts += chunk.toByteArray(Charsets.ISO_8859_1)
            if (next < 0) break
            pos = next + 1
        }
        return parts
    }

    fun parseDate(value: String?, fallback: Long): Long {
        if (value.isNullOrBlank()) return fallback
        val cleaned = value.replace(Regex("\\s*\\(.*\\)\\s*$"), "").trim()
        return runCatching { OffsetDateTime.parse(cleaned, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() }
            .getOrElse {
                runCatching { java.time.ZonedDateTime.parse(cleaned, DateTimeFormatter.ofPattern("d MMM yyyy HH:mm:ss Z", Locale.ENGLISH)).toInstant().toEpochMilli() }
                    .getOrDefault(fallback)
            }
    }

    private fun ids(value: String?): List<String> =
        Regex("<([^<>\\s]+)>").findAll(value.orEmpty()).map { it.groupValues[1] }.toList()

    private fun addresses(value: String?): List<String> =
        decodeHeader(value.orEmpty()).split(",").map { it.trim() }.filter { it.isNotEmpty() }

    fun toMailMessage(uid: Long, raw: ByteArray, fallbackDate: Long): MailMessage {
        val part = splitPart(raw)
        val content = extractContent(part)
        val messageId = ids(part.header("message-id")).firstOrNull()
            ?: "noid-$uid-" + java.security.MessageDigest.getInstance("SHA-256").digest(raw).take(6).joinToString("") { "%02x".format(it) }
        return MailMessage(
            uid = uid,
            messageId = messageId,
            inReplyTo = ids(part.header("in-reply-to")).firstOrNull(),
            references = ids(part.header("references")),
            from = decodeHeader(part.header("from").orEmpty()),
            to = addresses(part.header("to")),
            subject = decodeHeader(part.header("subject").orEmpty()),
            dateMillis = parseDate(part.header("date"), fallbackDate),
            bodyText = content.text,
            attachmentNames = content.attachments,
        )
    }

    private fun indexOf(data: ByteArray, pat: ByteArray, from: Int): Int {
        outer@ for (i in from..data.size - pat.size) {
            for (j in pat.indices) if (data[i + j] != pat[j]) continue@outer
            return i
        }
        return -1
    }
}

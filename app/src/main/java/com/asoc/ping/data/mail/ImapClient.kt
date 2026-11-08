package com.asoc.ping.data.mail

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.net.ssl.SSLSocketFactory

/**
 * Minimal in-house IMAP4rev1 client (no JavaMail: licence rule). TLS + LOGIN (app-password) only,
 * read-only (`EXAMINE`, `BODY.PEEK`), no IDLE. Fetches full RFC 822 messages and hands them to
 * [MimeParser].
 */
class ImapClient(
    private val host: String,
    private val port: Int = 993,
    private val useTls: Boolean = true,
    private val timeoutMs: Int = 30_000,
    private val maxLiteralBytes: Int = 2_000_000,
    private val now: () -> Long = { System.currentTimeMillis() },
) {
    private class Literal(val bytes: ByteArray)
    private class Response(val lines: List<String>, val literals: List<Literal>, val status: String)

    private lateinit var input: InputStream
    private lateinit var output: OutputStream
    private var tagCounter = 0

    fun fetchNew(
        username: String,
        password: String,
        folder: String,
        checkpoint: MailCheckpointState?,
        firstRunLookbackMillis: Long = 7L * 24 * 3600 * 1000,
        maxMessages: Int = 100,
    ): MailFetchResult {
        val socket = openSocket()
        try {
            input = socket.getInputStream().buffered()
            output = socket.getOutputStream()
            val greeting = readLine()
            if (!greeting.startsWith("* OK") && !greeting.startsWith("* PREAUTH")) throw MailException("IMAP greeting: $greeting")

            command("LOGIN ${quote(username)} ${quote(password)}").also {
                if (it.status != "OK") throw MailException("IMAP login failed")
            }
            val select = command("EXAMINE ${quote(folder)}")
            if (select.status != "OK") throw MailException("IMAP cannot open folder $folder")
            val uidValidity = select.lines.firstNotNullOfOrNull { Regex("""\[UIDVALIDITY (\d+)]""").find(it)?.groupValues?.get(1)?.toLong() } ?: 0L

            val sameBox = checkpoint != null && checkpoint.uidValidity == uidValidity && checkpoint.lastUid > 0
            val searchCmd = if (sameBox) "UID SEARCH UID ${checkpoint!!.lastUid + 1}:*"
            else "UID SEARCH SINCE ${imapDate(now() - firstRunLookbackMillis)}"
            val search = command(searchCmd)
            if (search.status != "OK") throw MailException("IMAP search failed")
            val floor = if (sameBox) checkpoint!!.lastUid else 0L
            val uids = search.lines.filter { it.startsWith("* SEARCH") }
                .flatMap { it.removePrefix("* SEARCH").trim().split(" ").filter { s -> s.isNotEmpty() } }
                .mapNotNull { it.toLongOrNull() }.filter { it > floor }.sorted().take(maxMessages)

            val messages = mutableListOf<MailMessage>()
            var highest = floor
            for (uid in uids) {
                val fetch = command("UID FETCH $uid (BODY.PEEK[])")
                if (fetch.status != "OK") continue
                val raw = fetch.literals.firstOrNull()?.bytes ?: continue
                messages += MimeParser.toMailMessage(uid, raw, now())
                highest = maxOf(highest, uid)
            }
            runCatching { command("LOGOUT") }
            return MailFetchResult(messages, MailCheckpointState(uidValidity, if (uids.isEmpty()) floor else highest))
        } finally {
            runCatching { socket.close() }
        }
    }

    private fun openSocket(): Socket {
        val plain = Socket()
        plain.connect(InetSocketAddress(host, port), timeoutMs)
        plain.soTimeout = timeoutMs
        if (!useTls) return plain
        return (SSLSocketFactory.getDefault() as SSLSocketFactory).createSocket(plain, host, port, true)
    }

    private fun command(cmd: String): Response {
        val tag = "a${++tagCounter}"
        output.write("$tag $cmd\r\n".toByteArray(Charsets.ISO_8859_1)); output.flush()
        val lines = mutableListOf<String>()
        val literals = mutableListOf<Literal>()
        while (true) {
            var line = readLine()
            while (true) {
                val m = Regex("""\{(\d+)}$""").find(line) ?: break
                val n = m.groupValues[1].toInt()
                val keep = n <= maxLiteralBytes
                val buf = ByteArrayOutputStream()
                var remaining = n
                val chunk = ByteArray(8192)
                while (remaining > 0) {
                    val r = input.read(chunk, 0, minOf(chunk.size, remaining))
                    if (r < 0) throw MailException("IMAP connection closed in literal")
                    if (keep) buf.write(chunk, 0, r)
                    remaining -= r
                }
                if (keep) literals += Literal(buf.toByteArray())
                lines += line
                line = readLine() // remainder of the line after the literal, e.g. ")"
            }
            if (line.startsWith("$tag ")) {
                return Response(lines, literals, line.substring(tag.length + 1).substringBefore(' '))
            }
            lines += line
        }
    }

    private fun readLine(): String {
        val sb = ByteArrayOutputStream()
        while (true) {
            val b = input.read()
            if (b < 0) throw MailException("IMAP connection closed")
            if (b == '\n'.code) break
            if (b != '\r'.code) sb.write(b)
        }
        return String(sb.toByteArray(), Charsets.ISO_8859_1)
    }

    private fun quote(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    private fun imapDate(millis: Long): String =
        DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH).withZone(ZoneOffset.UTC).format(Instant.ofEpochMilli(millis))
}

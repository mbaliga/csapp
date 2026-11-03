package com.mbaliga.csapp.mail

import com.mbaliga.csapp.data.mail.ImapClient
import com.mbaliga.csapp.data.mail.MailCheckpointState
import com.mbaliga.csapp.data.mail.MailException
import java.net.ServerSocket
import kotlin.concurrent.thread
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Socket-level scripted IMAP server: LOGIN / EXAMINE / UID SEARCH / UID FETCH with literals. */
class ImapClientTest {
    private val server = ServerSocket(0)
    private val seen = java.util.Collections.synchronizedList(mutableListOf<String>())

    @After fun tearDown() = server.close()

    private fun serve(loginOk: Boolean = true, uidValidity: Int = 42, searchUids: String = "5 6", messages: Map<Int, String>) = thread(isDaemon = true) {
        server.accept().use { s ->
            val inp = s.getInputStream().bufferedReader(Charsets.ISO_8859_1)
            val out = s.getOutputStream()
            fun w(t: String) { out.write(t.toByteArray(Charsets.ISO_8859_1)); out.flush() }
            w("* OK IMAP ready\r\n")
            while (true) {
                val line = inp.readLine() ?: break
                seen += line
                val tag = line.substringBefore(' ')
                val cmd = line.substringAfter(' ')
                when {
                    cmd.startsWith("LOGIN") -> w(if (loginOk) "$tag OK logged in\r\n" else "$tag NO bad creds\r\n")
                    cmd.startsWith("EXAMINE") -> w("* 2 EXISTS\r\n* OK [UIDVALIDITY $uidValidity] ok\r\n$tag OK [READ-ONLY] done\r\n")
                    cmd.startsWith("UID SEARCH") -> w("* SEARCH $searchUids\r\n$tag OK done\r\n")
                    cmd.startsWith("UID FETCH") -> {
                        val uid = Regex("UID FETCH (\\d+)").find(cmd)!!.groupValues[1].toInt()
                        val raw = messages.getValue(uid).replace("\n", "\r\n")
                        val bytes = raw.toByteArray(Charsets.ISO_8859_1)
                        w("* 1 FETCH (UID $uid BODY[] {${bytes.size}}\r\n"); out.write(bytes); w(")\r\n$tag OK done\r\n")
                    }
                    cmd.startsWith("LOGOUT") -> { w("* BYE\r\n$tag OK bye\r\n"); break }
                    else -> w("$tag BAD unknown\r\n")
                }
            }
        }
    }

    private val m5 = "From: A <a@x.com>\nSubject: First\nMessage-ID: <5@x>\nContent-Type: text/plain\n\nbody five"
    private val m6 = "From: B <b@x.com>\nSubject: =?UTF-8?B?U2Vjb25k?=\nMessage-ID: <6@x>\nIn-Reply-To: <5@x>\nContent-Type: text/plain\n\nbody six"

    @Test fun `first run searches SINCE and fetches with BODY PEEK, checkpoint advances to highest uid`() {
        serve(messages = mapOf(5 to m5, 6 to m6))
        val r = ImapClient("127.0.0.1", server.localPort, useTls = false, now = { 1_793_700_000_000L }).fetchNew("me", "p\"w", "INBOX", null)
        assertEquals(listOf("First", "Second"), r.messages.map { it.subject })
        assertEquals(MailCheckpointState(42, 6), r.checkpoint)
        assertEquals("5@x", r.messages[1].threadId)
        assertTrue(seen.any { it.contains("UID SEARCH SINCE 27-Oct-2026") })
        assertTrue(seen.any { it.contains("BODY.PEEK[]") })
        assertTrue(seen.any { it.contains("EXAMINE \"INBOX\"") }) // read-only
        assertTrue(seen.any { it.contains("LOGIN \"me\" \"p\\\"w\"") }) // quoting
    }

    @Test fun `later run searches after last uid and ignores uids at or below it`() {
        serve(searchUids = "6", messages = mapOf(6 to m6))
        val r = ImapClient("127.0.0.1", server.localPort, useTls = false).fetchNew("me", "pw", "INBOX", MailCheckpointState(42, 6))
        assertTrue(seen.any { it.contains("UID SEARCH UID 7:*") })
        assertEquals(0, r.messages.size) // the server echoes uid 6 (IMAP quirk for n:*): must be filtered
        assertEquals(6, r.checkpoint.lastUid)
    }

    @Test fun `uidvalidity change falls back to a date search`() {
        serve(uidValidity = 99, messages = mapOf(5 to m5, 6 to m6))
        ImapClient("127.0.0.1", server.localPort, useTls = false).fetchNew("me", "pw", "INBOX", MailCheckpointState(42, 6))
        assertTrue(seen.any { it.contains("UID SEARCH SINCE") })
    }

    @Test fun `bad credentials raise a typed error`() {
        serve(loginOk = false, messages = emptyMap())
        try { ImapClient("127.0.0.1", server.localPort, useTls = false).fetchNew("me", "bad", "INBOX", null); throw AssertionError("expected") }
        catch (e: MailException) { assertTrue(e.message!!.contains("login")) }
    }
}

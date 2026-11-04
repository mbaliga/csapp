package com.mbaliga.csapp.mail

import com.mbaliga.csapp.data.mail.MailFilter
import com.mbaliga.csapp.data.mail.MimeParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MimeParserTest {
    private fun msg(s: String) = MimeParser.toMailMessage(7, s.replace("\n", "\r\n").toByteArray(Charsets.ISO_8859_1), 123L)

    @Test fun `rfc2047 words in B and Q encodings decode, adjacent words join`() {
        assertEquals("Café ünïcode", MimeParser.decodeHeader("=?UTF-8?B?Q2Fmw6k=?= =?UTF-8?Q?_=C3=BCn=C3=AFcode?="))
        assertEquals("plain", MimeParser.decodeHeader("plain"))
    }

    @Test fun `quoted-printable plain body with soft breaks and utf8`() {
        val m = msg("From: A <a@x.com>\nSubject: =?UTF-8?Q?Crash_=E2=9C=93?=\nMessage-ID: <m1@x>\nContent-Type: text/plain; charset=utf-8\nContent-Transfer-Encoding: quoted-printable\n\nHello =C3=A9 wor=\nld")
        assertEquals("Crash ✓", m.subject)
        assertEquals("Hello é world", m.bodyText)
        assertEquals("m1@x", m.messageId)
    }

    @Test fun `multipart alternative prefers text plain and records attachment names`() {
        val m = msg(
            "Subject: S\nMessage-ID: <m2@x>\nContent-Type: multipart/mixed; boundary=\"B1\"\n\n--B1\nContent-Type: multipart/alternative; boundary=\"B2\"\n\n--B2\nContent-Type: text/plain; charset=utf-8\n\nPLAIN TEXT\n--B2\nContent-Type: text/html\n\n<p>HTML TEXT</p>\n--B2--\n--B1\nContent-Type: application/pdf; name=\"log.pdf\"\nContent-Disposition: attachment; filename=\"log.pdf\"\nContent-Transfer-Encoding: base64\n\nJVBERg==\n--B1--\n",
        )
        assertEquals("PLAIN TEXT", m.bodyText)
        assertEquals(listOf("log.pdf"), m.attachmentNames)
    }

    @Test fun `html only mail is stripped to text and base64 decodes`() {
        val html = java.util.Base64.getMimeEncoder().encodeToString("<html><style>x{}</style><body><p>Hi&nbsp;there</p><br>Bye &amp; thanks</body></html>".toByteArray())
        val m = msg("Subject: S\nMessage-ID: <m3@x>\nContent-Type: text/html; charset=utf-8\nContent-Transfer-Encoding: base64\n\n$html")
        assertTrue(m.bodyText, m.bodyText.contains("Hi there") && m.bodyText.contains("Bye & thanks") && !m.bodyText.contains("x{}"))
    }

    @Test fun `threading headers and RFC 2822 date parse`() {
        val m = msg("Subject: Re: S\nMessage-ID: <c@x>\nIn-Reply-To: <b@x>\nReferences: <a@x> <b@x>\nDate: Tue, 3 Nov 2026 10:00:00 +0000 (UTC)\n\nbody")
        assertEquals("a@x", m.threadId)
        assertEquals(1793700000000L, m.dateMillis)
    }

    @Test fun `mail filters route by subject from to and body with OR`() {
        val m = msg("From: Alice <alice@x.com>\nTo: fotoz@example.com\nSubject: [Fotoz] crash\nMessage-ID: <z@x>\n\nthe app is slow")
        assertTrue(MailFilter.matches("subject contains \"[Fotoz]\"", m))
        assertTrue(MailFilter.matches("subject contains \"nope\" OR to:fotoz@example.com", m))
        assertTrue(MailFilter.matches("from:alice@x.com", m))
        assertTrue(MailFilter.matches("body contains \"SLOW\"", m))
        assertTrue(MailFilter.matches("\"[fotoz]\"", m))
        assertEquals(false, MailFilter.matches("subject contains \"other\"", m))
        assertEquals(false, MailFilter.matches("", m))
    }
}

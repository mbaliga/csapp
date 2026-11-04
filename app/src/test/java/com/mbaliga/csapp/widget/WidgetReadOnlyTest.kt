package com.mbaliga.csapp.widget

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Hard rule 1 on the home screen: a widget may open the app, never send, approve or change a reply,
 * and (house rules) never touches the network or writes data. Grep-style, like SingleSendSiteTest.
 */
class WidgetReadOnlyTest {
    private val widgetSources = File("src/main/java/com/mbaliga/csapp/widget").walkTopDown().filter { it.extension == "kt" }.toList()
    private val dao = File("src/main/java/com/mbaliga/csapp/data/db/dao/WidgetDao.kt")

    @Test fun `widget code calls no send, accept, write or sync API and opens no connection`() {
        val forbidden = listOf(
            "sendReply(", "sendConfirmedReply(", "confirmAndSend", "updateReplyDraft", "markReply", "accept(",
            "applyTriage", "updateStatus", "updateSeverity", "merge(", "split(", "dismiss(", "syncAll", "PollScheduler",
            "suggestionService", "okhttp", "OkHttp", "HttpURLConnection", "URL(", "http://", "https://", ":reply",
        )
        assertTrue(widgetSources.size >= 7)
        for (f in widgetSources) {
            val text = f.readText()
            val hits = forbidden.filter { text.contains(it) }
            assertTrue("${f.name} uses $hits", hits.isEmpty())
        }
    }

    @Test fun `the widget DAO only reads, and never selects names, drafts, sent text or metadata`() {
        val text = dao.readText()
        assertTrue(!Regex("""@(Insert|Update|Delete|Upsert|RawQuery)""").containsMatchIn(text))
        val queries = Regex("""@Query\(\s*((?:"[^"]*"\s*\+?\s*)+)\)""").findAll(text).map { m ->
            Regex(""""([^"]*)"""").findAll(m.groupValues[1]).joinToString("") { it.groupValues[1] }
        }.toList()
        assertEquals(13, queries.size)
        for (q in queries) {
            assertTrue(q, q.trimStart().startsWith("SELECT"))
            for (col in listOf("authorName", "sentReplyText", "metadataJson", "textSent")) assertTrue("$col in: $q", !q.contains(col))
            // The one payload read is an accepted triage's (for its title); never a reply draft's.
            if (q.contains("payloadJson")) assertTrue("payload outside accepted triage: $q", q.contains("kind = 'TRIAGE' AND state = 'ACCEPTED'"))
            assertTrue("draft text selected: $q", !Regex("""SELECT[^F]*replyDraft""").containsMatchIn(q))
            assertTrue("whole body selected: $q", !q.contains("SELECT *") || q.contains("FROM incidents"))
        }
    }

    @Test fun `widget snapshots stay out of cloud backup`() {
        val container = File("src/main/java/com/mbaliga/csapp/di/AppContainer.kt").readText()
        assertTrue(container.contains("""File(appContext.noBackupFilesDir, "poll_health.json")"""))
        val manifest = File("src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("""android:allowBackup="false""""))
    }
}

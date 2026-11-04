package com.mbaliga.csapp.mail

import com.mbaliga.csapp.data.db.dao.ProjectDao
import com.mbaliga.csapp.data.db.entities.*
import com.mbaliga.csapp.data.mail.*
import com.mbaliga.csapp.domain.model.INBOX_PROJECT_ID
import com.mbaliga.csapp.domain.model.SignalType
import com.mbaliga.csapp.domain.repository.SignalRepository
import com.mbaliga.csapp.fakes.FakeSignalDao
import com.mbaliga.csapp.play.MemCreds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeProjectDao : ProjectDao {
    val sources = mutableListOf<ProjectSourceEntity>()
    val boxes = mutableListOf<MailboxEntity>()
    val checkpoints = mutableMapOf<String, MailCheckpointEntity>()
    override suspend fun upsert(project: ProjectEntity) {}
    override suspend fun find(id: String): ProjectEntity? = null
    override fun observeActive(): Flow<List<ProjectEntity>> = flowOf(emptyList())
    override suspend fun findActive() = emptyList<ProjectEntity>()
    override suspend fun insertSource(source: ProjectSourceEntity): Long { sources += source.copy(id = sources.size + 1L); return sources.size.toLong() }
    override suspend fun updateSource(source: ProjectSourceEntity) {}
    override suspend fun deleteSource(id: Long) { sources.removeAll { it.id == id } }
    override suspend fun sourcesFor(projectId: String) = sources.filter { it.projectId == projectId }
    override fun observeSourcesFor(projectId: String) = flowOf(sources.filter { it.projectId == projectId })
    override suspend fun allEnabledSources() = sources.filter { it.enabled }
    override suspend fun signalCount(projectId: String) = 0
    override suspend fun upsertMailbox(mailbox: MailboxEntity) { boxes.removeAll { it.id == mailbox.id }; boxes += mailbox }
    override suspend fun deleteMailbox(id: String) { boxes.removeAll { it.id == id } }
    override fun observeMailboxes() = flowOf(boxes.toList())
    override suspend fun findMailbox(id: String) = boxes.firstOrNull { it.id == id }
    override suspend fun enabledMailboxes() = boxes.filter { it.enabled }
    override suspend fun upsertMailCheckpoint(c: MailCheckpointEntity) { checkpoints[c.key] = c }
    override suspend fun findMailCheckpoint(key: String) = checkpoints[key]
}

class MailPollRepositoryTest {
    private fun message(uid: Long, id: String, subject: String, body: String, refs: List<String> = emptyList(), irt: String? = null) =
        MailMessage(uid, id, irt, refs, "Cust <c@x.com>", listOf("support@asystemofcells.com"), subject, 1000 + uid, body, emptyList())

    @Test fun `routes by filter, unmatched to Inbox, threads replies, dedupes message ids, advances checkpoint`() = runTest {
        val dao = FakeProjectDao()
        dao.boxes += MailboxEntity("mb1", "Support", "IMAP", "h", 993, "u", "INBOX", true)
        dao.sources += ProjectSourceEntity(1, "fotoz", "MAILBOX_FILTER", "subject contains \"[Fotoz]\"", true)
        val sigDao = FakeSignalDao()
        val signals = SignalRepository(sigDao)
        val creds = MemCreds().apply { putSecret(MailPollRepository.secretName("mb1"), "pw") }
        var batch = listOf(
            message(1, "a@x", "[Fotoz] crash", "it crashes"),
            message(2, "b@x", "Hello", "unrelated question"),
            message(3, "c@x", "Re: [Fotoz] crash", "still crashing", refs = listOf("a@x"), irt = "a@x"),
            message(3, "c@x", "Re: [Fotoz] crash", "still crashing", refs = listOf("a@x"), irt = "a@x"), // duplicate delivery
        )
        val repo = MailPollRepository(dao, signals, creds, sourceFor = { MailSource { _, _, prior ->
            assertTrue(prior == null || prior.lastUid == 3L)
            MailFetchResult(batch, MailCheckpointState(7, 3))
        } }, io = Dispatchers.Unconfined)

        val failures = mutableListOf<String>()
        assertEquals(1, repo.pollAll(failures)); assertTrue(failures.isEmpty())

        val all = signals.observeAll().first().filter { it.type == SignalType.EMAIL }
        assertEquals(2, all.size) // thread a+c is ONE signal, b is another
        val fotoz = all.single { it.projectId == "fotoz" }
        assertTrue(fotoz.body.contains("it crashes") && fotoz.body.contains("still crashing"))
        assertEquals(INBOX_PROJECT_ID, all.single { it.projectId != "fotoz" }.projectId)
        assertEquals("mail:mb1:a@x", fotoz.sourceKey)
        assertEquals(3L, dao.checkpoints["mb1|INBOX"]!!.lastUid)

        // second poll re-delivering the same messages creates nothing new and does not double-append
        repo.pollAll(failures)
        assertEquals(2, signals.observeAll().first().count { it.type == SignalType.EMAIL })
        assertEquals(1, signals.observeAll().first().single { it.projectId == "fotoz" }.body.split("still crashing").size - 1)
    }

    @Test fun `missing credential is reported not thrown`() = runTest {
        val dao = FakeProjectDao().apply { boxes += MailboxEntity("mb1", "Support", "IMAP", "h", 993, "u", "INBOX", true) }
        val repo = MailPollRepository(dao, SignalRepository(FakeSignalDao()), MemCreds(), sourceFor = { MailSource { _, _, _ -> error("unused") } }, io = Dispatchers.Unconfined)
        val failures = mutableListOf<String>()
        repo.pollAll(failures)
        assertEquals(1, failures.size)
    }

    @Test fun `gmail client lists, fetches full messages and decodes base64url`() {
        val server = MockWebServer().apply { start() }
        val b64 = { s: String -> java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(s.toByteArray()) }
        server.enqueue(MockResponse().setBody("""{"messages":[{"id":"g1"}]}"""))
        server.enqueue(MockResponse().setBody("""{"id":"g1","internalDate":"1793700000000","payload":{"mimeType":"multipart/alternative","headers":[{"name":"Subject","value":"Help"},{"name":"From","value":"A <a@x.com>"},{"name":"Message-ID","value":"<g1@mail>"},{"name":"References","value":"<r0@mail>"}],"parts":[{"mimeType":"text/plain","filename":"","body":{"data":"${b64("hello gmail")}"}},{"mimeType":"application/pdf","filename":"a.pdf","body":{"attachmentId":"z"}}]}}"""))
        val client = GmailApiClient(OkHttpClient(), { "tok" }, server.url("/").toString().trimEnd('/'), now = { 1_793_700_100_000L })
        val r = client.fetchNew("in:inbox", null)
        assertEquals("hello gmail", r.messages.single().bodyText)
        assertEquals("r0@mail", r.messages.single().threadId)
        assertEquals(listOf("a.pdf"), r.messages.single().attachmentNames)
        assertEquals(1_793_700_000_000L, r.checkpoint.lastUid)
        val req = server.takeRequest()
        assertEquals("Bearer tok", req.getHeader("Authorization"))
        assertTrue(java.net.URLDecoder.decode(req.path!!, "UTF-8").contains("q=in:inbox after:"))
        server.shutdown()
    }

    @Test fun `gmail oauth pkce challenge matches RFC 7636 vector and url carries scope`() {
        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", GmailOAuth.challenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"))
        val url = GmailOAuth.authUrl("cid", "v", "st")
        assertTrue(url.contains("gmail.readonly") && url.contains("code_challenge_method=S256") && url.contains("access_type=offline"))
    }
}

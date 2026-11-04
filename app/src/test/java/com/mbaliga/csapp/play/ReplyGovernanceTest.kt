package com.mbaliga.csapp.play

import com.mbaliga.csapp.data.play.*
import com.mbaliga.csapp.domain.model.ReplyState
import com.mbaliga.csapp.domain.model.Signal
import com.mbaliga.csapp.domain.model.SignalType
import com.mbaliga.csapp.domain.repository.SignalRepository
import com.mbaliga.csapp.fakes.FakeSignalDao
import com.mbaliga.csapp.ui.reply.ReplyDiff
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ReplyGovernanceTest {
    private lateinit var server: MockWebServer
    private lateinit var gov: FakeGovernanceDao
    private lateinit var signals: SignalRepository
    private lateinit var cps: FakeCheckpointDao
    private lateinit var repo: PlayReviewRepository
    private var clock = 1_000_000L
    private val key = "play:com.foo:r1"

    @Before fun setUp() = runTest {
        server = MockWebServer().apply { start() }
        gov = FakeGovernanceDao(); signals = SignalRepository(FakeSignalDao()); cps = FakeCheckpointDao()
        val creds = MemCreds().apply { putPlayServiceAccountKeyJson("""{"client_email":"sa@x","private_key":"pem"}""") }
        val api = PlayReviewsApiClient(OkHttpClient(), FakePlayAuth(), server.url("/").toString().trimEnd('/'))
        repo = PlayReviewRepository(api, FakePlayAuth(), creds, cps, signals, gov, now = { clock }, io = Dispatchers.Unconfined)
        signals.ingestOrUpdate(Signal(sourceKey = key, type = SignalType.PLAY_REVIEW, title = "1★", body = "bad", authorName = "A", rating = 1, createdAt = 1, sourceUpdatedAt = 1, ingestedAt = 1))
    }

    @After fun tearDown() = server.shutdown()

    private suspend fun sig() = signals.observeAll().first().single { it.sourceKey == key }

    @Test fun `351 chars are rejected before any state change, network call or log`() = runTest {
        try { repo.sendConfirmedReply(key, "x".repeat(351)); throw AssertionError("expected") } catch (e: ReplyRejectedException) { }
        assertEquals(0, server.requestCount); assertEquals(0, gov.logs.size); assertEquals(ReplyState.NONE, sig().replyState)
        server.enqueue(MockResponse().setBody("{}")); repo.sendConfirmedReply(key, "x".repeat(350)); assertEquals(1, server.requestCount)
    }

    @Test fun `reply log is written exactly once per send, with outcome and http status`() = runTest {
        server.enqueue(MockResponse().setBody("{}")); repo.sendConfirmedReply(key, "Thanks")
        server.enqueue(MockResponse().setResponseCode(500))
        try { repo.sendConfirmedReply(key, "Again") } catch (e: PlayApiException) { }
        assertEquals(listOf("SENT" to 200, "FAILED" to 500), gov.logs.map { it.outcome to it.httpStatus })
        assertEquals(listOf("Thanks", "Again"), gov.logs.map { it.textSent })
    }

    @Test fun `sent text is remembered so Edit reply can diff old vs new`() = runTest {
        server.enqueue(MockResponse().setBody("{}")); repo.sendConfirmedReply(key, "Thanks for the review")
        assertEquals("Thanks for the review", sig().sentReplyText)
        val d = ReplyDiff.of("Thanks for the review", "Thanks for the feedback")
        assertEquals("Thanks for the ", d.prefix); assertEquals("review", d.removed); assertEquals("feedback", d.added)
        assertTrue(ReplyDiff.of("same", "same").unchanged)
    }

    @Test fun `quota blocks with a message, per package and method, and resets after the window`() = runTest {
        val q = QuotaGuard(gov, now = { clock }, getsPerHour = 2, postsPerDay = 1)
        q.acquireGet("a"); q.acquireGet("a"); q.acquireGet("b")
        try { q.acquireGet("a"); throw AssertionError("expected") } catch (e: QuotaExceededException) { assertTrue(e.message!!.contains("try again")) }
        q.acquirePost("a"); try { q.acquirePost("a"); throw AssertionError("expected") } catch (e: QuotaExceededException) { }
        clock += 3_600_001; q.acquireGet("a")
    }

    @Test fun `quota exhaustion blocks a reply without sending or logging`() = runTest {
        for (i in 1..1500) gov.upsertQuota(com.mbaliga.csapp.data.db.entities.QuotaEntity("post:com.foo", clock, i))
        try { repo.sendConfirmedReply(key, "hi"); throw AssertionError("expected") } catch (e: QuotaExceededException) { }
        assertEquals(0, server.requestCount); assertEquals(0, gov.logs.size)
    }

    @Test fun `poll ingests developer replies, flags edits, and skips unchanged already-seen reviews`() = runTest {
        val list = """{"reviews":[
          {"reviewId":"r9","authorName":"A","comments":[{"userComment":{"text":"bad","starRating":1,"lastModified":{"seconds":"1000"},"reviewerLanguage":"de"}},{"developerComment":{"text":"Sorry!","lastModified":{"seconds":"1100"}}}]},
          {"reviewId":"r2","comments":[{"userComment":{"text":"old","starRating":4,"lastModified":{"seconds":"500"}}}]}
        ]}"""
        cps.play = com.mbaliga.csapp.data.db.entities.PlayCheckpointEntity("com.foo", 600_000L, 0, null)
        server.enqueue(MockResponse().setBody(list))
        repo.pollRecentReviews("com.foo")
        val all = signals.observeAll().first()
        val r9 = all.single { it.sourceKey == "play:com.foo:r9" }
        assertEquals(ReplyState.SENT, r9.replyState); assertEquals("Sorry!", r9.sentReplyText); assertEquals(1_100_000L, r9.repliedAt)
        assertTrue(r9.metadataJson!!.contains("\"lang\":\"de\""))
        assertEquals(null, all.firstOrNull { it.sourceKey == "play:com.foo:r2" }) // 500s <= checkpoint 600s, no reply: skipped
        assertEquals(1_000_000L, cps.play!!.lastSeenReviewSubmitMillis)

        // review edited after the reply
        server.enqueue(MockResponse().setBody("""{"reviews":[{"reviewId":"r9","comments":[{"userComment":{"text":"worse now","starRating":1,"lastModified":{"seconds":"2000"}}},{"developerComment":{"text":"Sorry!","lastModified":{"seconds":"1100"}}}]}]}"""))
        repo.pollRecentReviews("com.foo")
        val again = signals.observeAll().first().single { it.sourceKey == "play:com.foo:r9" }
        assertTrue(again.editedAt != null && again.editedAt!! > again.repliedAt!!)
    }
}

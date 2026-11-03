package com.mbaliga.csapp.play

import com.mbaliga.csapp.data.credentials.CredentialStore
import com.mbaliga.csapp.data.db.dao.CheckpointDao
import com.mbaliga.csapp.data.db.entities.GithubCheckpointEntity
import com.mbaliga.csapp.data.db.entities.PlayCheckpointEntity
import com.mbaliga.csapp.data.play.PlayApiException
import com.mbaliga.csapp.data.play.PlayReviewRepository
import com.mbaliga.csapp.data.play.PlayReviewsApiClient
import com.mbaliga.csapp.domain.model.ReplyState
import com.mbaliga.csapp.domain.model.Signal
import com.mbaliga.csapp.domain.model.SignalType
import com.mbaliga.csapp.domain.repository.SignalRepository
import com.mbaliga.csapp.fakes.FakeSignalDao
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

class MemCreds : CredentialStore {
    val m = mutableMapOf<String, String>()
    override fun putSecret(name: String, value: String) { m[name] = value }
    override fun getSecret(name: String) = m[name]
    override fun clearSecret(name: String) { m.remove(name) }
    override fun clearAll() = m.clear()
}

class FakeGovernanceDao : com.mbaliga.csapp.data.db.dao.GovernanceDao {
    val logs = mutableListOf<com.mbaliga.csapp.data.db.entities.ReplyLogEntity>()
    val quotas = mutableMapOf<String, com.mbaliga.csapp.data.db.entities.QuotaEntity>()
    val suggestions = mutableMapOf<String, com.mbaliga.csapp.data.db.entities.SuggestionEntity>()
    override suspend fun insertReplyLog(entry: com.mbaliga.csapp.data.db.entities.ReplyLogEntity): Long { logs += entry; return logs.size.toLong() }
    override fun observeReplyLog(key: String) = kotlinx.coroutines.flow.flowOf(logs.filter { it.signalSourceKey == key })
    override suspend fun allReplyLog() = logs.toList()
    override suspend fun upsertQuota(q: com.mbaliga.csapp.data.db.entities.QuotaEntity) { quotas[q.bucketKey] = q }
    override suspend fun findQuota(key: String) = quotas[key]
    override suspend fun upsertSuggestion(s: com.mbaliga.csapp.data.db.entities.SuggestionEntity) { suggestions[s.id] = s }
    override suspend fun findSuggestion(id: String) = suggestions[id]
    override fun observeSuggestionsFor(targetId: String) = kotlinx.coroutines.flow.flowOf(suggestions.values.filter { it.targetId == targetId })
    override fun observePending() = kotlinx.coroutines.flow.flowOf(suggestions.values.filter { it.state == "PENDING" })
    override suspend fun allSuggestions() = suggestions.values.toList()
}

class FakeCheckpointDao : CheckpointDao {
    var play: PlayCheckpointEntity? = null
    var gh: GithubCheckpointEntity? = null
    override suspend fun upsertPlayCheckpoint(checkpoint: PlayCheckpointEntity) { play = checkpoint }
    override suspend fun findPlayCheckpoint(packageName: String) = play
    override suspend fun upsertGithubCheckpoint(checkpoint: GithubCheckpointEntity) { gh = checkpoint }
    override suspend fun findGithubCheckpoint(repoFullName: String) = gh
}

class PlayReviewRepositorySendTest {
    private lateinit var server: MockWebServer
    private lateinit var dao: FakeSignalDao
    private lateinit var signals: SignalRepository
    private lateinit var repo: PlayReviewRepository
    private val key = "play:com.foo:r1"

    @Before fun setUp() = runTest {
        server = MockWebServer().apply { start() }
        dao = FakeSignalDao()
        signals = SignalRepository(dao)
        val creds = MemCreds().apply {
            putPlayServiceAccountKeyJson("""{"client_email":"sa@x","private_key":"pem"}""")
        }
        val api = PlayReviewsApiClient(OkHttpClient(), FakePlayAuth(), server.url("/").toString().trimEnd('/'))
        repo = PlayReviewRepository(api, FakePlayAuth(), creds, FakeCheckpointDao(), signals, FakeGovernanceDao(), io = Dispatchers.Unconfined)
        signals.ingestOrUpdate(Signal(sourceKey = key, type = SignalType.PLAY_REVIEW, title = "1★", body = "bad", authorName = "A", rating = 1, createdAt = 1000, sourceUpdatedAt = 1000, ingestedAt = 1000))
    }

    @After fun tearDown() = server.shutdown()

    private suspend fun state() = signals.observeAll().let { f -> f.first() }.single { it.sourceKey == key }

    @Test fun `successful send marks SENT`() = runTest {
        server.enqueue(MockResponse().setBody("{}"))
        repo.sendConfirmedReply(key, "Thanks!")
        assertEquals(ReplyState.SENT, state().replyState)
    }

    @Test fun `failed send marks FAILED and rethrows`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))
        try { repo.sendConfirmedReply(key, "Thanks!"); throw AssertionError("expected") }
        catch (e: PlayApiException) { assertTrue(true) }
        assertEquals(ReplyState.FAILED, state().replyState)
    }

    @Test fun `backfill signals cannot be replied to`() = runTest {
        try { repo.sendConfirmedReply("play-backfill:com.foo:abc", "x"); throw AssertionError("expected") }
        catch (e: IllegalArgumentException) { assertEquals(0, server.requestCount) }
    }
}

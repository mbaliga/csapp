package com.asoc.ping.ai

import com.asoc.ping.domain.ai.*
import com.asoc.ping.domain.clustering.ClusteringEngine
import com.asoc.ping.domain.model.Incident
import com.asoc.ping.domain.model.IncidentStatus
import com.asoc.ping.domain.model.Severity
import com.asoc.ping.domain.model.Signal
import com.asoc.ping.domain.model.SignalType
import com.asoc.ping.domain.repository.IncidentRepository
import com.asoc.ping.domain.repository.SignalRepository
import com.asoc.ping.fakes.FakeIncidentDao
import com.asoc.ping.fakes.FakeSignalDao
import com.asoc.ping.play.FakeGovernanceDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeModel(var reply: (String, String) -> String) : ModelProvider {
    override val id = "fake"; override val displayName = "Fake"; override val model = "fake-1"
    override val provenance: Provenance = Provenance.Cloud("fake")
    val calls = mutableListOf<Pair<String, String>>()
    override suspend fun complete(system: String, user: String, maxTokens: Int, temperature: Double): Completion {
        calls += system to user; return Completion(reply(system, user), provenance, model)
    }
}

class AiTest {
    private fun sig(key: String, title: String, body: String = title) = Signal(
        sourceKey = key, type = SignalType.GITHUB_ISSUE, title = title, body = body, authorName = null, rating = null,
        createdAt = 1000, sourceUpdatedAt = 1000, ingestedAt = 1000, projectId = "p",
    )

    private class Env {
        val sd = FakeSignalDao(); val idao = FakeIncidentDao(); val gov = FakeGovernanceDao()
        val signals = SignalRepository(sd)
        val incidents = IncidentRepository(idao, sd, ClusteringEngine(now = { 5000 }), now = { 5000 })
        val model = FakeModel { _, _ -> """{"severity":"CRITICAL","title":"Launch crash","summary":"App dies at start.","reasons":["many users"]}""" }
        val svc = SuggestionService({ model }, gov, incidents, signals, now = { 9000 }, newId = { java.util.UUID.randomUUID().toString() })
    }

    @Test fun `SuggestionNeverAutoApplied - automation writes only PENDING rows and changes nothing else`() = runTest {
        val e = Env()
        e.signals.ingestOrUpdate(sig("github:a#1", "app crashes on launch")); e.signals.ingestOrUpdate(sig("github:a#2", "totally different billing question"))
        e.incidents.runClustering()
        val incBefore = e.incidents.getAll(); val sigBefore = e.signals.observeAllSnapshot()
        e.svc.runAutomation("p")
        assertTrue(e.gov.suggestions.isNotEmpty())
        assertTrue(e.gov.suggestions.values.all { it.state == "PENDING" && it.provenance.isNotBlank() && it.model.isNotBlank() })
        assertEquals(incBefore, e.incidents.getAll())
        assertEquals(sigBefore, e.signals.observeAllSnapshot())
    }

    @Test fun `accept applies triage, reject does not`() = runTest {
        val e = Env()
        e.signals.ingestOrUpdate(sig("github:a#1", "app crashes on launch")); e.incidents.runClustering()
        val inc = e.incidents.getAll().single()
        val s1 = e.svc.suggestTriage(inc)
        assertEquals(Severity.MEDIUM, e.incidents.getById(inc.id)!!.severity)
        e.svc.reject(s1.id); assertEquals(Severity.MEDIUM, e.incidents.getById(inc.id)!!.severity)
        val s2 = e.svc.suggestTriage(inc); e.svc.accept(s2.id)
        val after = e.incidents.getById(inc.id)!!
        assertEquals(Severity.CRITICAL, after.severity); assertEquals("Launch crash", after.title)
        assertEquals("ACCEPTED", e.gov.suggestions[s2.id]!!.state)
    }

    @Test fun `merge suggestions come from same-issue verdicts and apply through deterministic merge`() = runTest {
        val e = Env()
        e.model.reply = { _, _ -> """{"verdict":"same-issue","reason":"same crash"}""" }
        e.signals.ingestOrUpdate(sig("github:a#1", "crash when opening camera screen")); e.signals.ingestOrUpdate(sig("github:a#2", "camera screen crash on open"))
        // force two incidents (engine would normally group them)
        val now = 100L
        e.idao.upsert(com.asoc.ping.data.db.entities.IncidentEntity("i1", "crash opening camera screen", "camera crash", "LOW", "OPEN", false, null, null, 1, 1, "p"))
        e.idao.upsert(com.asoc.ping.data.db.entities.IncidentEntity("i2", "camera screen crashes", "crash camera", "LOW", "OPEN", false, null, null, 2, 2, "p"))
        val out = e.svc.suggestMerges("p")
        assertEquals(1, out.size)
        assertEquals(0, e.incidents.getAll().count { it.status == IncidentStatus.MERGED })
        e.svc.accept(out.single().id)
        assertEquals(1, e.incidents.getAll().count { it.status == IncidentStatus.MERGED })
    }

    @Test fun `unconfigured provider is a typed error and no rows are written`() = runTest {
        val e = Env()
        val svc = SuggestionService({ null }, e.gov, e.incidents, e.signals)
        assertEquals(false, svc.isConfigured)
        try { svc.suggestReply(sig("k", "t")); throw AssertionError("expected") } catch (x: AiException) { }
        svc.runAutomation("p")
        assertTrue(e.gov.suggestions.isEmpty())
    }

    @Test fun `draft filter enforces 350 chars, strips PII, rejects promises`() {
        assertTrue(DraftFilter.clean("Thanks! " + "word ".repeat(200)).length <= 350)
        assertEquals("Write to [contact removed] or [number removed] please", DraftFilter.clean("Write to me@x.com or +44 7700 900123 please"))
        for (bad in listOf("We will fix this by Friday", "You'll get a refund", "Fixed within 3 days")) {
            try { DraftFilter.clean(bad); throw AssertionError("should reject: $bad") } catch (r: DraftFilter.Rejected) { }
        }
    }

    @Test fun `prompts are versioned resources and carry the hard constraints`() {
        val reply = ResourcePrompts.load("reply.v1")
        assertTrue(reply.contains("350") && reply.contains("same language") && reply.contains("Do NOT promise"))
        assertTrue(ResourcePrompts.load("triage.v1").contains("CRITICAL|HIGH|MEDIUM|LOW"))
        assertTrue(ResourcePrompts.load("merge.v1").contains("same-issue|different"))
        assertTrue(ResourcePrompts.load("digest.v1").isNotBlank())
    }

    @Test fun `openai-compatible provider posts chat completions with bearer key and parses content`() = runTest {
        val server = MockWebServer().apply { start() }
        server.enqueue(MockResponse().setBody("""{"choices":[{"message":{"content":" hi there "}}]}"""))
        val cfg = ProviderConfig(ProviderPreset.DEEPSEEK, server.url("/v1").toString(), "m1")
        val p = createProvider(OkHttpClient(), cfg, { "KEY" }, Dispatchers.Unconfined)
        val c = p.complete("sys", "usr")
        assertEquals("hi there", c.text); assertEquals(Provenance.Cloud("deepseek"), c.provenance)
        val req = server.takeRequest()
        assertEquals("Bearer KEY", req.getHeader("Authorization")); assertTrue(req.path!!.endsWith("/v1/chat/completions"))
        assertTrue(req.body.readUtf8().contains("\"model\":\"m1\""))
        server.shutdown()
    }

    @Test fun `local preset is LOCAL provenance and sends no auth header without a key`() = runTest {
        val server = MockWebServer().apply { start() }
        server.enqueue(MockResponse().setBody("""{"choices":[{"message":{"content":"ok"}}]}"""))
        val p = createProvider(OkHttpClient(), ProviderConfig(ProviderPreset.ASOM, server.url("/v1").toString(), "default"), { null }, Dispatchers.Unconfined)
        assertEquals(Provenance.Local, p.complete("s", "u").provenance)
        assertEquals(null, server.takeRequest().getHeader("Authorization"))
        server.shutdown()
    }

    @Test fun `anthropic provider uses x-api-key and the messages endpoint`() = runTest {
        val server = MockWebServer().apply { start() }
        server.enqueue(MockResponse().setBody("""{"content":[{"type":"text","text":"drafted"}]}"""))
        val p = createProvider(OkHttpClient(), ProviderConfig(ProviderPreset.ANTHROPIC, server.url("/v1").toString(), "claude-haiku-5-5"), { "AK" }, Dispatchers.Unconfined)
        assertEquals("drafted", p.complete("s", "u").text)
        val req = server.takeRequest()
        assertEquals("AK", req.getHeader("x-api-key")); assertNotNull(req.getHeader("anthropic-version")); assertTrue(req.path!!.endsWith("/v1/messages"))
        server.shutdown()
    }

    @Test fun `provider http failure surfaces as AiException`() = runTest {
        val server = MockWebServer().apply { start() }
        server.enqueue(MockResponse().setResponseCode(401))
        val p = createProvider(OkHttpClient(), ProviderConfig(ProviderPreset.OPENAI, server.url("/v1").toString(), "m"), { "k" }, Dispatchers.Unconfined)
        try { p.complete("s", "u"); throw AssertionError("expected") } catch (e: AiException) { assertTrue(e.message!!.contains("401")) }
        server.shutdown()
    }
}

private suspend fun SignalRepository.observeAllSnapshot() = observeAll().first()

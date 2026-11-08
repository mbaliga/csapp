package com.asoc.ping.domain.ai

import com.asoc.ping.data.db.dao.GovernanceDao
import com.asoc.ping.data.db.entities.SuggestionEntity
import com.asoc.ping.domain.clustering.TextSimilarity
import com.asoc.ping.domain.model.Incident
import com.asoc.ping.domain.model.IncidentStatus
import com.asoc.ping.domain.model.Severity
import com.asoc.ping.domain.model.Signal
import com.asoc.ping.domain.repository.IncidentRepository
import com.asoc.ping.domain.repository.SignalRepository
import java.util.UUID
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

enum class SuggestionKind { TRIAGE, MERGE, REPLY_DRAFT, DIGEST }
enum class SuggestionState { PENDING, ACCEPTED, REJECTED }

fun interface PromptSource { fun load(name: String): String }

/** Loads versioned prompts from `src/main/resources/prompts`. */
object ResourcePrompts : PromptSource {
    override fun load(name: String): String =
        ResourcePrompts::class.java.classLoader!!.getResourceAsStream("prompts/$name.txt")!!.bufferedReader().use { it.readText() }
}

/**
 * AI *suggests*, a human *decides* (hard rule 2). Every public `suggest*`/`run*` method here only
 * WRITES `PENDING` [SuggestionEntity] rows. The only code path that changes an incident or signal
 * is [accept], which is reached only from a user's tap on Accept.
 */
class SuggestionService(
    private val provider: () -> ModelProvider?,
    private val dao: GovernanceDao,
    private val incidents: IncidentRepository,
    private val signals: SignalRepository,
    private val prompts: PromptSource = ResourcePrompts,
    private val now: () -> Long = { System.currentTimeMillis() },
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    private val json = Json { ignoreUnknownKeys = true }

    val isConfigured: Boolean get() = provider() != null

    private fun requireProvider() = provider() ?: throw AiException("No AI provider configured")

    private suspend fun store(kind: SuggestionKind, type: String, target: String, payload: JsonObject, c: Completion, version: String): SuggestionEntity {
        val e = SuggestionEntity(newId(), type, target, kind.name, payload.toString(), c.provenance.serialize(), c.model, version, now(), SuggestionState.PENDING.name)
        dao.upsertSuggestion(e)
        return e
    }

    suspend fun suggestTriage(incident: Incident): SuggestionEntity {
        val p = requireProvider()
        val members = signals.getByIncidentId(incident.id).take(8)
        val user = buildString {
            appendLine("Incident: ${incident.title}")
            members.forEach { appendLine("--- [${it.type}] ${it.title}\n${it.body.take(600)}") }
        }
        val c = p.complete(prompts.load("triage.v1"), user)
        val o = extractJson(c.text)
        val sev = o["severity"]?.jsonPrimitive?.content?.uppercase()?.takeIf { s -> Severity.values().any { it.name == s } }
            ?: throw AiException("Model returned an unusable severity")
        val payload = buildJsonObject {
            put("severity", sev)
            put("title", (o["title"]?.jsonPrimitive?.content ?: incident.title).take(80))
            put("summary", o["summary"]?.jsonPrimitive?.content.orEmpty().take(400))
            put("reasons", buildJsonArray { (o["reasons"] as? JsonArray)?.forEach { add(it) } })
        }
        return store(SuggestionKind.TRIAGE, "INCIDENT", incident.id, payload, c, "triage.v1")
    }

    /** Top-5 Jaccard neighbours per incident go to the model; "same-issue" verdicts become MERGE suggestions. */
    suspend fun suggestMerges(projectId: String, maxPairs: Int = 10): List<SuggestionEntity> {
        val p = requireProvider()
        val open = incidents.getForProject(projectId).filter { it.status != IncidentStatus.DISMISSED && it.status != IncidentStatus.MERGED }
        val tokens = open.associate { it.id to TextSimilarity.tokenize(it.title + " " + it.summary) }
        val existing = dao.allSuggestions().filter { it.kind == SuggestionKind.MERGE.name }.map { it.targetId }.toSet()
        val pairs = mutableListOf<Pair<Incident, Incident>>()
        val seen = mutableSetOf<String>()
        for (a in open.sortedBy { it.id }) {
            val neighbours = open.filter { it.id != a.id }
                .map { it to TextSimilarity.jaccard(tokens.getValue(a.id), tokens.getValue(it.id)) }
                .filter { it.second > 0.0 }.sortedByDescending { it.second }.take(5)
            for ((b, _) in neighbours) {
                val key = listOf(a.id, b.id).sorted().joinToString("+")
                if (key in seen || key in existing) continue
                seen += key; pairs += a to b
            }
        }
        val out = mutableListOf<SuggestionEntity>()
        for ((a, b) in pairs.take(maxPairs)) {
            val user = "Incident A: ${a.title}\n${a.summary}\n\nIncident B: ${b.title}\n${b.summary}"
            val c = p.complete(prompts.load("merge.v1"), user, maxTokens = 200)
            val o = runCatching { extractJson(c.text) }.getOrNull() ?: continue
            if (o["verdict"]?.jsonPrimitive?.content != "same-issue") continue
            val ids = listOf(a.id, b.id).sorted()
            val payload = buildJsonObject {
                put("incidentIds", buildJsonArray { ids.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } })
                put("reason", o["reason"]?.jsonPrimitive?.content.orEmpty())
            }
            out += store(SuggestionKind.MERGE, "INCIDENT", ids.joinToString("+"), payload, c, "merge.v1")
        }
        return out
    }

    suspend fun suggestReply(signal: Signal): SuggestionEntity {
        val p = requireProvider()
        val user = "Review (${signal.rating ?: "?"} stars):\n${signal.body.take(1200)}"
        val c = p.complete(prompts.load("reply.v1"), user, maxTokens = 300, temperature = 0.4)
        val text = DraftFilter.clean(c.text)
        return store(SuggestionKind.REPLY_DRAFT, "SIGNAL", signal.sourceKey, buildJsonObject { put("text", text) }, c, "reply.v1")
    }

    suspend fun suggestDigest(projectId: String, sinceMillis: Long): SuggestionEntity? {
        val p = requireProvider()
        val recent = incidents.getForProject(projectId).filter { it.updatedAt >= sinceMillis && it.status != IncidentStatus.MERGED }
        if (recent.isEmpty()) return null
        val user = recent.sortedByDescending { it.severity.ordinal }.take(30).joinToString("\n") { "- [${it.severity}/${it.status}] ${it.title}" }
        val c = p.complete(prompts.load("digest.v1"), user, maxTokens = 300)
        return store(SuggestionKind.DIGEST, "PROJECT", projectId, buildJsonObject { put("text", c.text.take(800)) }, c, "digest.v1")
    }

    /** Opt-in per-project automation after ingest: writes PENDING suggestions only. */
    suspend fun runAutomation(projectId: String) {
        if (!isConfigured) return
        val pendingTargets = dao.allSuggestions().filter { it.state == SuggestionState.PENDING.name && it.kind == SuggestionKind.TRIAGE.name }.map { it.targetId }.toSet()
        for (inc in incidents.getForProject(projectId).filter { it.status == IncidentStatus.OPEN && !it.isManual && it.id !in pendingTargets }.take(10)) {
            runCatching { suggestTriage(inc) }
        }
        runCatching { suggestMerges(projectId) }
    }

    /** The ONLY place a suggestion changes real data, called from an explicit user Accept. */
    suspend fun accept(id: String) {
        val s = dao.findSuggestion(id) ?: return
        if (s.state != SuggestionState.PENDING.name) return
        val o = json.parseToJsonElement(s.payloadJson).jsonObject
        when (SuggestionKind.valueOf(s.kind)) {
            SuggestionKind.TRIAGE -> {
                incidents.applyTriage(
                    s.targetId,
                    Severity.valueOf(o.getValue("severity").jsonPrimitive.content),
                    o["title"]?.jsonPrimitive?.content,
                    o["summary"]?.jsonPrimitive?.content,
                )
            }
            SuggestionKind.MERGE -> incidents.merge(o.getValue("incidentIds").jsonArray.map { it.jsonPrimitive.content })
            SuggestionKind.REPLY_DRAFT -> signals.updateReplyDraft(s.targetId, o.getValue("text").jsonPrimitive.content)
            SuggestionKind.DIGEST -> Unit
        }
        dao.upsertSuggestion(s.copy(state = SuggestionState.ACCEPTED.name))
    }

    suspend fun reject(id: String) {
        val s = dao.findSuggestion(id) ?: return
        if (s.state == SuggestionState.PENDING.name) dao.upsertSuggestion(s.copy(state = SuggestionState.REJECTED.name))
    }

    internal fun extractJson(text: String): JsonObject {
        val start = text.indexOf('{'); val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) throw AiException("Model did not return JSON")
        return try { json.parseToJsonElement(text.substring(start, end + 1)).jsonObject } catch (e: Exception) { throw AiException("Model returned malformed JSON", e) }
    }
}

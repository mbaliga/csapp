package com.mbaliga.csapp.domain.ai

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

private val JSON = "application/json".toMediaType()

abstract class BaseProvider(
    protected val http: OkHttpClient,
    protected val config: ProviderConfig,
    private val io: CoroutineDispatcher,
) : ModelProvider {
    override val model get() = config.model
    override val displayName get() = config.preset.label
    override val id get() = config.preset.name.lowercase()
    override val provenance: Provenance get() = if (config.preset.local) Provenance.Local else Provenance.Cloud(id)
    protected val host: String = config.baseUrl.toHttpUrl().host

    /** The credential is sent only to the provider's own host. */
    protected fun checked(url: String): String {
        require(url.toHttpUrl().host == host) { "Refusing request to a host other than $host" }
        return url
    }

    protected suspend fun call(req: Request): String = withContext(io) {
        try {
            http.newCall(req).execute().use { r ->
                val body = r.body?.string().orEmpty()
                if (!r.isSuccessful) throw AiException("${displayName} returned HTTP ${r.code}")
                body
            }
        } catch (e: AiException) { throw e } catch (e: Exception) { throw AiException("${displayName} unreachable: ${e.message}", e) }
    }
}

/** OpenAI-compatible `/chat/completions` (ASOM, DeepSeek, Grok, OpenAI, Gemini-compat, Llama servers). */
class OpenAiCompatProvider(
    http: OkHttpClient, config: ProviderConfig, private val apiKey: () -> String?, io: CoroutineDispatcher = Dispatchers.IO,
) : ModelProvider, BaseProvider(http, config, io) {
    override suspend fun complete(system: String, user: String, maxTokens: Int, temperature: Double): Completion {
        val body = buildJsonObject {
            put("model", config.model)
            put("temperature", temperature)
            put("max_tokens", maxTokens)
            put("messages", buildJsonArray {
                add(buildJsonObject { put("role", "system"); put("content", system) })
                add(buildJsonObject { put("role", "user"); put("content", user) })
            })
        }.toString()
        val rb = Request.Builder().url(checked(config.baseUrl.trimEnd('/') + "/chat/completions")).post(body.toRequestBody(JSON))
        apiKey()?.takeIf { it.isNotBlank() }?.let { rb.header("Authorization", "Bearer $it") }
        val text = Json.parseToJsonElement(call(rb.build())).jsonObject["choices"]?.jsonArray?.firstOrNull()
            ?.jsonObject?.get("message")?.jsonObject?.get("content")?.jsonPrimitive?.content
            ?: throw AiException("${displayName} returned no content")
        return Completion(text.trim(), provenance, config.model)
    }
}

/** Anthropic Messages API. */
class AnthropicProvider(
    http: OkHttpClient, config: ProviderConfig, private val apiKey: () -> String?, io: CoroutineDispatcher = Dispatchers.IO,
) : ModelProvider, BaseProvider(http, config, io) {
    override suspend fun complete(system: String, user: String, maxTokens: Int, temperature: Double): Completion {
        val key = apiKey()?.takeIf { it.isNotBlank() } ?: throw AiException("Anthropic API key not set")
        val body = buildJsonObject {
            put("model", config.model)
            put("max_tokens", maxTokens)
            put("temperature", temperature)
            put("system", system)
            put("messages", buildJsonArray { add(buildJsonObject { put("role", "user"); put("content", user) }) })
        }.toString()
        val req = Request.Builder().url(checked(config.baseUrl.trimEnd('/') + "/messages"))
            .header("x-api-key", key).header("anthropic-version", "2023-06-01")
            .post(body.toRequestBody(JSON)).build()
        val content: JsonArray = Json.parseToJsonElement(call(req)).jsonObject["content"]?.jsonArray
            ?: throw AiException("Anthropic returned no content")
        val text = content.joinToString("") { (it as? JsonObject)?.get("text")?.jsonPrimitive?.content.orEmpty() }
        return Completion(text.trim(), provenance, config.model)
    }
}

fun createProvider(http: OkHttpClient, config: ProviderConfig, apiKey: () -> String?, io: CoroutineDispatcher = Dispatchers.IO): ModelProvider =
    if (config.preset.anthropic) AnthropicProvider(http, config, apiKey, io) else OpenAiCompatProvider(http, config, apiKey, io)

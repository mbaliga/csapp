package com.asoc.ping.domain.ai

/** Where a completion came from; always shown next to a suggestion. */
sealed class Provenance {
    data object Local : Provenance()
    data class Cloud(val providerId: String) : Provenance()
    fun serialize(): String = when (this) { Local -> "LOCAL"; is Cloud -> "CLOUD:$providerId" }
    companion object {
        fun parse(s: String): Provenance = if (s.startsWith("CLOUD:")) Cloud(s.removePrefix("CLOUD:")) else Local
    }
}

data class Completion(val text: String, val provenance: Provenance, val model: String)

class AiException(message: String, cause: Throwable? = null) : Exception(message, cause)

interface ModelProvider {
    val id: String
    val displayName: String
    val provenance: Provenance
    val model: String
    suspend fun complete(system: String, user: String, maxTokens: Int = 600, temperature: Double = 0.2): Completion
}

enum class ProviderPreset(
    val label: String, val baseUrl: String, val defaultModel: String, val local: Boolean, val anthropic: Boolean = false,
) {
    ASOM("ASOM (on-device)", "http://127.0.0.1:11435/v1", "default", true),
    LOCAL_SERVER("Local server (Llama etc.)", "http://127.0.0.1:11434/v1", "llama3.2", true),
    DEEPSEEK("DeepSeek", "https://api.deepseek.com/v1", "deepseek-chat", false),
    GROK("Grok (xAI)", "https://api.x.ai/v1", "grok-3-mini", false),
    OPENAI("OpenAI", "https://api.openai.com/v1", "gpt-4o-mini", false),
    GEMINI("Gemini (OpenAI-compatible)", "https://generativelanguage.googleapis.com/v1beta/openai", "gemini-2.0-flash", false),
    ANTHROPIC("Anthropic", "https://api.anthropic.com/v1", "claude-haiku-5-5", false, anthropic = true),
}

data class ProviderConfig(val preset: ProviderPreset, val baseUrl: String, val model: String)

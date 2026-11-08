package com.asoc.ping.data.mail

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/** PKCE helpers + token exchange for the on-device Gmail OAuth flow (scope gmail.readonly). */
object GmailOAuth {
    const val SCOPE = "https://www.googleapis.com/auth/gmail.readonly"
    const val REDIRECT_URI = "com.asoc.ping:/oauth2redirect"
    private const val AUTH = "https://accounts.google.com/o/oauth2/v2/auth"
    private const val TOKEN = "https://oauth2.googleapis.com/token"

    fun newVerifier(): String {
        val b = ByteArray(48); SecureRandom().nextBytes(b)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b)
    }

    fun challenge(verifier: String): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()))

    fun authUrl(clientId: String, verifier: String, state: String): String =
        AUTH.toHttpUrl().newBuilder()
            .addQueryParameter("client_id", clientId)
            .addQueryParameter("redirect_uri", REDIRECT_URI)
            .addQueryParameter("response_type", "code")
            .addQueryParameter("scope", SCOPE)
            .addQueryParameter("code_challenge", challenge(verifier))
            .addQueryParameter("code_challenge_method", "S256")
            .addQueryParameter("access_type", "offline")
            .addQueryParameter("prompt", "consent")
            .addQueryParameter("state", state)
            .build().toString()

    /** Exchanges the auth code for a refresh token. */
    fun exchangeCode(http: OkHttpClient, clientId: String, code: String, verifier: String, tokenUrl: String = TOKEN): String {
        val body = FormBody.Builder().add("client_id", clientId).add("code", code).add("code_verifier", verifier)
            .add("grant_type", "authorization_code").add("redirect_uri", REDIRECT_URI).build()
        http.newCall(Request.Builder().url(tokenUrl).post(body).build()).execute().use { r ->
            if (!r.isSuccessful) throw MailException("Gmail token exchange failed: HTTP ${r.code}")
            val o = Json.parseToJsonElement(r.body?.string().orEmpty()).jsonObject
            return o["refresh_token"]?.jsonPrimitive?.content ?: throw MailException("Gmail returned no refresh token")
        }
    }

    fun refreshAccessToken(http: OkHttpClient, clientId: String, refreshToken: String, tokenUrl: String = TOKEN): String {
        val body = FormBody.Builder().add("client_id", clientId).add("refresh_token", refreshToken)
            .add("grant_type", "refresh_token").build()
        http.newCall(Request.Builder().url(tokenUrl).post(body).build()).execute().use { r ->
            if (!r.isSuccessful) throw MailException("Gmail token refresh failed: HTTP ${r.code}")
            val o = Json.parseToJsonElement(r.body?.string().orEmpty()).jsonObject
            return o["access_token"]?.jsonPrimitive?.content ?: throw MailException("Gmail returned no access token")
        }
    }
}

/** Gmail REST (users.messages.list / get?format=full) over plain OkHttp. Read-only. */
class GmailApiClient(
    private val http: OkHttpClient,
    private val accessToken: () -> String,
    private val baseUrl: String = "https://gmail.googleapis.com/gmail/v1",
    private val now: () -> Long = { System.currentTimeMillis() },
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun fetchNew(query: String, checkpoint: MailCheckpointState?, maxMessages: Int = 100): MailFetchResult {
        val floor = checkpoint?.lastUid ?: 0L // Gmail: lastUid holds internalDate (ms) of newest message seen
        val afterSecs = if (floor > 0) floor / 1000 else (now() - 7L * 24 * 3600 * 1000) / 1000
        val q = "$query after:$afterSecs".trim()
        val ids = mutableListOf<String>()
        var page: String? = null
        do {
            val url = "$baseUrl/users/me/messages".toHttpUrl().newBuilder().addQueryParameter("q", q).addQueryParameter("maxResults", "100")
            page?.let { url.addQueryParameter("pageToken", it) }
            val o = get(url.build().toString())
            (o["messages"] as? JsonArray)?.forEach { ids += it.jsonObject["id"]!!.jsonPrimitive.content }
            page = o["nextPageToken"]?.jsonPrimitive?.content
        } while (page != null && ids.size < maxMessages)

        val messages = mutableListOf<MailMessage>()
        var newest = floor
        for (id in ids.take(maxMessages)) {
            val m = get("$baseUrl/users/me/messages/$id?format=full")
            val internal = m["internalDate"]?.jsonPrimitive?.content?.toLongOrNull() ?: now()
            if (internal <= floor) continue
            messages += toMailMessage(m, internal)
            newest = maxOf(newest, internal)
        }
        return MailFetchResult(messages.sortedBy { it.dateMillis }, MailCheckpointState(0, newest))
    }

    private fun get(url: String): JsonObject {
        val req = Request.Builder().url(url).header("Authorization", "Bearer ${accessToken()}").get().build()
        http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw MailException("Gmail API failed: HTTP ${r.code}")
            return json.parseToJsonElement(r.body?.string().orEmpty()).jsonObject
        }
    }

    internal fun toMailMessage(m: JsonObject, internal: Long): MailMessage {
        val payload = m["payload"]!!.jsonObject
        val headers = (payload["headers"] as? JsonArray).orEmpty().associate {
            it.jsonObject["name"]!!.jsonPrimitive.content.lowercase() to it.jsonObject["value"]!!.jsonPrimitive.content
        }
        var plain: String? = null
        var html: String? = null
        val attachments = mutableListOf<String>()
        fun walk(p: JsonObject) {
            val mime = p["mimeType"]?.jsonPrimitive?.content?.lowercase().orEmpty()
            val filename = p["filename"]?.jsonPrimitive?.content.orEmpty()
            val data = p["body"]?.jsonObject?.get("data")?.jsonPrimitive?.content
            if (filename.isNotEmpty()) attachments += filename
            else if (data != null && mime == "text/plain" && plain == null) plain = String(Base64.getUrlDecoder().decode(data), Charsets.UTF_8)
            else if (data != null && mime == "text/html" && html == null) html = String(Base64.getUrlDecoder().decode(data), Charsets.UTF_8)
            (p["parts"] as? JsonArray)?.forEach { walk(it.jsonObject) }
        }
        walk(payload)
        fun ids(v: String?) = Regex("<([^<>\\s]+)>").findAll(v.orEmpty()).map { it.groupValues[1] }.toList()
        val mid = ids(headers["message-id"]).firstOrNull() ?: "gmail-${m["id"]!!.jsonPrimitive.content}"
        return MailMessage(
            uid = internal,
            messageId = mid,
            inReplyTo = ids(headers["in-reply-to"]).firstOrNull(),
            references = ids(headers["references"]),
            from = MimeParser.decodeHeader(headers["from"].orEmpty()),
            to = headers["to"].orEmpty().split(",").map { it.trim() }.filter { it.isNotEmpty() },
            subject = MimeParser.decodeHeader(headers["subject"].orEmpty()),
            dateMillis = internal,
            bodyText = plain?.trim() ?: html?.let { MimeParser.htmlToText(it) } ?: "",
            attachmentNames = attachments,
        )
    }
}

private fun JsonArray?.orEmpty(): JsonArray = this ?: JsonArray(emptyList())

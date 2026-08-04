package com.mbaliga.csapp.data.play

import android.util.Base64
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.util.concurrent.TimeUnit
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request

/** Shape of a Google Cloud "service account" JSON key file, as downloaded from the Play Console. */
@Serializable
data class PlayServiceAccountKey(
    @kotlinx.serialization.SerialName("client_email") val clientEmail: String,
    @kotlinx.serialization.SerialName("private_key") val privateKeyPem: String,
    @kotlinx.serialization.SerialName("token_uri") val tokenUri: String = "https://oauth2.googleapis.com/token",
)

@Serializable
private data class TokenResponse(
    @kotlinx.serialization.SerialName("access_token") val accessToken: String,
    @kotlinx.serialization.SerialName("expires_in") val expiresInSeconds: Long,
)

/**
 * Signs a Google service-account JWT assertion and exchanges it for a short-lived OAuth2 access
 * token, per the standard [Google service-account server-to-server
 * flow](https://developers.google.com/identity/protocols/oauth2/service-account). This is a
 * private/dogfood-style credential exchange (no user consent screen, no public-distribution
 * OAuth flow), matching the "private/dogfood service-account style auth only" V1 scope.
 */
class PlayServiceAccountAuth(
    private val httpClient: OkHttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    private val jsonCodec = Json { ignoreUnknownKeys = true }
    private var cachedToken: String? = null
    private var cachedTokenExpiryMillis: Long = 0

    fun parseKey(rawJson: String): PlayServiceAccountKey = jsonCodec.decodeFromString(rawJson)

    /** Returns a valid bearer access token, refreshing it if the cached one has expired. */
    fun getAccessToken(key: PlayServiceAccountKey, scope: String = ANDROID_PUBLISHER_SCOPE, nowMillis: Long = System.currentTimeMillis()): String {
        cachedToken?.let { if (nowMillis < cachedTokenExpiryMillis - EXPIRY_SAFETY_MARGIN_MILLIS) return it }

        val jwt = buildSignedJwt(key, scope, nowMillis)
        val body = FormBody.Builder()
            .add("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer")
            .add("assertion", jwt)
            .build()
        val request = Request.Builder().url(key.tokenUri).post(body).build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw PlayAuthException("Token exchange failed: HTTP ${response.code}")
            }
            val tokenResponse = json.decodeFromString<TokenResponse>(response.body?.string().orEmpty())
            cachedToken = tokenResponse.accessToken
            cachedTokenExpiryMillis = nowMillis + TimeUnit.SECONDS.toMillis(tokenResponse.expiresInSeconds)
            return tokenResponse.accessToken
        }
    }

    private fun buildSignedJwt(key: PlayServiceAccountKey, scope: String, nowMillis: Long): String {
        val header = """{"alg":"RS256","typ":"JWT"}"""
        val issuedAtSeconds = nowMillis / 1000
        val expirySeconds = issuedAtSeconds + TimeUnit.HOURS.toSeconds(1)
        val claims = """{"iss":"${key.clientEmail}","scope":"$scope","aud":"${key.tokenUri}","iat":$issuedAtSeconds,"exp":$expirySeconds}"""

        val encodedHeader = base64UrlEncode(header.toByteArray(Charsets.UTF_8))
        val encodedClaims = base64UrlEncode(claims.toByteArray(Charsets.UTF_8))
        val signingInput = "$encodedHeader.$encodedClaims"

        val privateKey = loadPrivateKey(key.privateKeyPem)
        val signature = Signature.getInstance("SHA256withRSA").apply {
            initSign(privateKey)
            update(signingInput.toByteArray(Charsets.UTF_8))
        }.sign()

        return "$signingInput.${base64UrlEncode(signature)}"
    }

    private fun loadPrivateKey(pem: String): java.security.PrivateKey {
        val cleaned = pem
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replace("\\n", "")
            .replace("\n", "")
            .trim()
        val keyBytes = Base64.decode(cleaned, Base64.DEFAULT)
        return KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(keyBytes))
    }

    private fun base64UrlEncode(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

    companion object {
        const val ANDROID_PUBLISHER_SCOPE = "https://www.googleapis.com/auth/androidpublisher"
        private const val EXPIRY_SAFETY_MARGIN_MILLIS = 60_000L
    }
}

class PlayAuthException(message: String) : Exception(message)

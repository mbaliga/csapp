package com.mbaliga.csapp.data.play

import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Thin client for the Play Developer API's recent-reviews endpoint
 * (`androidpublisher/v3/applications/{packageName}/reviews`).
 *
 * This is the "recent reviews" polling surface only (last ~1 week, near-real-time). Historical
 * backfill beyond that window comes from the monthly UTF-16 review-report export, parsed by
 * [PlayMonthlyReportParser] - the Play Developer API does not expose full review history.
 */
class PlayReviewsApiClient(
    private val httpClient: OkHttpClient,
    private val auth: PlayServiceAccountAuth,
    private val baseUrl: String = "https://androidpublisher.googleapis.com/androidpublisher/v3",
) {
    private val json = Json { ignoreUnknownKeys = true }

    /** Fetches all pages of recent reviews for [packageName], newest data as reported by Google. */
    fun fetchRecentReviews(packageName: String, key: PlayServiceAccountKey): List<PlayReviewDto> {
        val accessToken = auth.getAccessToken(key)
        val allReviews = mutableListOf<PlayReviewDto>()
        var pageToken: String? = null

        do {
            val urlBuilder = "$baseUrl/applications/$packageName/reviews".toHttpUrl().newBuilder()
                .addQueryParameter("maxResults", "100")
            pageToken?.let { urlBuilder.addQueryParameter("token", it) }

            val request = Request.Builder()
                .url(urlBuilder.build())
                .header("Authorization", "Bearer $accessToken")
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw PlayApiException("Play reviews.list failed: HTTP ${response.code} for $packageName")
                }
                val parsed = json.decodeFromString<PlayReviewsListResponse>(response.body?.string().orEmpty())
                allReviews.addAll(parsed.reviews)
                pageToken = parsed.tokenPagination?.nextPageToken
            }
        } while (!pageToken.isNullOrBlank())

        return allReviews
    }

    /**
     * Sends a developer reply to a single review. Callers must only invoke this after an explicit
     * human confirmation of the exact reply text - this client performs no automatic replying or
     * retry-with-different-text logic.
     */
    fun sendReply(packageName: String, reviewId: String, replyText: String, key: PlayServiceAccountKey) {
        val accessToken = auth.getAccessToken(key)
        val url = "$baseUrl/applications/$packageName/reviews/$reviewId:reply"
        val bodyJson = json.encodeToString(PlayReplyRequestDto.serializer(), PlayReplyRequestDto(replyText))
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $accessToken")
            .post(bodyJson.toRequestBody("application/json".toMediaType()))
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw PlayApiException("Play reviews.reply failed: HTTP ${response.code} for review $reviewId")
            }
        }
    }
}

class PlayApiException(message: String) : Exception(message)

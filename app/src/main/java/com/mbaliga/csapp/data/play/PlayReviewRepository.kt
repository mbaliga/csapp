package com.mbaliga.csapp.data.play

import com.mbaliga.csapp.data.credentials.CredentialStore
import com.mbaliga.csapp.data.db.dao.CheckpointDao
import com.mbaliga.csapp.data.db.entities.PlayCheckpointEntity
import com.mbaliga.csapp.domain.model.Signal
import com.mbaliga.csapp.domain.model.SignalType
import com.mbaliga.csapp.domain.repository.SignalRepository
import java.io.InputStream
import java.security.MessageDigest

/**
 * Orchestrates Play review ingestion: near-real-time polling via [PlayReviewsApiClient], plus
 * one-off historical backfill from a monthly report file parsed by [PlayMonthlyReportParser].
 * Both paths write through the same [SignalRepository] ingestion path, so clustering and export
 * see a unified signal stream regardless of origin.
 */
class PlayReviewRepository(
    private val apiClient: PlayReviewsApiClient,
    private val auth: PlayServiceAccountAuth,
    private val credentialStore: CredentialStore,
    private val checkpointDao: CheckpointDao,
    private val signalRepository: SignalRepository,
    private val now: () -> Long = { System.currentTimeMillis() },
) {
    /** Polls the recent-reviews API (last ~1 week) for [packageName]. */
    suspend fun pollRecentReviews(packageName: String) {
        val keyJson = credentialStore.getPlayServiceAccountKeyJson()
            ?: throw IllegalStateException("No Play service-account key configured")
        val key = auth.parseKey(keyJson)

        val reviews = apiClient.fetchRecentReviews(packageName, key)
        for (review in reviews) {
            val userComment = review.comments.firstNotNullOfOrNull { it.userComment } ?: continue
            val signal = review.toSignal(packageName, userComment)
            signalRepository.ingestOrUpdate(signal)
        }

        checkpointDao.upsertPlayCheckpoint(
            PlayCheckpointEntity(
                packageName = packageName,
                lastSeenReviewSubmitMillis = reviews.mapNotNull {
                    it.comments.firstNotNullOfOrNull { c -> c.userComment?.lastModified?.toEpochMillis() }
                }.maxOrNull() ?: (checkpointDao.findPlayCheckpoint(packageName)?.lastSeenReviewSubmitMillis ?: 0L),
                lastPollAt = now(),
                lastBackfillMonth = checkpointDao.findPlayCheckpoint(packageName)?.lastBackfillMonth,
            )
        )
    }

    /**
     * Backfills historical reviews from a Play Console monthly report export (UTF-16 CSV). The
     * report format does not carry the API's `reviewId`, so backfilled signals get a stable
     * synthetic sourceKey derived from (package, submit time, review text hash); such signals
     * cannot be replied to via the API (no reviewId), only viewed/clustered - see
     * [Signal.sourceKey] prefix `play-backfill:`.
     */
    suspend fun backfillFromMonthlyReport(input: InputStream, reportMonth: String) {
        val rows = PlayMonthlyReportParser.parse(input)
        for (row in rows) {
            val signal = row.toSignal()
            signalRepository.ingestOrUpdate(signal)
        }
        val packageName = rows.firstOrNull()?.packageName ?: return
        val existing = checkpointDao.findPlayCheckpoint(packageName)
        checkpointDao.upsertPlayCheckpoint(
            PlayCheckpointEntity(
                packageName = packageName,
                lastSeenReviewSubmitMillis = existing?.lastSeenReviewSubmitMillis ?: 0L,
                lastPollAt = existing?.lastPollAt ?: now(),
                lastBackfillMonth = reportMonth,
            )
        )
    }

    /**
     * Sends a developer reply to a Play review. Must only be called after the human has
     * confirmed the exact reply text in the UI (see [Signal.replyState] transitions); this
     * function performs no retries and never runs on its own.
     *
     * @throws IllegalArgumentException if [sourceKey] is a backfill-sourced signal, which has no
     *   real `reviewId` to reply to (see [backfillFromMonthlyReport]).
     */
    suspend fun sendConfirmedReply(sourceKey: String, replyText: String) {
        val reviewId = extractLiveReviewId(sourceKey)
            ?: throw IllegalArgumentException("Cannot reply to a backfill-sourced or malformed signal: $sourceKey")
        val packageName = extractPackageName(sourceKey)
        val keyJson = credentialStore.getPlayServiceAccountKeyJson()
            ?: throw IllegalStateException("No Play service-account key configured")
        val key = auth.parseKey(keyJson)

        signalRepository.markReplyConfirmedSending(sourceKey)
        try {
            apiClient.sendReply(packageName, reviewId, replyText, key)
            signalRepository.markReplySent(sourceKey, now())
        } catch (e: Exception) {
            signalRepository.markReplyFailed(sourceKey)
            throw e
        }
    }

    private fun extractLiveReviewId(sourceKey: String): String? {
        if (!sourceKey.startsWith("play:")) return null
        val parts = sourceKey.split(":")
        return if (parts.size >= 3) parts.drop(2).joinToString(":") else null
    }

    private fun extractPackageName(sourceKey: String): String = sourceKey.split(":").getOrElse(1) { "" }

    private fun PlayReviewDto.toSignal(packageName: String, userComment: PlayUserCommentDto): Signal {
        val submitMillis = userComment.lastModified?.toEpochMillis() ?: now()
        return Signal(
            sourceKey = "play:$packageName:$reviewId",
            type = SignalType.PLAY_REVIEW,
            title = "${userComment.starRating ?: "?"}★ review",
            body = userComment.text,
            authorName = authorName,
            rating = userComment.starRating,
            createdAt = submitMillis,
            sourceUpdatedAt = submitMillis,
            ingestedAt = now(),
            metadataJson = """{"device":"${userComment.device.orEmpty()}","appVersionName":"${userComment.appVersionName.orEmpty()}"}""",
        )
    }

    private fun PlayMonthlyReportRow.toSignal(): Signal {
        val submitMillis = submitMillisSinceEpoch ?: 0L
        val stableSuffix = sha256("$packageName|$submitMillis|$reviewText").take(16)
        return Signal(
            sourceKey = "play-backfill:$packageName:$stableSuffix",
            type = SignalType.PLAY_REVIEW,
            title = reviewTitle ?: "${starRating ?: "?"}★ review",
            body = reviewText,
            authorName = null,
            rating = starRating,
            createdAt = submitMillis,
            sourceUpdatedAt = lastUpdateMillisSinceEpoch ?: submitMillis,
            ingestedAt = now(),
            metadataJson = """{"device":"${device.orEmpty()}","appVersionName":"${appVersionName.orEmpty()}","source":"monthly-backfill"}""",
        )
    }

    private fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}

package com.mbaliga.csapp.data.play

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Mapping of the Play Developer API `reviews.list` response (androidpublisher v3). */
@Serializable
data class PlayReviewsListResponse(
    val reviews: List<PlayReviewDto> = emptyList(),
    val tokenPagination: PlayTokenPaginationDto? = null,
)

@Serializable
data class PlayTokenPaginationDto(
    val nextPageToken: String? = null,
)

@Serializable
data class PlayReviewDto(
    val reviewId: String,
    val authorName: String? = null,
    val comments: List<PlayCommentDto> = emptyList(),
)

@Serializable
data class PlayCommentDto(
    val userComment: PlayUserCommentDto? = null,
    val developerComment: PlayDeveloperCommentDto? = null,
)

@Serializable
data class PlayUserCommentDto(
    val text: String = "",
    val lastModified: PlayTimestampDto? = null,
    val starRating: Int? = null,
    val reviewerLanguage: String? = null,
    val device: String? = null,
    val androidOsVersion: Int? = null,
    val appVersionCode: Int? = null,
    val appVersionName: String? = null,
)

@Serializable
data class PlayDeveloperCommentDto(
    val text: String = "",
    val lastModified: PlayTimestampDto? = null,
)

@Serializable
data class PlayTimestampDto(
    val seconds: Long = 0,
    val nanos: Long = 0,
) {
    fun toEpochMillis(): Long = seconds * 1000 + nanos / 1_000_000
}

@Serializable
data class PlayReplyRequestDto(
    val replyText: String,
)

@Serializable
data class PlayReplyResponseDto(
    val result: PlayDeveloperCommentDto? = null,
)

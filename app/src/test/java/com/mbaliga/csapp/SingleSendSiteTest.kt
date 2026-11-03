package com.mbaliga.csapp

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

/** Hard rule 1: exactly one call site may send a reply to a user, and only after confirmation. */
class SingleSendSiteTest {
    private val main = File("src/main/java")

    private fun kt() = main.walkTopDown().filter { it.extension == "kt" }.toList()

    @Test fun `sendReply is defined once and called only from PlayReviewRepository`() {
        val hits = kt().filter { Regex("""\bsendReply\(""").containsMatchIn(it.readText()) }.map { it.name }.sorted()
        assertEquals(listOf("PlayReviewRepository.kt", "PlayReviewsApiClient.kt"), hits)
    }

    @Test fun `reply endpoint string appears only in the API client`() {
        val hits = kt().filter { it.readText().contains(":reply") }.map { it.name }
        assertEquals(listOf("PlayReviewsApiClient.kt"), hits)
    }

    @Test fun `sendConfirmedReply is invoked only from ReplyViewModel`() {
        val hits = kt().filter { it.readText().contains("sendConfirmedReply(") }.map { it.name }.sorted()
        assertEquals(listOf("PlayReviewRepository.kt", "ReplyViewModel.kt"), hits)
    }
}

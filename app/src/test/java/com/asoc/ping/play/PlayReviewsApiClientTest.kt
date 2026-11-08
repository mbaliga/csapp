package com.asoc.ping.play

import com.asoc.ping.data.play.PlayApiException
import com.asoc.ping.data.play.PlayReviewsApiClient
import com.asoc.ping.data.play.PlayServiceAccountAuth
import com.asoc.ping.data.play.PlayServiceAccountKey
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Auth stub: no RSA / android.util.Base64 in plain JVM tests. */
class FakePlayAuth : PlayServiceAccountAuth(OkHttpClient()) {
    override fun getAccessToken(key: PlayServiceAccountKey, scope: String, nowMillis: Long) = "tok"
}

val FAKE_KEY = PlayServiceAccountKey("sa@example.iam.gserviceaccount.com", "pem")

class PlayReviewsApiClientTest {
    private lateinit var server: MockWebServer
    private lateinit var client: PlayReviewsApiClient

    @Before fun setUp() {
        server = MockWebServer().apply { start() }
        client = PlayReviewsApiClient(OkHttpClient(), FakePlayAuth(), server.url("/").toString().trimEnd('/'))
    }

    @After fun tearDown() = server.shutdown()

    @Test fun `list paginates and parses string-encoded timestamps`() {
        server.enqueue(MockResponse().setBody("""{"reviews":[{"reviewId":"r1","authorName":"A","comments":[{"userComment":{"text":"crash","starRating":1,"lastModified":{"seconds":"1700000000","nanos":5000000}}}]}],"tokenPagination":{"nextPageToken":"p2"}}"""))
        server.enqueue(MockResponse().setBody("""{"reviews":[{"reviewId":"r2","comments":[{"userComment":{"text":"ok","starRating":5,"lastModified":{"seconds":"1700000100"}}}]}]}"""))
        val reviews = client.fetchRecentReviews("com.foo", FAKE_KEY)
        assertEquals(listOf("r1", "r2"), reviews.map { it.reviewId })
        assertEquals(1_700_000_000_005L, reviews[0].comments[0].userComment!!.lastModified!!.toEpochMillis())
        val first = server.takeRequest()
        assertEquals("Bearer tok", first.getHeader("Authorization"))
        assertTrue(first.path!!.contains("/applications/com.foo/reviews"))
        assertTrue(server.takeRequest().path!!.contains("token=p2"))
    }

    @Test fun `list failure throws typed error`() {
        server.enqueue(MockResponse().setResponseCode(403))
        try { client.fetchRecentReviews("com.foo", FAKE_KEY); throw AssertionError("expected") }
        catch (e: PlayApiException) { assertTrue(e.message!!.contains("403")) }
    }

    @Test fun `reply POSTs exact text to the reply endpoint`() {
        server.enqueue(MockResponse().setBody("{}"))
        client.sendReply("com.foo", "r1", "Thanks \"quoted\"", FAKE_KEY)
        val req = server.takeRequest()
        assertEquals("POST", req.method)
        assertTrue(req.path!!.endsWith("/applications/com.foo/reviews/r1:reply"))
        assertEquals("""{"replyText":"Thanks \"quoted\""}""", req.body.readUtf8())
    }

    @Test fun `reply failure throws`() {
        server.enqueue(MockResponse().setResponseCode(500))
        try { client.sendReply("com.foo", "r1", "x", FAKE_KEY); throw AssertionError("expected") }
        catch (e: PlayApiException) { assertTrue(e.message!!.contains("500")) }
    }
}

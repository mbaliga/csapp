package com.mbaliga.csapp.github

import com.mbaliga.csapp.data.github.GithubPollCheckpoint
import com.mbaliga.csapp.data.github.GitHubIssuePollingClient
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GitHubIssuePollingClientTest {

    private lateinit var server: MockWebServer
    private lateinit var client: GitHubIssuePollingClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = GitHubIssuePollingClient(OkHttpClient(), tokenProvider = { "test-token" }, baseUrl = server.url("/").toString().trimEnd('/'))
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `pull requests are filtered out of the results`() {
        val body = """
            [
              {"id":1,"number":10,"title":"Real issue","body":"b","state":"open","html_url":"h","created_at":"2024-01-01T00:00:00Z","updated_at":"2024-01-01T00:00:00Z"},
              {"id":2,"number":11,"title":"A PR","body":"b","state":"open","html_url":"h","created_at":"2024-01-01T00:00:00Z","updated_at":"2024-01-01T00:00:00Z","pull_request":{"url":"x"}}
            ]
        """.trimIndent()
        server.enqueue(MockResponse().setBody(body).setHeader("Content-Type", "application/json"))

        val result = client.pollIssues("owner", "repo", GithubPollCheckpoint(sinceMillis = 0, etag = null, lastSeenIssueNumber = 0))

        assertEquals(1, result.issues.size)
        assertEquals(10, result.issues.first().number)
    }

    @Test
    fun `paginates via Link header until no next relation`() {
        server.enqueue(
            MockResponse()
                .setBody("""[{"id":1,"number":1,"title":"t1","state":"open","html_url":"h","created_at":"2024-01-01T00:00:00Z","updated_at":"2024-01-01T00:00:00Z"}]""")
                .setHeader("Link", "<${server.url("/x?page=2")}>; rel=\"next\""),
        )
        server.enqueue(
            MockResponse()
                .setBody("""[{"id":2,"number":2,"title":"t2","state":"open","html_url":"h","created_at":"2024-01-02T00:00:00Z","updated_at":"2024-01-02T00:00:00Z"}]"""),
        )

        val result = client.pollIssues("owner", "repo", GithubPollCheckpoint(sinceMillis = 0, etag = null, lastSeenIssueNumber = 0))

        assertEquals(2, result.issues.size)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `304 response short-circuits with notModified and unchanged checkpoint`() {
        server.enqueue(MockResponse().setResponseCode(304))
        val checkpoint = GithubPollCheckpoint(sinceMillis = 5000L, etag = "\"abc\"", lastSeenIssueNumber = 7)

        val result = client.pollIssues("owner", "repo", checkpoint)

        assertTrue(result.notModified)
        assertTrue(result.issues.isEmpty())
        assertEquals(checkpoint, result.nextCheckpoint)
    }

    @Test
    fun `conditional request sends If-None-Match when an etag checkpoint exists`() {
        server.enqueue(MockResponse().setResponseCode(304))
        client.pollIssues("owner", "repo", GithubPollCheckpoint(sinceMillis = 0, etag = "\"abc\"", lastSeenIssueNumber = 0))

        val recorded = server.takeRequest()
        assertEquals("\"abc\"", recorded.getHeader("If-None-Match"))
    }

    @Test
    fun `next checkpoint since is behind the newest updated_at by the overlap buffer`() {
        server.enqueue(
            MockResponse().setBody(
                """[{"id":1,"number":1,"title":"t1","state":"open","html_url":"h","created_at":"2024-01-01T00:00:00Z","updated_at":"2024-06-01T00:00:00Z"}]""",
            ),
        )

        val result = client.pollIssues("owner", "repo", GithubPollCheckpoint(sinceMillis = 0, etag = null, lastSeenIssueNumber = 0))

        val newestMillis = java.time.Instant.parse("2024-06-01T00:00:00Z").toEpochMilli()
        assertEquals(newestMillis - GitHubIssuePollingClient.OVERLAP_BUFFER_MILLIS, result.nextCheckpoint.sinceMillis)
        assertFalse(result.notModified)
    }
}

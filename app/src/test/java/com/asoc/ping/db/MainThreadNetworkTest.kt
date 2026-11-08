package com.asoc.ping.db

import android.os.Looper
import com.asoc.ping.data.github.GitHubIssuePollRepository
import com.asoc.ping.data.github.GitHubIssuePollingClient
import com.asoc.ping.data.play.PlayReviewRepository
import com.asoc.ping.data.play.PlayReviewsApiClient
import com.asoc.ping.domain.model.Signal
import com.asoc.ping.domain.model.SignalType
import com.asoc.ping.domain.repository.SignalRepository
import com.asoc.ping.fakes.FakeSignalDao
import com.asoc.ping.play.FakeCheckpointDao
import com.asoc.ping.play.FakeGovernanceDao
import com.asoc.ping.play.FakePlayAuth
import com.asoc.ping.play.MemCreds
import java.io.IOException
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The P0 device bug: repositories were called from viewModelScope on Main and did blocking I/O.
 * Here the test thread IS the Android main thread (Robolectric), the repositories run on their
 * default (real) IO dispatcher, and an interceptor fails any request issued from the main thread.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class MainThreadNetworkTest {
    private val mainThreadGuard = Interceptor { chain ->
        if (Looper.myLooper() == Looper.getMainLooper()) throw IOException("NETWORK ON MAIN THREAD")
        chain.proceed(chain.request())
    }
    private val http = OkHttpClient.Builder().addInterceptor(mainThreadGuard).build()

    @Test fun `play poll, play reply and github poll never touch the network on the main thread`() = runBlocking {
        assertEquals(Looper.getMainLooper().thread, Thread.currentThread())
        val server = MockWebServer().apply { start() }
        val base = server.url("/").toString().trimEnd('/')
        server.enqueue(MockResponse().setBody("""{"reviews":[{"reviewId":"r1","comments":[{"userComment":{"text":"x","starRating":2,"lastModified":{"seconds":"10"}}}]}]}"""))
        server.enqueue(MockResponse().setBody("{}"))
        server.enqueue(MockResponse().setBody("[]"))

        val signals = SignalRepository(FakeSignalDao())
        val creds = MemCreds().apply { putPlayServiceAccountKeyJson("""{"client_email":"sa@x","private_key":"pem"}""") }
        val play = PlayReviewRepository(PlayReviewsApiClient(http, FakePlayAuth(), base), FakePlayAuth(), creds, FakeCheckpointDao(), signals, FakeGovernanceDao())
        play.pollRecentReviews("com.foo")
        play.sendConfirmedReply("play:com.foo:r1", "Thanks")

        val gh = GitHubIssuePollRepository(GitHubIssuePollingClient(http, { "t" }, base), FakeCheckpointDao(), signals)
        gh.pollRepo("o", "r")
        assertEquals(3, server.requestCount) // all three succeeded: no main-thread IOException
        server.shutdown()
    }
}

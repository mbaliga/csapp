package com.mbaliga.csapp.incident

import com.mbaliga.csapp.domain.model.ReplyState
import com.mbaliga.csapp.domain.model.Signal
import com.mbaliga.csapp.domain.model.SignalType
import com.mbaliga.csapp.domain.repository.IngestOutcome
import com.mbaliga.csapp.domain.repository.SignalRepository
import com.mbaliga.csapp.fakes.FakeSignalDao
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

class SignalRepositoryTest {

    private lateinit var dao: FakeSignalDao
    private lateinit var repository: SignalRepository

    @Before
    fun setUp() {
        dao = FakeSignalDao()
        repository = SignalRepository(dao)
    }

    private fun signal(body: String, updatedAt: Long) = Signal(
        sourceKey = "github:owner/repo#1",
        type = SignalType.GITHUB_ISSUE,
        title = "An issue",
        body = body,
        authorName = "reporter",
        rating = null,
        createdAt = 1000,
        sourceUpdatedAt = updatedAt,
        ingestedAt = updatedAt,
        replyState = ReplyState.NONE,
    )

    @Test
    fun `first ingestion of a signal is INSERTED`() = runTest {
        val result = repository.ingestOrUpdate(signal("original body", 1000))
        assertEquals(IngestOutcome.INSERTED, result.outcome)
    }

    @Test
    fun `re-ingesting identical content is UNCHANGED`() = runTest {
        repository.ingestOrUpdate(signal("original body", 1000))
        val result = repository.ingestOrUpdate(signal("original body", 1000))
        assertEquals(IngestOutcome.UNCHANGED, result.outcome)
    }

    @Test
    fun `re-ingesting edited content is detected as UPDATED_EDITED and preserves the original hash`() = runTest {
        repository.ingestOrUpdate(signal("original body", 1000))
        val result = repository.ingestOrUpdate(signal("edited body", 2000))

        assertEquals(IngestOutcome.UPDATED_EDITED, result.outcome)
        assertEquals("edited body", result.signal.body)
        assertEquals(2000L, result.signal.editedAt)
        assertNotNull(result.signal.originalBodyHash)

        val stored = dao.findBySourceKey("github:owner/repo#1")
        assertNotNull(stored?.originalBodyHash)
    }

    @Test
    fun `reply lifecycle moves NONE to DRAFTED to SEND_CONFIRMED to SENT`() = runTest {
        repository.ingestOrUpdate(signal("body", 1000).copy(sourceKey = "play:app:r1", type = SignalType.PLAY_REVIEW))

        repository.updateReplyDraft("play:app:r1", "Thanks for the feedback!")
        assertEquals(ReplyState.DRAFTED, dao.findBySourceKey("play:app:r1")?.replyState?.let { ReplyState.valueOf(it) })

        repository.markReplyConfirmedSending("play:app:r1")
        assertEquals(ReplyState.SEND_CONFIRMED, dao.findBySourceKey("play:app:r1")?.replyState?.let { ReplyState.valueOf(it) })

        repository.markReplySent("play:app:r1", 5000)
        val finalEntity = dao.findBySourceKey("play:app:r1")
        assertEquals(ReplyState.SENT, finalEntity?.replyState?.let { ReplyState.valueOf(it) })
        assertEquals(5000L, finalEntity?.repliedAt)
    }
}

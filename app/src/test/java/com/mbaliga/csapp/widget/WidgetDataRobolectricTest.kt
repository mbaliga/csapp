package com.mbaliga.csapp.widget

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mbaliga.csapp.data.db.AppDatabase
import com.mbaliga.csapp.data.db.entities.IncidentEntity
import com.mbaliga.csapp.data.db.entities.MailboxEntity
import com.mbaliga.csapp.data.db.entities.ProjectEntity
import com.mbaliga.csapp.data.db.entities.ProjectSourceEntity
import com.mbaliga.csapp.data.db.entities.SignalEntity
import com.mbaliga.csapp.data.db.entities.SuggestionEntity
import com.mbaliga.csapp.data.github.GitHubIssuePollRepository
import com.mbaliga.csapp.data.github.GitHubIssuePollingClient
import com.mbaliga.csapp.data.mail.MailPollRepository
import com.mbaliga.csapp.data.mail.MailSource
import com.mbaliga.csapp.data.play.PlayReviewRepository
import com.mbaliga.csapp.data.play.PlayReviewsApiClient
import com.mbaliga.csapp.data.play.PlayServiceAccountAuth
import com.mbaliga.csapp.data.project.ProjectRepository
import com.mbaliga.csapp.data.sync.PollChannel
import com.mbaliga.csapp.data.sync.PollCoordinator
import com.mbaliga.csapp.data.sync.PollHealth
import com.mbaliga.csapp.data.sync.PollPass
import com.mbaliga.csapp.domain.model.IncidentStatus
import com.mbaliga.csapp.domain.model.ReplyState
import com.mbaliga.csapp.domain.model.Severity
import com.mbaliga.csapp.domain.model.SignalType
import com.mbaliga.csapp.domain.model.SourceKind
import com.mbaliga.csapp.domain.repository.IncidentRepository
import com.mbaliga.csapp.domain.repository.SignalRepository
import com.mbaliga.csapp.play.MemCreds
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The widget queries and the sync-outcome record, on real SQLite + real Room. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class WidgetDataRobolectricTest {
    private lateinit var db: AppDatabase
    private val clock = TestClock.clock()
    private val today = TestClock.at(2026, 11, 4, 9, 0)
    private val yesterday = TestClock.at(2026, 11, 3, 23, 59)

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java).allowMainThreadQueries().build()
    }

    @After fun tearDown() = db.close()

    private fun signal(
        key: String, type: SignalType, incident: String?, project: String = "fotoz", title: String = "t", body: String = "b",
        createdAt: Long = yesterday, reply: ReplyState = ReplyState.NONE, draft: String? = null, rating: Int? = null,
    ) = SignalEntity(
        sourceKey = key, type = type.name, title = title, body = body, authorName = "Jane Reviewer", rating = rating,
        createdAt = createdAt, sourceUpdatedAt = createdAt, ingestedAt = createdAt, editedAt = null, originalBodyHash = null,
        incidentId = incident, replyState = reply.name, replyDraft = draft, repliedAt = null, metadataJson = null, projectId = project,
    )

    private fun inc(id: String, title: String, sev: Severity, status: IncidentStatus = IncidentStatus.OPEN, project: String = "fotoz") =
        IncidentEntity(id, title, "s", sev.name, status.name, false, null, null, 1, 2, project)

    private fun loader(health: PollHealth = PollHealth()) = WidgetSnapshotLoader(db.widgetDao(), { health }, { 30 })

    @Test fun `empty database is the first-run state, also with only archived data`() = runBlocking {
        db.projectDao().upsert(ProjectEntity("inbox", "Inbox", "csapp:inbox", 0, false))
        val snap = loader().load(clock)
        assertTrue(snap.firstRun)
        assertTrue(snap.live.isEmpty())
        db.projectDao().upsert(ProjectEntity("old", "Old", "csapp:old", 3, true))
        db.signalDao().insertIgnoringDuplicates(signal("play:com.o:z", SignalType.PLAY_REVIEW, null, project = "old"))
        val archivedOnly = loader().load(clock)
        assertTrue(archivedOnly.firstRun)
        assertEquals(0, archivedOnly.ungrouped)
    }

    @Test fun `a title whose email or review anchor was split or moved out stays hidden`() = runBlocking {
        db.projectDao().upsert(ProjectEntity("fotoz", "Fotoz", "csapp:fotoz", 1, false))
        db.projectDao().upsert(ProjectEntity("notes", "Notes", "csapp:notes", 2, false))
        val subject = "Refund for order 5531 - Jane Doe"
        val i = db.incidentDao()
        i.upsert(inc("i_mail", subject, Severity.HIGH))
        i.upsert(inc("i_moved", "Worst update ever, Jane here", Severity.MEDIUM))
        i.upsert(inc("i_gh", "Crash on launch", Severity.LOW))
        val s = db.signalDao()
        s.insertIgnoringDuplicates(signal("mail:m1:t1", SignalType.EMAIL, "i_mail", title = subject, body = "My card was charged twice"))
        s.insertIgnoringDuplicates(signal("github:o/r#7", SignalType.GITHUB_ISSUE, "i_mail", title = "Refunds fail with error 500"))
        s.insertIgnoringDuplicates(signal("play-backfill:b", SignalType.PLAY_REVIEW, "i_moved", title = "Worst update ever, Jane here", body = "lost photos"))
        s.insertIgnoringDuplicates(signal("github:o/r#8", SignalType.GITHUB_ISSUE, "i_gh", title = "Crash on launch"))
        assertEquals(ShownTitle.Withheld(WithheldReason.QUOTES_EMAIL), loader().load(clock).live.single { it.id == "i_mail" }.title)

        // The detail screen's split and the move to another project both leave the old title behind.
        IncidentRepository(db.incidentDao(), db.signalDao()).split("i_mail", listOf("mail:m1:t1"))
        SignalRepository(db.signalDao()).moveToProject("play-backfill:b", "notes")
        assertEquals(subject, db.incidentDao().findById("i_mail")!!.title)
        assertTrue(db.signalDao().findByIncidentId("i_moved").isEmpty())

        val snap = loader().load(clock)
        val byId = snap.live.associateBy { it.id }
        assertEquals(ShownTitle.Withheld(WithheldReason.QUOTES_EMAIL), byId.getValue("i_mail").title)
        assertEquals(ShownTitle.Withheld(WithheldReason.QUOTES_REVIEW), byId.getValue("i_moved").title)
        assertEquals(ShownTitle.Text("Crash on launch"), byId.getValue("i_gh").title)
        val printed = WidgetKind.entries.flatMap { k -> WidgetSize.entries.map { WidgetFaces(XmlWidgetText(), clock).face(k, it, snap).description } }.joinToString("\n")
        for (secret in listOf("Jane", "5531", "Worst update")) assertFalse("'$secret' reached a widget", printed.contains(secret))
        assertTrue(printed.contains("Crash on launch"))
    }

    @Test fun `an accepted AI title is shown, a rejected one or a kept subject line is not`() = runBlocking {
        db.projectDao().upsert(ProjectEntity("fotoz", "Fotoz", "csapp:fotoz", 1, false))
        db.projectDao().upsert(ProjectEntity("notes", "Notes", "csapp:notes", 2, false))
        val subject = "Cancel my subscription - Ravi"
        val i = db.incidentDao()
        i.upsert(inc("i_ai", "Photo loss after update", Severity.HIGH))
        i.upsert(inc("i_kept", subject, Severity.MEDIUM))
        i.upsert(inc("i_rej", "Battery drain", Severity.LOW))
        val s = db.signalDao()
        s.insertIgnoringDuplicates(signal("play-backfill:c", SignalType.PLAY_REVIEW, "i_ai", title = "Everything gone!!", body = "I lost every photo"))
        s.insertIgnoringDuplicates(signal("mail:m1:t2", SignalType.EMAIL, null, project = "notes", title = subject, body = "Please cancel"))
        s.insertIgnoringDuplicates(signal("play-backfill:d", SignalType.PLAY_REVIEW, "i_rej", title = "Dies by noon", body = "Phone gets hot"))
        val g = db.governanceDao()
        g.upsertSuggestion(SuggestionEntity("t1", "INCIDENT", "i_ai", "TRIAGE", """{"severity":"HIGH","title":"Photo loss after update","summary":"s"}""", "LOCAL", "m", "triage.v1", 1, "ACCEPTED"))
        // The model gave no title, so the payload kept the old one: an email subject.
        g.upsertSuggestion(SuggestionEntity("t2", "INCIDENT", "i_kept", "TRIAGE", """{"severity":"MEDIUM","title":"$subject"}""", "LOCAL", "m", "triage.v1", 1, "ACCEPTED"))
        g.upsertSuggestion(SuggestionEntity("t3", "INCIDENT", "i_rej", "TRIAGE", """{"severity":"LOW","title":"Battery drain"}""", "LOCAL", "m", "triage.v1", 1, "REJECTED"))

        val byId = loader().load(clock).live.associateBy { it.id }
        assertEquals(ShownTitle.Text("Photo loss after update"), byId.getValue("i_ai").title)
        assertEquals(ShownTitle.Withheld(WithheldReason.QUOTES_EMAIL), byId.getValue("i_kept").title)
        assertEquals(ShownTitle.Withheld(WithheldReason.UNVERIFIED), byId.getValue("i_rej").title)
    }

    @Test fun `snapshot reads live incidents, replies, today and sources - and nothing private`() = runBlocking {
        val p = db.projectDao()
        p.upsert(ProjectEntity("fotoz", "Fotoz", "csapp:fotoz", 1, false))
        p.upsert(ProjectEntity("notes", "Notes", "csapp:notes", 2, false))
        p.upsert(ProjectEntity("old", "Old", "csapp:old", 3, true))
        p.insertSource(ProjectSourceEntity(projectId = "fotoz", kind = SourceKind.GITHUB_REPO.name, value = "o/r", enabled = true))
        p.insertSource(ProjectSourceEntity(projectId = "fotoz", kind = SourceKind.PLAY_PACKAGE.name, value = "com.f", enabled = true))
        p.insertSource(ProjectSourceEntity(projectId = "notes", kind = SourceKind.PLAY_PACKAGE.name, value = "com.n", enabled = false))
        p.upsertMailbox(MailboxEntity("m1", "Support", "IMAP", "h", 993, "u", "INBOX", true))

        val i = db.incidentDao()
        i.upsert(inc("i_crit", "Worst update ever", Severity.CRITICAL))
        i.upsert(inc("i_high", "Crash on launch", Severity.HIGH, IncidentStatus.ACKNOWLEDGED, project = "notes"))
        i.upsert(inc("i_mail", "Refund please", Severity.MEDIUM))
        i.upsert(inc("i_resolved", "Done", Severity.CRITICAL, IncidentStatus.RESOLVED))
        i.upsert(inc("i_archived", "Gone", Severity.CRITICAL, project = "old"))

        val s = db.signalDao()
        s.insertIgnoringDuplicates(signal("play-backfill:a", SignalType.PLAY_REVIEW, "i_crit", title = "Worst update ever", body = "lost my photos", createdAt = today, rating = 1))
        s.insertIgnoringDuplicates(signal("play:com.f:r2", SignalType.PLAY_REVIEW, "i_crit", title = "2★ review", createdAt = today, reply = ReplyState.FAILED, rating = 2))
        s.insertIgnoringDuplicates(signal("github:o/r#1", SignalType.GITHUB_ISSUE, "i_high", project = "notes", title = "Crash on launch", createdAt = today))
        s.insertIgnoringDuplicates(signal("mail:m1:x", SignalType.EMAIL, "i_mail", title = "Refund please", body = "my card"))
        s.insertIgnoringDuplicates(signal("play:com.f:r3", SignalType.PLAY_REVIEW, null, reply = ReplyState.DRAFTED, draft = "Thanks for the report", rating = 4))
        s.insertIgnoringDuplicates(signal("play:com.f:r4", SignalType.PLAY_REVIEW, null, reply = ReplyState.DRAFTED, draft = "   "))
        s.insertIgnoringDuplicates(signal("play:com.o:r5", SignalType.PLAY_REVIEW, "i_archived", project = "old", reply = ReplyState.FAILED))
        s.insertIgnoringDuplicates(signal("play:com.f:r6", SignalType.PLAY_REVIEW, null, reply = ReplyState.SENT))
        s.insertIgnoringDuplicates(signal("play:com.o:r7", SignalType.PLAY_REVIEW, null, project = "old"))
        db.governanceDao().upsertSuggestion(SuggestionEntity("g1", "SIGNAL", "play:com.f:r3", "REPLY_DRAFT", "{}", "LOCAL", "m", "reply.v1", 1, "PENDING"))
        db.governanceDao().upsertSuggestion(SuggestionEntity("g2", "SIGNAL", "play:com.f:r3", "REPLY_DRAFT", "{}", "LOCAL", "m", "reply.v1", 1, "REJECTED"))

        val snap = loader().load(clock)

        assertFalse(snap.firstRun)
        assertEquals(listOf("fotoz", "notes"), snap.projects.map { it.id })
        assertEquals(listOf("i_crit", "i_high", "i_mail"), snap.live.map { it.id })
        assertEquals(mapOf(SignalType.PLAY_REVIEW to 2), snap.live[0].channelCounts)
        // The backfilled review's own title and the email subject never reach the widget.
        assertEquals(ShownTitle.Withheld(WithheldReason.QUOTES_REVIEW), snap.live[0].title)
        assertEquals(ShownTitle.Text("Crash on launch"), snap.live[1].title)
        assertEquals(ShownTitle.Withheld(WithheldReason.QUOTES_EMAIL), snap.live[2].title)

        assertEquals(listOf("play:com.f:r2" to ReplyState.FAILED, "play:com.f:r3" to ReplyState.DRAFTED), snap.replies.items.map { it.sourceKey to it.state })
        assertEquals(1, snap.replies.aiDrafts)

        assertEquals(
            setOf(TodayCount("fotoz", SignalType.PLAY_REVIEW, 2), TodayCount("notes", SignalType.GITHUB_ISSUE, 1)),
            snap.today.toSet(),
        )
        assertEquals("archived projects' signals are not counted", 3, snap.ungrouped)
        assertEquals(mapOf(PollChannel.GITHUB to 1, PollChannel.PLAY to 1, PollChannel.MAIL to 1), snap.sync.configured)

        // Nothing the faces can print carries a reviewer's name or review / mail text.
        val faces = WidgetFaces(XmlWidgetText(), clock)
        val printed = WidgetKind.entries.flatMap { k -> WidgetSize.entries.map { faces.face(k, it, snap).description } }.joinToString("\n")
        for (secret in listOf("Jane", "Worst update", "lost my photos", "Refund please", "my card", "Thanks for the report")) {
            assertFalse("'$secret' reached a widget", printed.contains(secret))
        }
        assertTrue(printed.contains("Crash on launch"))
    }

    @Test fun `a sync pass records per-channel outcomes`() = runBlocking {
        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) =
                if (request.path!!.startsWith("/repos/o/ok/")) MockResponse().setBody("[]") else MockResponse().setResponseCode(500)
        }
        server.start()
        try {
            val projects = ProjectRepository(db.projectDao())
            db.projectDao().upsert(ProjectEntity("fotoz", "Fotoz", "csapp:fotoz", 1, false))
            projects.addSource("fotoz", SourceKind.GITHUB_REPO, "o/ok")
            projects.addSource("fotoz", SourceKind.GITHUB_REPO, "o/broken")
            projects.addSource("fotoz", SourceKind.PLAY_PACKAGE, "com.f")
            db.projectDao().upsertMailbox(MailboxEntity("m1", "Support", "IMAP", "h", 993, "u", "INBOX", true))

            val http = OkHttpClient()
            val creds = MemCreds() // no Play key, no mailbox password: both fail, as on a fresh install
            val signals = SignalRepository(db.signalDao())
            val passes = mutableListOf<PollPass>()
            val coordinator = PollCoordinator(
                projects,
                GitHubIssuePollRepository(GitHubIssuePollingClient(http, { "t" }, server.url("/").toString().trimEnd('/')), db.checkpointDao(), signals),
                PlayReviewRepository(PlayReviewsApiClient(http, PlayServiceAccountAuth(http)), PlayServiceAccountAuth(http), creds, db.checkpointDao(), signals, db.governanceDao()),
                MailPollRepository(db.projectDao(), signals, creds, sourceFor = { MailSource { _, _, _ -> error("not reached") } }),
                IncidentRepository(db.incidentDao(), db.signalDao()),
                creds,
                health = { passes += it },
                now = { 42L },
            )
            val report = coordinator.syncAll()

            assertEquals(3, report.failures.size)
            val pass = passes.single()
            assertEquals(42L, pass.at)
            assertEquals(mapOf(PollChannel.GITHUB to 2, PollChannel.PLAY to 1, PollChannel.MAIL to 1), pass.attempted)
            assertEquals(mapOf(PollChannel.GITHUB to 1, PollChannel.PLAY to 1, PollChannel.MAIL to 1), pass.failed)
        } finally {
            server.shutdown()
        }
    }

    @Test fun `a broken health sink never breaks the sync`() = runBlocking {
        val creds = MemCreds()
        val http = OkHttpClient()
        val signals = SignalRepository(db.signalDao())
        val coordinator = PollCoordinator(
            ProjectRepository(db.projectDao()),
            GitHubIssuePollRepository(GitHubIssuePollingClient(http, { "t" }), db.checkpointDao(), signals),
            PlayReviewRepository(PlayReviewsApiClient(http, PlayServiceAccountAuth(http)), PlayServiceAccountAuth(http), creds, db.checkpointDao(), signals, db.governanceDao()),
            MailPollRepository(db.projectDao(), signals, creds, sourceFor = { MailSource { _, _, _ -> error("not reached") } }),
            IncidentRepository(db.incidentDao(), db.signalDao()),
            creds,
            health = { error("disk full") },
        )
        assertTrue(coordinator.syncAll().ok)
    }
}

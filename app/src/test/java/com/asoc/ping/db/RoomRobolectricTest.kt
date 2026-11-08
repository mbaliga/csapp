package com.asoc.ping.db

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.asoc.ping.data.db.AppDatabase
import com.asoc.ping.data.db.Migrations
import com.asoc.ping.data.db.entities.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Real SQLite + real Room: proves the v2 schema, the 1->2 migration and the new DAO queries work. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class RoomRobolectricTest {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    @Test fun `room validates MIGRATION_1_2 against the exported schemas and keeps legacy rows`() {
        helper.createDatabase("mig.db", 1).apply {
            execSQL("INSERT INTO incidents(id,title,summary,severity,status,isManual,createdAt,updatedAt) VALUES('inc_x','t','s','LOW','OPEN',0,1,1)")
            execSQL("INSERT INTO signals(sourceKey,type,title,body,createdAt,sourceUpdatedAt,ingestedAt,replyState) VALUES('github:o/r#1','GITHUB_ISSUE','t','b',1,1,1,'NONE')")
            close()
        }
        val db = helper.runMigrationsAndValidate("mig.db", 2, true, Migrations.MIGRATION_1_2)
        db.query("SELECT projectId FROM incidents").use { assertEquals(true, it.moveToFirst()); assertEquals("default", it.getString(0)) }
        db.query("SELECT projectId FROM signals").use { assertEquals(true, it.moveToFirst()); assertEquals("default", it.getString(0)) }
    }

    @Test fun `new DAOs work on a real database`() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).allowMainThreadQueries().build()
        db.projectDao().upsert(ProjectEntity("fotoz", "Fotoz", "csapp:fotoz", 1, false))
        db.projectDao().insertSource(ProjectSourceEntity(projectId = "fotoz", kind = "GITHUB_REPO", value = "o/r", enabled = true))
        assertEquals(1, db.projectDao().allEnabledSources().size)
        db.projectDao().upsertMailbox(MailboxEntity("m1", "Support", "IMAP", "h", 993, "u", "INBOX", true))
        assertEquals(1, db.projectDao().observeMailboxes().first().size)
        db.signalDao().insertIgnoringDuplicates(SignalEntity(sourceKey = "mail:m1:a@x", type = "EMAIL", title = "t", body = "b", authorName = null, rating = null,
            createdAt = 1, sourceUpdatedAt = 1, ingestedAt = 1, editedAt = null, originalBodyHash = null, incidentId = null, replyState = "NONE",
            replyDraft = null, repliedAt = null, metadataJson = """{"threadId":"a@x","messageIds":["a@x"]}""", projectId = "fotoz"))
        assertNotNull(db.signalDao().findEmailByThreadId("a@x"))
        assertEquals(null, db.signalDao().findEmailByThreadId("zzz"))
        assertEquals(1, db.projectDao().signalCount("fotoz"))
        db.governanceDao().upsertSuggestion(SuggestionEntity("s1", "INCIDENT", "i", "TRIAGE", "{}", "LOCAL", "m", "triage.v1", 1, "PENDING"))
        assertEquals(1, db.governanceDao().observePending().first().size)
        db.governanceDao().insertReplyLog(ReplyLogEntity(signalSourceKey = "k", textSent = "t", sentAt = 1, outcome = "SENT", httpStatus = 200))
        assertEquals(1, db.governanceDao().observeReplyLog("k").first().size)
        db.close()
    }
}

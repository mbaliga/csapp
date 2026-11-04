package com.mbaliga.csapp.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.mbaliga.csapp.data.db.entities.MailCheckpointEntity
import com.mbaliga.csapp.data.db.entities.MailboxEntity
import com.mbaliga.csapp.data.db.entities.ProjectEntity
import com.mbaliga.csapp.data.db.entities.ProjectSourceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(project: ProjectEntity)
    @Query("SELECT * FROM projects WHERE id = :id") suspend fun find(id: String): ProjectEntity?
    @Query("SELECT * FROM projects WHERE archived = 0 ORDER BY createdAt ASC") fun observeActive(): Flow<List<ProjectEntity>>
    @Query("SELECT * FROM projects WHERE archived = 0 ORDER BY createdAt ASC") suspend fun findActive(): List<ProjectEntity>

    @Insert suspend fun insertSource(source: ProjectSourceEntity): Long
    @Update suspend fun updateSource(source: ProjectSourceEntity)
    @Query("DELETE FROM project_sources WHERE id = :id") suspend fun deleteSource(id: Long)
    @Query("SELECT * FROM project_sources WHERE projectId = :projectId ORDER BY id") suspend fun sourcesFor(projectId: String): List<ProjectSourceEntity>
    @Query("SELECT * FROM project_sources WHERE projectId = :projectId ORDER BY id") fun observeSourcesFor(projectId: String): Flow<List<ProjectSourceEntity>>
    @Query("SELECT * FROM project_sources WHERE enabled = 1 ORDER BY id") suspend fun allEnabledSources(): List<ProjectSourceEntity>

    @Query("SELECT COUNT(*) FROM signals WHERE projectId = :projectId") suspend fun signalCount(projectId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertMailbox(mailbox: MailboxEntity)
    @Query("DELETE FROM mailboxes WHERE id = :id") suspend fun deleteMailbox(id: String)
    @Query("SELECT * FROM mailboxes ORDER BY label") fun observeMailboxes(): Flow<List<MailboxEntity>>
    @Query("SELECT * FROM mailboxes WHERE id = :id") suspend fun findMailbox(id: String): MailboxEntity?
    @Query("SELECT * FROM mailboxes WHERE enabled = 1") suspend fun enabledMailboxes(): List<MailboxEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertMailCheckpoint(c: MailCheckpointEntity)
    @Query("SELECT * FROM mail_checkpoints WHERE `key` = :key") suspend fun findMailCheckpoint(key: String): MailCheckpointEntity?
}

package com.asoc.ping.data.db

import com.asoc.ping.data.db.entities.*

import androidx.room.Database
import androidx.room.RoomDatabase
import com.asoc.ping.data.db.dao.CheckpointDao
import com.asoc.ping.data.db.dao.IncidentDao
import com.asoc.ping.data.db.dao.SignalDao
import com.asoc.ping.data.db.entities.GithubCheckpointEntity
import com.asoc.ping.data.db.entities.IncidentEntity
import com.asoc.ping.data.db.entities.PlayCheckpointEntity
import com.asoc.ping.data.db.entities.SignalEntity

@Database(
    entities = [
        SignalEntity::class,
        IncidentEntity::class,
        GithubCheckpointEntity::class,
        PlayCheckpointEntity::class,
        ProjectEntity::class,
        ProjectSourceEntity::class,
        MailboxEntity::class,
        MailCheckpointEntity::class,
        ReplyLogEntity::class,
        QuotaEntity::class,
        SuggestionEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun signalDao(): SignalDao
    abstract fun incidentDao(): IncidentDao
    abstract fun checkpointDao(): CheckpointDao
    abstract fun projectDao(): com.asoc.ping.data.db.dao.ProjectDao
    abstract fun governanceDao(): com.asoc.ping.data.db.dao.GovernanceDao

    companion object {
        const val DATABASE_NAME = "csapp.db"
    }
}

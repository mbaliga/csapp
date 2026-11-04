package com.mbaliga.csapp.data.db

import com.mbaliga.csapp.data.db.entities.*

import androidx.room.Database
import androidx.room.RoomDatabase
import com.mbaliga.csapp.data.db.dao.CheckpointDao
import com.mbaliga.csapp.data.db.dao.IncidentDao
import com.mbaliga.csapp.data.db.dao.SignalDao
import com.mbaliga.csapp.data.db.entities.GithubCheckpointEntity
import com.mbaliga.csapp.data.db.entities.IncidentEntity
import com.mbaliga.csapp.data.db.entities.PlayCheckpointEntity
import com.mbaliga.csapp.data.db.entities.SignalEntity

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
    abstract fun projectDao(): com.mbaliga.csapp.data.db.dao.ProjectDao
    abstract fun governanceDao(): com.mbaliga.csapp.data.db.dao.GovernanceDao
    /** Read-only queries for the home-screen widgets (no entity, so no schema change). */
    abstract fun widgetDao(): com.mbaliga.csapp.data.db.dao.WidgetDao

    companion object {
        const val DATABASE_NAME = "csapp.db"
    }
}

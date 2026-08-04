package com.mbaliga.csapp.data.db

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
    ],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun signalDao(): SignalDao
    abstract fun incidentDao(): IncidentDao
    abstract fun checkpointDao(): CheckpointDao

    companion object {
        const val DATABASE_NAME = "csapp.db"
    }
}

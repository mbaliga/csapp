package com.mbaliga.csapp.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mbaliga.csapp.data.db.entities.GithubCheckpointEntity
import com.mbaliga.csapp.data.db.entities.PlayCheckpointEntity

@Dao
interface CheckpointDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertGithubCheckpoint(checkpoint: GithubCheckpointEntity)

    @Query("SELECT * FROM github_checkpoints WHERE repoFullName = :repoFullName LIMIT 1")
    suspend fun findGithubCheckpoint(repoFullName: String): GithubCheckpointEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPlayCheckpoint(checkpoint: PlayCheckpointEntity)

    @Query("SELECT * FROM play_checkpoints WHERE packageName = :packageName LIMIT 1")
    suspend fun findPlayCheckpoint(packageName: String): PlayCheckpointEntity?
}

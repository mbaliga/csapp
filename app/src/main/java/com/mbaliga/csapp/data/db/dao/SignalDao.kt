package com.mbaliga.csapp.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.mbaliga.csapp.data.db.entities.SignalEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SignalDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnoringDuplicates(signal: SignalEntity): Long

    @Update
    suspend fun update(signal: SignalEntity)

    @Query("SELECT * FROM signals WHERE sourceKey = :sourceKey LIMIT 1")
    suspend fun findBySourceKey(sourceKey: String): SignalEntity?

    @Query("SELECT * FROM signals WHERE incidentId IS NULL ORDER BY createdAt ASC")
    suspend fun findUnclustered(): List<SignalEntity>

    @Query("SELECT * FROM signals WHERE incidentId = :incidentId ORDER BY createdAt ASC")
    suspend fun findByIncidentId(incidentId: String): List<SignalEntity>

    @Query("SELECT * FROM signals WHERE incidentId IN (:incidentIds)")
    suspend fun findByIncidentIds(incidentIds: List<String>): List<SignalEntity>

    @Query("UPDATE signals SET incidentId = :incidentId WHERE sourceKey = :sourceKey")
    suspend fun assignIncident(sourceKey: String, incidentId: String)

    @Query("SELECT * FROM signals ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<SignalEntity>>

    @Query("SELECT * FROM signals WHERE type = 'PLAY_REVIEW' AND replyState != 'SENT' ORDER BY createdAt DESC")
    fun observeReplyableReviews(): Flow<List<SignalEntity>>
}

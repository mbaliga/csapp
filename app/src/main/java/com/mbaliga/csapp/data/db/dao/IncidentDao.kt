package com.mbaliga.csapp.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.mbaliga.csapp.data.db.entities.IncidentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface IncidentDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(incident: IncidentEntity)

    @Update
    suspend fun update(incident: IncidentEntity)

    @Query("SELECT * FROM incidents WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): IncidentEntity?

    @Query("SELECT * FROM incidents WHERE status NOT IN ('DISMISSED', 'MERGED') ORDER BY updatedAt DESC")
    suspend fun findOpen(): List<IncidentEntity>

    @Query("SELECT * FROM incidents ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<IncidentEntity>>

    @Query("SELECT * FROM incidents ORDER BY updatedAt DESC")
    suspend fun findAll(): List<IncidentEntity>
}

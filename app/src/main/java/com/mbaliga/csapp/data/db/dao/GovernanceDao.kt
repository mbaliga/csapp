package com.mbaliga.csapp.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mbaliga.csapp.data.db.entities.QuotaEntity
import com.mbaliga.csapp.data.db.entities.ReplyLogEntity
import com.mbaliga.csapp.data.db.entities.SuggestionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GovernanceDao {
    @Insert suspend fun insertReplyLog(entry: ReplyLogEntity): Long
    @Query("SELECT * FROM reply_log WHERE signalSourceKey = :key ORDER BY sentAt DESC") fun observeReplyLog(key: String): Flow<List<ReplyLogEntity>>
    @Query("SELECT * FROM reply_log ORDER BY sentAt DESC") suspend fun allReplyLog(): List<ReplyLogEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertQuota(q: QuotaEntity)
    @Query("SELECT * FROM quota_buckets WHERE bucketKey = :key") suspend fun findQuota(key: String): QuotaEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertSuggestion(s: SuggestionEntity)
    @Query("SELECT * FROM suggestions WHERE id = :id") suspend fun findSuggestion(id: String): SuggestionEntity?
    @Query("SELECT * FROM suggestions WHERE targetId = :targetId ORDER BY createdAt DESC") fun observeSuggestionsFor(targetId: String): Flow<List<SuggestionEntity>>
    @Query("SELECT * FROM suggestions WHERE state = 'PENDING' ORDER BY createdAt DESC") fun observePending(): Flow<List<SuggestionEntity>>
    @Query("SELECT * FROM suggestions ORDER BY createdAt DESC") suspend fun allSuggestions(): List<SuggestionEntity>
}

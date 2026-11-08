package com.asoc.ping.data.play

import com.asoc.ping.data.db.dao.GovernanceDao
import com.asoc.ping.data.db.entities.QuotaEntity
import java.util.concurrent.TimeUnit

class QuotaExceededException(message: String) : Exception(message)

/**
 * Persisted fixed-window request counter per package and method. Defaults sit below Google's
 * published limits (200 GET/hour, 2000 POST/day). Exceeding it BLOCKS with a message; nothing
 * is ever silently dropped.
 */
class QuotaGuard(
    private val dao: GovernanceDao,
    private val now: () -> Long = { System.currentTimeMillis() },
    private val getsPerHour: Int = 150,
    private val postsPerDay: Int = 1500,
) {
    suspend fun acquireGet(packageName: String) = acquire("get:$packageName", getsPerHour, TimeUnit.HOURS.toMillis(1), "reviews.list")
    suspend fun acquirePost(packageName: String) = acquire("post:$packageName", postsPerDay, TimeUnit.DAYS.toMillis(1), "reviews.reply")

    private suspend fun acquire(key: String, limit: Int, windowMs: Long, label: String) {
        val t = now()
        val q = dao.findQuota(key)
        val fresh = q == null || t - q.windowStart >= windowMs
        val start = if (fresh) t else q!!.windowStart
        val count = if (fresh) 0 else q!!.count
        if (count >= limit) {
            val mins = TimeUnit.MILLISECONDS.toMinutes(start + windowMs - t).coerceAtLeast(1)
            throw QuotaExceededException("Play $label quota reached ($limit per window); try again in about $mins min")
        }
        dao.upsertQuota(QuotaEntity(key, start, count + 1))
    }
}

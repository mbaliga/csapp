package com.mbaliga.csapp.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "incidents")
data class IncidentEntity(
    @PrimaryKey val id: String,
    val title: String,
    val summary: String,
    val severity: String,
    val status: String,
    val isManual: Boolean,
    val isRecurringOf: String?,
    val mergedInto: String?,
    val createdAt: Long,
    val updatedAt: Long,
)

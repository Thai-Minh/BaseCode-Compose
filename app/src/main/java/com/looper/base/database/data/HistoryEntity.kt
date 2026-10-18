package com.looper.videodownloader.database.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "history_table",
    indices = [
        Index(value = ["lastUrl"], unique = false),
    ],
)
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val historyId: Long = System.currentTimeMillis(),
    val lastUrl: String,
    val isHomePage: Boolean = true,
    val iconUrl: String? = null,
    val title: String,
    val lastVisitedAt: Long = System.currentTimeMillis(),
)
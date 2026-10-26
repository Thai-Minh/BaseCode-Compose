package com.looper.base.database.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "lyrics",
    foreignKeys = [
        ForeignKey(
            entity = MediaItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["mediaItemId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class LyricsEntity(
    @PrimaryKey val mediaItemId: String,
    val syncLevel: LyricsSyncLevel = LyricsSyncLevel.NONE,
    val content: String? = null,
    val source: LyricsProvider? = null,
    val fetchedAt: Long? = null,
    val lastAttemptAt: Long = System.currentTimeMillis(),
    val attemptCount: Int = 0,
)

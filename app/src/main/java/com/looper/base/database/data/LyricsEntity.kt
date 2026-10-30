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
    val format: LyricsFormat? = null,
    val rawContent: String? = null,
    val linesJson: String? = null,
    val parserVersion: Int = 0,
    val songwriters: String? = null,
    val language: String? = null,
    val source: LyricsProvider? = null,
    val sourceUrl: String? = null,
    val matchScore: Int? = null,
    val userOffsetMs: Long = 0,
    val fetchedAt: Long? = null,
    val lastAttemptAt: Long = System.currentTimeMillis(),
    val attemptCount: Int = 0,
)

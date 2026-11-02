package com.looper.base.database.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "playback_event",
    indices = [
        Index(value = ["mediaItemId"]),
        Index(value = ["startedAt"]),
    ],
    foreignKeys = [
        ForeignKey(
            entity = MediaItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["mediaItemId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class PlaybackEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val mediaItemId: String,
    val startedAt: Long,
    val durationMs: Long,
    val listenedMs: Long,
)

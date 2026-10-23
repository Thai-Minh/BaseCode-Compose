package com.looper.base.database.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "play_history",
    indices = [
        Index(value = ["playedAt"]),
        Index(value = ["mediaItemId"]),
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
data class PlayHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val mediaItemId: String,
    val playedAt: Long = System.currentTimeMillis(),
    val lastPositionMs: Long = 0L,
)

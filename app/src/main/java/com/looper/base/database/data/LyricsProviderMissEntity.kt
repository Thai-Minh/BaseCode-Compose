package com.looper.base.database.data

import androidx.room.Entity
import androidx.room.ForeignKey

@Entity(
    tableName = "lyrics_provider_miss",
    primaryKeys = ["mediaItemId", "provider"],
    foreignKeys = [
        ForeignKey(
            entity = MediaItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["mediaItemId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class LyricsProviderMissEntity(
    val mediaItemId: String,
    val provider: LyricsProvider,
    val attemptedAt: Long = System.currentTimeMillis(),
)

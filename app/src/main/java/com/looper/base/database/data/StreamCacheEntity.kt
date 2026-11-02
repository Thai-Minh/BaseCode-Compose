package com.looper.base.database.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "stream_cache",
    foreignKeys = [
        ForeignKey(
            entity = MediaItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["mediaItemId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class StreamCacheEntity(
    @PrimaryKey val mediaItemId: String,
    val url: String,
    val audioUrl: String? = null,
    val mimeType: String? = null,
    val headersJson: String? = null,
    val subtitlesJson: String? = null,
    val hasVideo: Boolean,
    val formatId: String? = null,
    val expiresAt: Long,
    val resolvedAt: Long = System.currentTimeMillis(),
)

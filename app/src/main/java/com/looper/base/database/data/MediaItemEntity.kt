package com.looper.base.database.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "media_item",
    indices = [
        Index(value = ["sourceUrl"], unique = true),
        Index(value = ["platformId"]),
        Index(value = ["addedAt"]),
    ],
    foreignKeys = [
        ForeignKey(
            entity = PlatformEntity::class,
            parentColumns = ["id"],
            childColumns = ["platformId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
)
data class MediaItemEntity(
    @PrimaryKey val id: String,
    val mediaType: MediaType,
    val sourceUrl: String,
    val sourceId: String? = null,
    val platformId: String? = null,
    val title: String,
    val artist: String? = null,
    val album: String? = null,
    val durationMs: Long,
    val thumbnailUrl: String? = null,
    val thumbnailLocalPath: String? = null,
    val isFavorite: Boolean = false,
    val favoritedAt: Long? = null,
    val addedAt: Long = System.currentTimeMillis(),
    val playCount: Int = 0,
    val loudnessDb: Float? = null,
    val isLive: Boolean = false,
)

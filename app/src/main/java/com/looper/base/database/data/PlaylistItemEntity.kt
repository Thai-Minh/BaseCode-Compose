package com.looper.base.database.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "playlist_item",
    primaryKeys = ["playlistId", "mediaItemId"],
    indices = [
        Index(value = ["mediaItemId"]),
        Index(value = ["playlistId", "position"]),
    ],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = MediaItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["mediaItemId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class PlaylistItemEntity(
    val playlistId: String,
    val mediaItemId: String,
    val position: Int,
    val addedAt: Long = System.currentTimeMillis(),
)

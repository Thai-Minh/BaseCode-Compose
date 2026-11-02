package com.looper.base.database.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "queue_item",
    indices = [Index(value = ["mediaItemId"])],
    foreignKeys = [
        ForeignKey(
            entity = MediaItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["mediaItemId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class QueueItemEntity(
    @PrimaryKey val position: Int,
    val mediaItemId: String,
    val shuffleIndex: Int? = null,
)

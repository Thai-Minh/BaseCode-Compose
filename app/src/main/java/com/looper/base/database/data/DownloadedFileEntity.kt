package com.looper.base.database.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "downloaded_file",
    indices = [
        Index(value = ["mediaItemId", "kind"], unique = true),
        Index(value = ["downloadedAt"]),
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
data class DownloadedFileEntity(
    @PrimaryKey val id: String,
    val mediaItemId: String,
    val kind: DownloadKind,
    val qualityLabel: String,
    val formatId: String,
    val localUri: String,
    val mimeType: String,
    val size: Long,
    val downloadedAt: Long = System.currentTimeMillis(),
)

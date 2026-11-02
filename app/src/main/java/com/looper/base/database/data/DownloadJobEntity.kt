package com.looper.base.database.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "download_job",
    indices = [
        Index(value = ["mediaItemId"]),
        Index(value = ["status"]),
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
data class DownloadJobEntity(
    @PrimaryKey val id: String,
    val mediaItemId: String,
    val processId: String,
    val targetKind: DownloadKind,
    val qualityLabel: String,
    val formatSelector: String,
    val cookieFilePath: String? = null,
    val userAgent: String? = null,
    val referer: String? = null,
    val tempDirectory: String? = null,
    val status: DownloadStatus = DownloadStatus.QUEUED,
    val progress: Int = 0,
    val etaSeconds: Long = -1L,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = -1L,
    val error: String? = null,
    val origin: DownloadOrigin = DownloadOrigin.USER,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

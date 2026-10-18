package com.looper.videodownloader.database.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "youtube_dl_download")
data class YoutubeDLDownloadEntity(
    @PrimaryKey val id: String,
    val processId: String,
    val sourceUrl: String,
    val title: String,
    val ownerName: String?,
    val thumbnailUrl: String?,
    val durationMs: Long,
    val extractor: String,
    val formatSelector: String,
    val requestedName: String,
    val suggestedExtension: String,
    val cookieFilePath: String?,
    val userAgent: String?,
    val referer: String?,
    val tempDirectory: String?,
    val finalUri: String?,
    val finalDisplayName: String?,
    val mimeType: String?,
    val status: String,
    val progress: Int,
    val etaSeconds: Long,
    val size: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val error: String?
)

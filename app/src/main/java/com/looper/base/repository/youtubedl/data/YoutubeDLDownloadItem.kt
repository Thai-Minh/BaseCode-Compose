package com.looper.videodownloader.repository.youtubedl.data

import com.looper.videodownloader.database.data.YoutubeDLDownloadEntity

data class YoutubeDLDownloadItem(
    val id: String,
    val sourceUrl: String,
    val title: String,
    val ownerName: String?,
    val thumbnailUrl: String?,
    val durationMs: Long,
    val extractor: String,
    val displayName: String,
    val location: String?,
    val status: YoutubeDLDownloadStatus,
    val progress: Int,
    val size: Long,
    val lastModified: Long,
    val error: String?
)

internal fun YoutubeDLDownloadEntity.toModel() = YoutubeDLDownloadItem(
    id = id,
    sourceUrl = sourceUrl,
    title = title,
    ownerName = ownerName,
    thumbnailUrl = thumbnailUrl,
    durationMs = durationMs,
    extractor = extractor,
    displayName = finalDisplayName ?: requestedName,
    location = finalUri,
    status = YoutubeDLDownloadStatus.valueOf(status),
    progress = progress,
    size = size,
    lastModified = updatedAt,
    error = error
)
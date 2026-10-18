package com.looper.base.repository.youtubedl.data

enum class YoutubeDLDownloadStatus {
    QUEUED,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELED,
    INTERRUPTED
}

internal val TERMINAL_YOUTUBE_DL_STATUSES = setOf(
    YoutubeDLDownloadStatus.COMPLETED.name,
    YoutubeDLDownloadStatus.FAILED.name,
    YoutubeDLDownloadStatus.CANCELED.name,
    YoutubeDLDownloadStatus.INTERRUPTED.name
)
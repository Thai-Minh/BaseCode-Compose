package com.looper.base.screen.download_test.data

import com.example.all.downloader.manager.data.DownloadInfoItem
import com.looper.base.repository.download.data.DownloadItem

enum class CatcherResultType {
    OpenCountManager,
    Dismiss,
    DownloadError,
}

sealed class CatcherResult {
    data class Preview(val downloadItem: DownloadItem) : CatcherResult()

    data class DownloadComplete(val downloadInfoItem: DownloadInfoItem) : CatcherResult()

    data class YoutubeDLDownloadComplete(val id: String) : CatcherResult()

    data class Status(val type: CatcherResultType) : CatcherResult()
}
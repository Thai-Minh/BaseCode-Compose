package com.looper.base.repository.download.data

import android.net.Uri

sealed class DownloadState {
    data object Checking : DownloadState()
    data object NotDownloaded : DownloadState()
    data object Downloading : DownloadState()
    data class Downloaded(val uri: Uri) : DownloadState()
    data class Failed(val throwable: Throwable) : DownloadState()
}

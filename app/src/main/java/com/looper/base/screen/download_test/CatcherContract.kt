package com.looper.base.screen.download_test

import com.looper.base.repository.youtubedl.data.YoutubeDLDownloadItem
import com.looper.base.repository.youtubedl.data.YoutubeDLResolvedAudio
import com.looper.base.screen.download_test.data.CatcherResult
import com.looper.base.screen.download_test.data.CatcherPayload

sealed interface CatcherIntent {
    data class UrlChanged(val url: String) : CatcherIntent
    data object Catch : CatcherIntent
    data object Download : CatcherIntent
    data object CancelDownload : CatcherIntent
    data object Dismiss : CatcherIntent
}

enum class CatcherPhase {
    IDLE, RESOLVING, READY, STARTING_DOWNLOAD, DOWNLOADING, CANCELING, COMPLETED
}

data class CatcherUiState(
    val url: String = "",
    val isConnected: Boolean = false,
    val phase: CatcherPhase = CatcherPhase.IDLE,
    val payload: CatcherPayload.YoutubeDL? = null,
    val outputName: String = "",
    val taskId: String? = null,
    val download: YoutubeDLDownloadItem? = null,
    val error: String? = null
) {
    val audio: YoutubeDLResolvedAudio?
        get() = payload?.item
    val isBusy: Boolean
        get() = when (phase) {
            CatcherPhase.RESOLVING, CatcherPhase.STARTING_DOWNLOAD,
            CatcherPhase.DOWNLOADING, CatcherPhase.CANCELING -> true
            else -> false
        }
    val canCatch: Boolean
        get() = url.isNotBlank() && isConnected && !isBusy
    val canDownload: Boolean
        get() = audio != null && isConnected && phase == CatcherPhase.READY
}

sealed interface CatcherEffect {
    data class Result(val result: CatcherResult) : CatcherEffect
}

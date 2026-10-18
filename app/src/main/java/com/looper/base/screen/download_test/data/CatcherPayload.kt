package com.looper.base.screen.download_test.data

import com.looper.all.catcher.data.CatchItem
import com.looper.base.repository.youtubedl.data.YoutubeDLResolvedAudio

sealed interface CatcherPayload {
    val stableId: String

    data class DownloaderCore(val item: CatchItem) : CatcherPayload {
        override val stableId: String = "core:${item.id}"
    }

    data class YoutubeDL(val item: YoutubeDLResolvedAudio) : CatcherPayload {
        override val stableId: String = "ytdl:${item.id}:${item.sourceUrl.hashCode()}"
    }
}

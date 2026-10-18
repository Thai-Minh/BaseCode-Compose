package com.looper.base.repository.download

import androidx.activity.ComponentActivity
import com.example.all.downloader.manager.IDownloaderManager
import com.example.all.downloader.manager.data.MediaType
import com.looper.ads.AdsManager

val MediaType.isVideo
    get() = this == MediaType.VIDEO_FFMPEG || this == MediaType.VIDEO

val MediaType.isAudio
    get() = this == MediaType.AUDIO

val MediaType.isImage
    get() = this == MediaType.IMAGE

suspend fun updateDownloadDB(downloader: IDownloaderManager, id: String, isLocked: Boolean) {
    downloader.updateLockedDB(id = id, isLocked = isLocked)
}

suspend fun updateDownloadDB(downloader: IDownloaderManager, ids: List<String>, isLocked: Boolean) {
    ids.forEach {
        updateDownloadDB(downloader = downloader, id = it, isLocked = isLocked)
    }
}

fun deleteFileNormal(
    activity: ComponentActivity,
    downloader: IDownloaderManager,
    ids: List<String>
) {
    AdsManager.ignoreShowAppOpenAdOnetime()

    downloader.deleteWithId(activity = activity, ids = ids)
}

fun deleteFileNormal(
    activity: ComponentActivity,
    downloader: IDownloaderManager,
    id: String
) {
    AdsManager.ignoreShowAppOpenAdOnetime()

    downloader.deleteWithId(activity = activity, id = id)
}
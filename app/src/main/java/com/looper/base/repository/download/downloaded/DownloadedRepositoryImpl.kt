package com.looper.videodownloader.repository.download.downloaded

import androidx.activity.ComponentActivity
import com.example.all.downloader.manager.IDownloaderManager
import com.example.all.downloader.manager.data.DownloadStatus
import com.example.all.downloader.manager.data.MediaType
import com.looper.base.repository.download.IDownloadFileRepository
import com.looper.videodownloader.repository.download.data.DownloadItem
import com.looper.videodownloader.repository.download.data.toDownloadedItem
import com.looper.videodownloader.repository.download.deleteFileNormal
import com.looper.videodownloader.repository.download.isAudio
import com.looper.videodownloader.repository.download.isImage
import com.looper.videodownloader.repository.download.isVideo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.File

class DownloadedAudioRepository(
    downloader: IDownloaderManager,
) : IDownloadedRepository(downloader) {

    override val mediaType: MediaType
        get() = MediaType.AUDIO
}

class DownloadedImageRepository(
    downloader: IDownloaderManager
) : IDownloadedRepository(downloader) {

    override val mediaType: MediaType
        get() = MediaType.IMAGE
}

class DownloadedVideoRepository(
    downloader: IDownloaderManager
) : IDownloadedRepository(downloader) {

    override val mediaType: MediaType
        get() = MediaType.VIDEO
}

open class IDownloadedRepository internal constructor(
    downloader: IDownloaderManager
) : IDownloadFileRepository(downloader) {

    open val mediaType: MediaType
        get() {
            TODO()
        }

    private val _items = downloader.downloadInfoItems.map { downloadInfoItems ->
        downloadInfoItems.mapNotNull {
            val acceptType = when (mediaType) {
                MediaType.IMAGE -> it.mediaType.isImage
                MediaType.AUDIO -> it.mediaType.isAudio
                else -> it.mediaType.isVideo
            }

            if (it.status == DownloadStatus.COMPLETED && !it.isLocked && acceptType)
                it.toDownloadedItem()
            else
                null
        }
    }

    override val items: Flow<List<DownloadItem>> = _items

    override fun delete(activity: ComponentActivity, id: String) {
        deleteFileNormal(activity = activity, downloader = downloader, id = id)
    }

    override fun delete(activity: ComponentActivity, ids: List<String>) {
        deleteFileNormal(activity = activity, downloader = downloader, ids = ids)
    }

    suspend fun removeDatabaseRecordByPath(path: String) {
        val normalizedPath = path.normalizedPath()
        val matchingIds = downloader.downloadInfoItems.first()
            .filter { item ->
                item.outputPath?.normalizedPath() == normalizedPath
            }
            .map { it.id }

        if (matchingIds.isNotEmpty()) {
            downloader.removeDB(matchingIds)
        }
    }

    private fun String.normalizedPath(): String = File(this).absoluteFile.normalize().path
}
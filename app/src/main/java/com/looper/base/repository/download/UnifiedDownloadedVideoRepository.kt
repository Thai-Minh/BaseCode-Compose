package com.looper.base.repository.download

import androidx.activity.ComponentActivity
import com.example.all.downloader.manager.data.MediaType
import com.example.all.downloader.manager.data.Platform
import com.looper.base.repository.download.data.DownloadBackend
import com.looper.base.repository.download.data.DownloadItem
import com.looper.base.repository.download.downloaded.IDownloadedRepository
import com.looper.base.repository.youtubedl.YoutubeDLDownloadRepository
import com.looper.base.repository.youtubedl.data.YoutubeDLDownloadStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

interface UnifiedDownloadedVideoRepository {
    val items: Flow<List<DownloadItem.DownloadedItem>>
    suspend fun delete(activity: ComponentActivity, compositeId: String)
}

class UnifiedDownloadedVideoRepositoryImpl(
    private val core: IDownloadedRepository,
    private val youtubeDL: YoutubeDLDownloadRepository
) : UnifiedDownloadedVideoRepository {
    override val items = combine(core.items, youtubeDL.items) { coreItems, youtubeItems ->
        val downloaderCore = coreItems.filterIsInstance<DownloadItem.DownloadedItem>()
            .map {
                it.copy(backend = DownloadBackend.DOWNLOADER_CORE)
            }

        val fallback =
            youtubeItems.filter {
                it.status == YoutubeDLDownloadStatus.COMPLETED && it.location != null
            }
                .map { item ->
                    DownloadItem.DownloadedItem(
                        downloadId = item.id,
                        outputPath = requireNotNull(item.location),
                        outputFileName = item.displayName,
                        title = item.title,
                        description = item.ownerName,
                        durationMs = item.durationMs,
                        platform = Platform(item.extractor.lowercase(), ""),
                        mediaType = if (item.displayName.endsWith(".mp3", ignoreCase = true)) {
                            MediaType.AUDIO
                        } else {
                            MediaType.VIDEO
                        },
                        size = item.size,
                        lastModified = item.lastModified,
                        recentTime = null,
                        backend = DownloadBackend.YOUTUBE_DL
                    )
                }

        (downloaderCore + fallback).sortedByDescending {
            it.lastModified
        }
    }

    override suspend fun delete(activity: ComponentActivity, compositeId: String) {
        val (backend, id) = DownloadBackend.parseCompositeId(compositeId)

        when (backend) {
            DownloadBackend.DOWNLOADER_CORE -> core.delete(activity, id)
            DownloadBackend.YOUTUBE_DL -> youtubeDL.delete(id)
        }
    }
}

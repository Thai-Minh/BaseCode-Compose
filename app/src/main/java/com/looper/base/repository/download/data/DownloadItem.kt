package com.looper.videodownloader.repository.download.data

import com.example.all.downloader.manager.data.DownloadInfoItem
import com.example.all.downloader.manager.data.DownloadStatus
import com.example.all.downloader.manager.data.MediaType
import com.example.all.downloader.manager.data.Platform
import com.example.all.downloader.manager.data.platformIcon
import java.io.File

enum class DownloadBackend(val prefix: String) {
    DOWNLOADER_CORE("core"), YOUTUBE_DL("ytdl");

    companion object {
        fun parseCompositeId(value: String): Pair<DownloadBackend, String> {
            val prefix = value.substringBefore(':')
            val backend = entries.firstOrNull { it.prefix == prefix } ?: DOWNLOADER_CORE
            return backend to value.substringAfter(':', value)
        }
    }
}

sealed class DownloadItem {
    data class DownloadingItem(
        val downloadInfo: DownloadInfoItem,
    ) : DownloadItem()

    data class DownloadedItem(
        val downloadId: String,
        val outputPath: String,
        val outputFileName: String,
        val title: String?,
        val description: String?,
        val durationMs: Long?,
        val platform: Platform,
        val mediaType: MediaType,
        val size: Long,
        val lastModified: Long,
        val recentTime: Long?,
        val backend: DownloadBackend = DownloadBackend.DOWNLOADER_CORE
    ) : DownloadItem() {
        val compositeId: String get() = "${backend.prefix}:$downloadId"
        constructor(
            downloadId: String,
            outputPath: String,
            outputFileName: String,
            title: String?,
            description: String?,
            durationMs: Long?,
            platform: Platform,
            mediaType: MediaType,
            recentTime: Long?
        ) : this(
            downloadId = downloadId,
            outputPath = outputPath,
            outputFileName = outputFileName,
            title = title,
            description = description,
            durationMs = durationMs,
            platform = platform,
            mediaType = mediaType,
            size = outputPath.locationSize,
            lastModified = outputPath.locationLastModified,
            recentTime = recentTime,
            backend = DownloadBackend.DOWNLOADER_CORE
        )

        constructor(
            downloadId: String,
            outputPath: String,
            outputFileName: String,
            title: String?,
            description: String?,
            durationMs: Long?,
            platform: Platform,
            mediaType: MediaType,
            lastModified: Long,
            recentTime: Long?
        ) : this(
            downloadId = downloadId,
            outputPath = outputPath,
            outputFileName = outputFileName,
            title = title,
            description = description,
            durationMs = durationMs,
            platform = platform,
            mediaType = mediaType,
            size = outputPath.locationSize,
            lastModified = lastModified,
            recentTime = recentTime,
            backend = DownloadBackend.DOWNLOADER_CORE
        )
    }
}

val DownloadItem.DownloadingItem.downloadId: String get() = downloadInfo.id

val DownloadItem.DownloadingItem.outputPath: String? get() = downloadInfo.outputPath

val DownloadItem.DownloadingItem.outputFileName: String get() = downloadInfo.outputFileName

val DownloadItem.DownloadingItem.title: String? get() = downloadInfo.caption

val DownloadItem.DownloadingItem.description: String? get() = downloadInfo.owner

val DownloadItem.DownloadingItem.thumbnailUrl: String? get() = downloadInfo.thumbnailUrl

val DownloadItem.DownloadingItem.durationMs: Long? get() = downloadInfo.durationMs

val DownloadItem.DownloadingItem.startTime: Long get() = downloadInfo.startTime

val DownloadItem.DownloadingItem.status: DownloadStatus get() = downloadInfo.status

val DownloadItem.DownloadingItem.platform: Platform
    get() = Platform(
        id = downloadInfo.platform,
        iconUrl = downloadInfo.platformIcon
    )

val DownloadItem.DownloadingItem.mediaType: MediaType get() = downloadInfo.mediaType

val DownloadItem.DownloadingItem.isLocked: Boolean get() = downloadInfo.isLocked

val DownloadItem.DownloadingItem.progress: Int get() = downloadInfo.progress

val DownloadItem.id: String
    get() = when (this) {
        is DownloadItem.DownloadingItem -> downloadId
        is DownloadItem.DownloadedItem -> downloadId
    }

val DownloadItem.outputFileName: String
    get() = when (this) {
        is DownloadItem.DownloadingItem -> outputFileName
        is DownloadItem.DownloadedItem -> outputFileName
    }

val DownloadItem.title: String?
    get() = when (this) {
        is DownloadItem.DownloadingItem -> title
        is DownloadItem.DownloadedItem -> title
    }

val DownloadItem.description: String?
    get() = when (this) {
        is DownloadItem.DownloadingItem -> description
        is DownloadItem.DownloadedItem -> description
    }

val DownloadItem.platform: Platform
    get() = when (this) {
        is DownloadItem.DownloadingItem -> platform
        is DownloadItem.DownloadedItem -> platform
    }

val DownloadItem.mediaType: MediaType
    get() = when (this) {
        is DownloadItem.DownloadingItem -> mediaType
        is DownloadItem.DownloadedItem -> mediaType
    }

val DownloadItem.size: Long?
    get() = when (this) {
        is DownloadItem.DownloadingItem -> null
        is DownloadItem.DownloadedItem -> size
    }

val DownloadItem.path: String?
    get() = when (this) {
        is DownloadItem.DownloadingItem -> outputPath
        is DownloadItem.DownloadedItem -> outputPath
    }

val DownloadItem.lastModified: Long
    get() = when (this) {
        is DownloadItem.DownloadingItem -> startTime
        is DownloadItem.DownloadedItem -> lastModified
    }

val DownloadItem.status: DownloadStatus
    get() = when (this) {
        is DownloadItem.DownloadingItem -> status
        is DownloadItem.DownloadedItem -> DownloadStatus.COMPLETED
    }

val DownloadItem.thumbnail: String?
    get() = when (this) {
        is DownloadItem.DownloadingItem -> thumbnailUrl
        is DownloadItem.DownloadedItem -> null
    }

val DownloadItem.durationMs: Long?
    get() = when (this) {
        is DownloadItem.DownloadingItem -> durationMs
        is DownloadItem.DownloadedItem -> durationMs
    }

val DownloadItem.recentTime: Long?
    get() = when (this) {
        is DownloadItem.DownloadingItem -> null
        is DownloadItem.DownloadedItem -> recentTime
    }

fun DownloadInfoItem.toDownloadingItem(): DownloadItem.DownloadingItem {
    return DownloadItem.DownloadingItem(
        downloadInfo = this
    )
}

fun DownloadInfoItem.toDownloadedItem(): DownloadItem.DownloadedItem {
    return DownloadItem.DownloadedItem(
        downloadId = id,
        outputPath = outputPath ?: "",
        outputFileName = outputFileName,
        title = caption,
        description = owner,
        durationMs = durationMs,
        platform = Platform(id = platform, iconUrl = platformIcon),
        mediaType = mediaType,
        recentTime = null
    )
}

val String.locationSize: Long
    get() = if (startsWith("content://")) 0L else File(this).takeIf { it.isFile }?.length() ?: 0L

val String.locationLastModified: Long
    get() = if (startsWith("content://")) 0L else File(this).takeIf { it.isFile }?.lastModified() ?: 0L

val String.size: Long get() = locationSize
val String.lastModified: Long get() = locationLastModified
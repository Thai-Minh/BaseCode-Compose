package com.looper.base.screen.download_test.data

import com.example.all.downloader.manager.data.Platform
import com.looper.all.catcher.data.CatchMedia
import com.looper.all.catcher.data.id
import com.looper.all.catcher.data.name
import com.looper.base.repository.download.data.DownloadItem
import com.looper.base.repository.download.data.id
import com.looper.base.repository.download.data.path
import com.looper.base.repository.youtubedl.data.YoutubeDLDownloadItem

data class BrowserCatchItem(
    val id: String,
    val title: String?,
    val ownerName: String?,
    val ownerCoverUrl: String?,
    val thumbnailUrl: String?,
    val duration: Long,
    val childMediaItems: List<ChildMediaItem>,
    val platform: Platform,
    val sourceUrl: String = "",
)

sealed class ChildMediaItem(
    open val media: CatchMedia,
    open val isExpanded: Boolean = false,
    open val isLastGroup: Boolean = false,
    open val isFirstGroup: Boolean = false,
    open val isHighQuality: Boolean = false,
    open val isBestQuality: Boolean = false
)

data class ChildCatchItem(
    override val media: CatchMedia,
    override val isExpanded: Boolean = false,
    override val isLastGroup: Boolean = false,
    override val isFirstGroup: Boolean = false,
    override val isHighQuality: Boolean = false,
    override val isBestQuality: Boolean = false
) : ChildMediaItem(
    media = media,
    isExpanded = isExpanded,
    isLastGroup = isLastGroup
)

data class ChildDownloadingItem(
    override val media: CatchMedia,
    val data: DownloadItem.DownloadingItem,
    override val isExpanded: Boolean = false,
    override val isLastGroup: Boolean = false,
    override val isFirstGroup: Boolean = false,
    override val isHighQuality: Boolean = false,
    override val isBestQuality: Boolean = false
) : ChildMediaItem(
    media = media,
    isExpanded = isExpanded,
    isLastGroup = isLastGroup
)

data class ChildYoutubeDLDownloadingItem(
    override val media: CatchMedia,
    val data: YoutubeDLDownloadItem,
    override val isExpanded: Boolean = false,
    override val isLastGroup: Boolean = false,
    override val isFirstGroup: Boolean = false,
    override val isHighQuality: Boolean = false,
    override val isBestQuality: Boolean = false
) : ChildMediaItem(media, isExpanded, isLastGroup, isFirstGroup, isHighQuality, isBestQuality)

data class ChildYoutubeDLDownloadedItem(
    override val media: CatchMedia,
    val data: YoutubeDLDownloadItem,
    override val isExpanded: Boolean = false,
    override val isLastGroup: Boolean = false,
    override val isFirstGroup: Boolean = false,
    override val isHighQuality: Boolean = false,
    override val isBestQuality: Boolean = false
) : ChildMediaItem(media, isExpanded, isLastGroup, isFirstGroup, isHighQuality, isBestQuality)

data class ChildDownloadedItem(
    override val media: CatchMedia,
    val data: DownloadItem.DownloadedItem,
    override val isExpanded: Boolean = false,
    override val isLastGroup: Boolean = false,
    override val isFirstGroup: Boolean = false,
    override val isHighQuality: Boolean = false,
    override val isBestQuality: Boolean = false
) : ChildMediaItem(
    media = media,
    isExpanded = isExpanded,
    isLastGroup = isLastGroup
)

val ChildMediaItem.id: String
    get() {
        return when (this) {
            is ChildCatchItem -> media.id
            is ChildDownloadedItem -> data.id
            is ChildDownloadingItem -> data.id
            is ChildYoutubeDLDownloadingItem -> media.id
            is ChildYoutubeDLDownloadedItem -> media.id
        }
    }

val ChildMediaItem.name: String
    get() {
        return when (this) {
            is ChildCatchItem -> media.name
            is ChildDownloadedItem -> media.name
            is ChildDownloadingItem -> media.name
            is ChildYoutubeDLDownloadingItem -> media.name
            is ChildYoutubeDLDownloadedItem -> media.name
        }
    }

val ChildMediaItem.path: String?
    get() {
        return when (this) {
            is ChildCatchItem -> null
            is ChildDownloadedItem -> data.path
            is ChildDownloadingItem -> data.path
            is ChildYoutubeDLDownloadingItem -> data.location
            is ChildYoutubeDLDownloadedItem -> data.location
        }
    }

val ChildMediaItem.isShowTagAds: Boolean
    get() {
        return when (this) {
            is ChildCatchItem -> isBestQuality
            is ChildDownloadedItem -> false
            is ChildDownloadingItem -> false
            is ChildYoutubeDLDownloadingItem -> false
            is ChildYoutubeDLDownloadedItem -> false
        }
    }
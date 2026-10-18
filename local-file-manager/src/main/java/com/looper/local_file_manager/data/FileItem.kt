package com.looper.local_file_manager.data

data class MediaItem(
    val path: String,
    val name: String,
    val size: Long,
    val lastModified: Long,
    val duration: Long? = null,
    val recentTime: Long? = null,
    val contentUri: String? = null,
)

sealed class FileItem(
    open val state: FileManagerState,
    open val list: List<MediaItem>
) {
    data class Video(
        override val state: FileManagerState,
        override val list: List<MediaItem> = emptyList()
    ) : FileItem(state, list)

    data class Audio(
        override val state: FileManagerState,
        override val list: List<MediaItem> = emptyList()
    ) : FileItem(state, list)

    data class Image(
        override val state: FileManagerState,
        override val list: List<MediaItem> = emptyList()
    ) : FileItem(state, list)
}
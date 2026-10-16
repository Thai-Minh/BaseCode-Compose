package com.looper.videodownloader.repository.video_local.data

import com.looper.local_file_manager.data.FileManagerState
import com.looper.local_file_manager.data.MediaItem

data class LocalVideoItem(
    val state: FileManagerState,
    val videos: List<MediaItem>
)

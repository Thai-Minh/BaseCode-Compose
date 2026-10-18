package com.looper.base.repository.audio_local.data

import com.looper.local_file_manager.data.FileManagerState
import com.looper.local_file_manager.data.MediaItem

data class LocalVideoItem(
    val state: FileManagerState,
    val audio: List<MediaItem>
)

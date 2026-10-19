package com.looper.base.repository.audio_local.data

import com.looper.local_file_manager.data.FileManagerState

data class LocalAudioItem(
    val state: FileManagerState,
    val audio: List<DeviceAudio>,
)

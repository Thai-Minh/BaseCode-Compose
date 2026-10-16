package com.looper.base.audio.model

import android.net.Uri

data class DeviceAudio(
    val id: Long,
    val name: String,
    val artist: String,
    val uri: Uri,
    val artworkUri: Uri?,
    val durationMs: Long,
)

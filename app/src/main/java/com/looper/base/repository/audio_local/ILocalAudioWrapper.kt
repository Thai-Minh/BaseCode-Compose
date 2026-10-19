package com.looper.base.repository.audio_local

import androidx.activity.ComponentActivity
import com.looper.base.repository.audio_local.data.LocalAudioItem
import kotlinx.coroutines.flow.Flow

interface ILocalAudioWrapper {

    val localAudio: Flow<LocalAudioItem>

    fun fetch()

    suspend fun getDuration(path: String): Long?

    suspend fun deleteFile(activity: ComponentActivity, path: String): Boolean

    suspend fun deleteFiles(activity: ComponentActivity, paths: List<String>): Boolean
}

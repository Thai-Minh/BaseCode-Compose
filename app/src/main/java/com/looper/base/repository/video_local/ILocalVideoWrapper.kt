package com.looper.videodownloader.repository.video_local

import androidx.activity.ComponentActivity
import com.looper.videodownloader.repository.video_local.data.LocalVideoItem
import kotlinx.coroutines.flow.Flow

interface ILocalVideoWrapper {

    val localVideo: Flow<LocalVideoItem>

    fun fetch()

    suspend fun getDuration(path: String): Long?

    suspend fun deleteFile(activity: ComponentActivity, path: String): Boolean

    suspend fun deleteFiles(activity: ComponentActivity, paths: List<String>): Boolean
}
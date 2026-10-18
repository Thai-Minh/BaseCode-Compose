package com.looper.base.repository.audio_local

import androidx.activity.ComponentActivity
import com.looper.ads.AdsManager
import com.looper.local_file_manager.ILocalFileManager
import com.looper.local_file_manager.data.FileManagerState
import com.looper.base.repository.audio_local.data.LocalVideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class LocalAudioWrapperImpl(
    private val localFileManager: ILocalFileManager
): ILocalAudioWrapper {

    private val scope = MainScope()

    override val localAudio: Flow<LocalVideoItem> = localFileManager.videoFileItem.distinctUntilChanged()
        .map { item ->
            LocalVideoItem(state = item.state, audio = item.list)
        }
        .flowOn(Dispatchers.IO)
        .stateIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = LocalVideoItem(state = FileManagerState.Loading, audio = emptyList())
        )

    override fun fetch() = localFileManager.fetch()

    override suspend fun getDuration(path: String): Long? = localFileManager.getDuration(path = path)

    override suspend fun deleteFile(
        activity: ComponentActivity,
        path: String
    ): Boolean {
        AdsManager.ignoreShowAppOpenAdOnetime()

        return localFileManager.deleteFile(activity = activity, path = path)
    }

    override suspend fun deleteFiles(
        activity: ComponentActivity,
        paths: List<String>
    ): Boolean {
        AdsManager.ignoreShowAppOpenAdOnetime()

        return localFileManager.deleteFiles(activity = activity, paths = paths)
    }
}
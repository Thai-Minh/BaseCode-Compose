package com.looper.base.screen.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.looper.base.repository.audio_local.data.DeviceAudio
import com.looper.base.service.AudioPlaybackConnection
import com.looper.base.repository.audio_local.ILocalAudioWrapper
import com.looper.local_file_manager.data.FileManagerState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn

class MainViewModel(
    private val localAudioWrapper: ILocalAudioWrapper,
    private val playback: AudioPlaybackConnection,
) : ViewModel() {
    private val localAudio = localAudioWrapper.localAudio.shareIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(replayExpirationMillis = 0),
        replay = 1,
    )

    val tabState: Flow<FileManagerState> = localAudio
        .map { it.state }
        .distinctUntilChanged()

    val audioItems: Flow<List<DeviceAudio>> = localAudio
        .map { it.audio }
        .distinctUntilChanged()

    val playbackState = playback.state

    init {
        playback.connect()
    }

    fun refresh() = localAudioWrapper.fetch()

    fun reconnect() = playback.connect()
    fun play(audio: List<DeviceAudio>, selected: DeviceAudio) = playback.play(audio, selected)
    fun togglePlayPause() = playback.togglePlayPause()
    fun previous() = playback.previous()
    fun next() = playback.next()

    override fun onCleared() {
        playback.release()
    }
}

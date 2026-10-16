package com.looper.base.screen.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.looper.base.R
import com.looper.base.audio.model.DeviceAudio
import com.looper.base.audio.playback.AudioPlaybackConnection
import com.looper.base.audio.repository.AudioRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class MainUiState(
    val hasAudioPermission: Boolean = false,
    val isLoading: Boolean = false,
    val audio: List<DeviceAudio> = emptyList(),
    val error: Int? = null,
)

class MainViewModel(
    private val repository: AudioRepository,
    private val playback: AudioPlaybackConnection,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(MainUiState())
    val uiState = mutableUiState.asStateFlow()
    val playbackState = playback.state
    private var loadJob: Job? = null

    init {
        playback.connect()
    }

    fun onPermissionChanged(granted: Boolean) {
        loadJob?.cancel()
        mutableUiState.value = if (granted) {
            mutableUiState.value.copy(hasAudioPermission = true)
        } else {
            MainUiState()
        }
        if (granted) refresh()
    }

    fun refresh() {
        if (!mutableUiState.value.hasAudioPermission) return
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            mutableUiState.value = mutableUiState.value.copy(isLoading = true, error = null)
            try {
                val audio = repository.getDeviceAudio()
                mutableUiState.value = mutableUiState.value.copy(audio = audio, isLoading = false)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: SecurityException) {
                mutableUiState.value = MainUiState()
            } catch (_: Exception) {
                mutableUiState.value = mutableUiState.value.copy(
                    isLoading = false, error = R.string.audio_load_error
                )
            }
        }
    }

    fun reconnect() = playback.connect()
    fun play(audio: DeviceAudio) = playback.play(mutableUiState.value.audio, audio)
    fun togglePlayPause() = playback.togglePlayPause()
    fun previous() = playback.previous()
    fun next() = playback.next()

    override fun onCleared() {
        playback.release()
    }
}

package com.looper.base.screen.download_test

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.looper.base.repository.youtubedl.YoutubeDLDownloadRepository
import com.looper.base.repository.youtubedl.YoutubeDLOutputNaming
import com.looper.base.repository.youtubedl.YoutubeDLVideoResolver
import com.looper.base.repository.youtubedl.data.YoutubeDLDownloadItem
import com.looper.base.repository.youtubedl.data.YoutubeDLDownloadStatus
import com.looper.base.screen.download_test.data.CatcherResult
import com.looper.base.screen.download_test.data.CatcherResultType
import com.looper.base.screen.download_test.data.CatcherPayload
import com.looper.base.utils.NetworkManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.net.URI
import kotlin.time.Duration.Companion.milliseconds

class CatcherViewModel(
    private val youtubeDLRepository: YoutubeDLDownloadRepository,
    private val resolver: YoutubeDLVideoResolver,
    networkManager: NetworkManager
) : ViewModel() {
    private val _uiState = MutableStateFlow(CatcherUiState())
    val uiState = _uiState.asStateFlow()
    private val effectChannel = Channel<CatcherEffect>(Channel.BUFFERED)
    val effects = effectChannel.receiveAsFlow()
    private var operationJob: Job? = null
    private var observationJob: Job? = null
    private var needsFreshResolution = false

    init {
        viewModelScope.launch {
            networkManager.isConnected.distinctUntilChanged().catch { emit(false) }.collect { connected ->
                _uiState.update { it.copy(isConnected = connected) }
            }
        }
    }

    fun onIntent(intent: CatcherIntent) {
        when (intent) {
            is CatcherIntent.UrlChanged -> if (!_uiState.value.isBusy) {
                if (intent.url != _uiState.value.url) reset()
                _uiState.update { it.copy(url = intent.url, error = null) }
            }
            CatcherIntent.Catch -> catchAudio()
            CatcherIntent.Download -> downloadAudio()
            CatcherIntent.CancelDownload -> cancelDownload()
            CatcherIntent.Dismiss -> if (!_uiState.value.isBusy) {
                reset()
                effectChannel.trySend(CatcherEffect.Result(CatcherResult.Status(CatcherResultType.Dismiss)))
            }
        }
    }

    private fun catchAudio() {
        val state = _uiState.value
        if (!state.canCatch || operationJob?.isActive == true) return
        reset()
        val input = state.url.trim()
        val uri = runCatching { URI(input) }.getOrNull()
        if (uri?.scheme?.lowercase() !in listOf("http", "https") || uri?.host.isNullOrBlank()) {
            _uiState.update { it.copy(error = "Enter a valid HTTP or HTTPS URL") }
            return
        }
        _uiState.update { it.copy(phase = CatcherPhase.RESOLVING) }
        operationJob = viewModelScope.launch {
            try {
                val audio = resolver.resolveV2(input)
                _uiState.update {
                    it.copy(
                        phase = CatcherPhase.READY,
                        payload = CatcherPayload.YoutubeDL(audio),
                        outputName = YoutubeDLOutputNaming.finalAudioName("${audio.title}.mp3")
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _uiState.update { it.copy(phase = CatcherPhase.IDLE, error = error.message ?: "Unable to resolve audio") }
            }
        }
    }

    private fun downloadAudio() {
        val state = _uiState.value
        if (!state.canDownload || operationJob?.isActive == true) return
        val source = state.payload ?: return
        val audio = source.item
        observationJob?.cancel()
        _uiState.update {
            it.copy(phase = CatcherPhase.STARTING_DOWNLOAD, taskId = null, download = null, error = null)
        }
        operationJob = viewModelScope.launch {
            try {
                // Failure/cancellation cleanup removes cookies and temporary metadata owned by the previous task.
                val downloadAudio = if (needsFreshResolution) resolver.resolveV2(audio.sourceUrl) else audio
                needsFreshResolution = true
                val id = youtubeDLRepository.enqueue(downloadAudio, state.outputName)
                _uiState.update {
                    it.copy(phase = CatcherPhase.DOWNLOADING, payload = CatcherPayload.YoutubeDL(downloadAudio), taskId = id)
                }
                observeDownload(id)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                failDownload(error.message ?: "Unable to download audio")
            }
        }
    }

    private fun observeDownload(id: String) {
        observationJob = viewModelScope.launch {
            try {
                youtubeDLRepository.getItem(id).retryWhen { error, attempt ->
                    if (error is CancellationException) throw error
                    _uiState.update { it.copy(error = error.message ?: "Unable to observe download. Retrying...") }
                    delay(((attempt + 1).coerceAtMost(5) * 1_000).milliseconds)
                    true
                }.distinctUntilChanged().collect { task ->
                    updateDownload(id, task)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                // An observation failure does not imply the foreground download has stopped.
                _uiState.update { it.copy(error = error.message ?: "Unable to observe download") }
            }
        }
    }

    private suspend fun updateDownload(id: String, task: YoutubeDLDownloadItem?) {
        if (_uiState.value.taskId != id) return
        val previous = _uiState.value.download
        when (task?.status) {
            YoutubeDLDownloadStatus.QUEUED, YoutubeDLDownloadStatus.RUNNING -> {
                _uiState.update { it.copy(download = task, error = null) }
            }
            YoutubeDLDownloadStatus.COMPLETED -> {
                _uiState.update { it.copy(phase = CatcherPhase.COMPLETED, download = task, error = null) }
                if (previous?.status != task.status) {
                    effectChannel.send(CatcherEffect.Result(CatcherResult.YoutubeDLDownloadComplete(id)))
                }
            }
            YoutubeDLDownloadStatus.FAILED, YoutubeDLDownloadStatus.INTERRUPTED -> {
                _uiState.update { it.copy(download = task) }
                if (previous?.status != task.status) {
                    failDownload(task.error ?: "Unable to download audio. Please try again.")
                }
            }
            YoutubeDLDownloadStatus.CANCELED, null -> {
                _uiState.update {
                    it.copy(phase = CatcherPhase.READY, taskId = null, download = null, error = null)
                }
            }
        }
    }

    private fun cancelDownload() {
        val state = _uiState.value
        if (state.phase != CatcherPhase.DOWNLOADING || operationJob?.isActive == true) return
        val id = state.taskId ?: return
        observationJob?.cancel()
        _uiState.update { it.copy(phase = CatcherPhase.CANCELING, error = null) }
        operationJob = viewModelScope.launch {
            try {
                youtubeDLRepository.cancel(id)
                val terminalTask = withTimeout(15_000.milliseconds) {
                    youtubeDLRepository.getItem(id).first { task ->
                        task == null || task.status !in listOf(YoutubeDLDownloadStatus.QUEUED, YoutubeDLDownloadStatus.RUNNING)
                    }
                }
                updateDownload(id, terminalTask)
            } catch (error: kotlinx.coroutines.TimeoutCancellationException) {
                cancelFailed(id, "Cancellation was not confirmed. Please try again.")
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                cancelFailed(id, error.message ?: "Unable to cancel download")
            }
        }
    }

    private fun cancelFailed(id: String, message: String) {
        _uiState.update {
            if (it.taskId == id && it.phase == CatcherPhase.CANCELING) {
                it.copy(phase = CatcherPhase.DOWNLOADING, error = message)
            } else it
        }
        if (_uiState.value.taskId == id) observeDownload(id)
    }

    private suspend fun failDownload(message: String) {
        _uiState.update { it.copy(phase = CatcherPhase.READY, error = message) }
        effectChannel.send(CatcherEffect.Result(CatcherResult.Status(CatcherResultType.DownloadError)))
    }

    private fun reset() {
        observationJob?.cancel()
        needsFreshResolution = false
        _uiState.update { CatcherUiState(url = it.url, isConnected = it.isConnected) }
    }
}

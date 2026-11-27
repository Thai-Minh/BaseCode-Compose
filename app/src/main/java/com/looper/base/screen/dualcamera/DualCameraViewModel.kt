package com.looper.base.screen.dualcamera

import android.os.SystemClock
import android.util.Log
import androidx.camera.core.SurfaceRequest
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.looper.base.R
import com.looper.base.repository.dualcamera.IDualCameraRepository
import com.looper.base.screen.common.data.DualLayout
import com.looper.base.screen.common.data.PipPosition
import com.looper.base.screen.dualcamera.data.DualCameraEffect
import com.looper.base.screen.dualcamera.data.DualCameraSupport
import com.looper.base.screen.dualcamera.data.DualCameraUiState
import com.looper.base.screen.dualcamera.data.DualCaptureMode
import com.looper.base.screen.dualcamera.data.PreviewLayout
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val TAG = "DualCameraViewModel"

/**
 * false: always use the Camera2 pipeline (Camera2Viewfinder + DualVideoRecorder), which renders
 * every [DualLayout] identically in preview, photo and
 * video. On devices with concurrent camera support both cameras stream with it too.
 * true: use CameraX ConcurrentCamera when supported; its composition video can only offset /
 * scale the second camera, so videos won't match the selected layout.
 */
private const val USE_CAMERAX_CONCURRENT = false

/**
 * Dual camera screen state. The cameras, captures, recordings and saving are done by
 * [IDualCameraRepository]; this class decides what to do and when, and shows it in [uiState].
 */
class DualCameraViewModel(
    private val repository: IDualCameraRepository,
) : ViewModel() {

    val uiState: StateFlow<DualCameraUiState>
        field = MutableStateFlow(DualCameraUiState())

    val effects: SharedFlow<DualCameraEffect>
        field = MutableSharedFlow(extraBufferCapacity = 1)

    /** Back camera preview (photo mode) or the composited PiP preview (video mode). */
    val primarySurfaceRequest: StateFlow<SurfaceRequest?> = repository.primarySurfaceRequest

    /** Front camera preview, only used in photo mode. */
    val secondarySurfaceRequest: StateFlow<SurfaceRequest?> = repository.secondarySurfaceRequest

    /** Still capture / recording handles for the Camera2 fallback viewfinders. */
    val camera2Back: Camera2CameraHandle = repository.camera2Back
    val camera2Front: Camera2CameraHandle = repository.camera2Front

    /** Ticks the Camera2 recording's duration, null when idle. */
    private var camera2DurationJob: Job? = null

    /** Guards against an old session's cleanup tearing down a newer binding. */
    private var bindSessionId = 0

    /** Record button was pressed: start recording as soon as the Video binding is ready. */
    private var pendingRecord = false

    /** Latest on-screen preview geometry, used to render photos exactly like the preview. */
    @Volatile
    private var previewLayout: PreviewLayout? = null

    fun onPreviewLayout(layout: PreviewLayout) {
        previewLayout = layout
    }

    /**
     * Exchanges the main and the window feed. In single-camera mode only the main camera is
     * open, so this switches it between the back and front camera instead.
     */
    fun onSwapClick() {
        if (!uiState.value.canEditLayout) return
        uiState.update { it.copy(isSwapped = !it.isSwapped) }
    }

    fun onLayoutSelected(layout: DualLayout) {
        if (!uiState.value.canEditLayout) return
        // The PiP window keeps its position across layout changes.
        uiState.update { it.copy(layout = layout) }
    }

    /** The PiP window was dragged by fractions of its free space (see [PipPosition]). */
    fun onPipDragged(dx: Float, dy: Float) {
        if (!uiState.value.canMovePip) return
        uiState.update { it.copy(pipPosition = it.pipPosition.movedBy(dx, dy)) }
    }

    fun onPhotoClick() {
        val state = uiState.value
        if (!state.isPhotoEnabled) return

        viewModelScope.launch {
            uiState.update { it.copy(isCapturing = true) }
            try {
                val photo = if (state.support == DualCameraSupport.Camera2Fallback) {
                    repository.captureCamera2Photo(
                        state.isSwapped,
                        previewLayout,
                        state.isSingleCamera,
                    )
                } else {
                    repository.captureConcurrentPhoto(state.isSwapped, previewLayout)
                }
                photo.fold(
                    onSuccess = {
                        effects.emit(DualCameraEffect.PhotoSaved(it.uri, it.isSingleCamera))
                    },
                    onFailure = { e ->
                        Log.e(TAG, "Dual photo capture failed", e)
                        effects.emit(DualCameraEffect.Error(R.string.dual_camera_capture_failed))
                    },
                )
            } finally {
                uiState.update { it.copy(isCapturing = false) }
            }
        }
    }

    fun onVideoClick() {
        val state = uiState.value
        when {
            repository.isRecording -> stopRecording()
            !state.isVideoEnabled -> Unit
            state.support == DualCameraSupport.Camera2Fallback -> startCamera2Recording()
            // Rebind in composition mode; recording starts once bound (see bindToCamera).
            else -> {
                pendingRecord = true
                uiState.update { it.copy(mode = DualCaptureMode.Video) }
            }
        }
    }

    fun stopRecording() {
        repository.stopConcurrentRecording()
        stopCamera2Recording()
    }

    /**
     * Binds both cameras for [mode] and suspends until cancelled, then unbinds.
     * Call from a `LaunchedEffect(lifecycleOwner, mode)` on the main thread.
     */
    suspend fun bindToCamera(lifecycleOwner: LifecycleOwner, mode: DualCaptureMode) {
        val sessionId = ++bindSessionId
        if (!USE_CAMERAX_CONCURRENT) {
            switchToCamera2Fallback()
            return
        }
        if (!repository.hasConcurrentCameras()) {
            Log.w(TAG, "No concurrent front+back combination reported, using Camera2 fallback")
            switchToCamera2Fallback()
            return
        }
        uiState.update { it.copy(support = DualCameraSupport.Supported) }

        try {
            repository.bindConcurrentCameras(lifecycleOwner, mode, uiState.value.isSwapped)
            uiState.update { it.copy(isCameraBound = true) }
            if (mode == DualCaptureMode.Video) {
                if (pendingRecord) {
                    pendingRecord = false
                    startConcurrentRecording()
                } else {
                    // Video binding only exists to record; nothing to record -> back to Photo.
                    uiState.update { it.copy(mode = DualCaptureMode.Photo) }
                }
            }
            awaitCancellation()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // IllegalArgumentException / UnsupportedOperationException: stream combination
            // not supported, or camera already in use by another app.
            if (mode == DualCaptureMode.Video) {
                // Photo mode worked, only composition failed: keep the dual preview.
                Log.e(TAG, "Failed to bind composition mode for recording", e)
                pendingRecord = false
                effects.tryEmit(DualCameraEffect.Error(R.string.dual_camera_record_failed))
                uiState.update { it.copy(mode = DualCaptureMode.Photo) }
            } else {
                Log.e(TAG, "Failed to bind concurrent cameras ($mode), using Camera2 fallback", e)
                switchToCamera2Fallback()
            }
        } finally {
            if (sessionId == bindSessionId) {
                repository.unbindConcurrentCameras()
                uiState.update { it.copy(isCameraBound = false) }
            }
        }
    }

    /**
     * Opens the cameras directly with Camera2 (see [Camera2Viewfinder]): both of them, or only
     * the main one when the device can't stream both at once (single-camera mode).
     */
    private suspend fun switchToCamera2Fallback() {
        val ids = repository.findCamera2Ids()
        val isDualSupported = ids != null && repository.checkCameraSupport().third
        uiState.update {
            if (ids == null) {
                it.copy(support = DualCameraSupport.Unsupported, camera2Ids = null)
            } else {
                it.copy(
                    support = DualCameraSupport.Camera2Fallback,
                    camera2Ids = ids,
                    isSingleCamera = !isDualSupported,
                )
            }
        }
    }

    private fun startCamera2Recording() {
        if (repository.isCamera2Recording) return
        val layout = previewLayout ?: run {
            effects.tryEmit(DualCameraEffect.Error(R.string.dual_camera_record_failed))
            return
        }
        val state = uiState.value
        val firstFrameRecorded = repository
            .startCamera2Recording(layout, state.isSwapped, state.isSingleCamera)
            .getOrElse { e ->
                Log.e(TAG, "Failed to start Camera2 recording", e)
                effects.tryEmit(DualCameraEffect.Error(R.string.dual_camera_record_failed))
                return
            }

        uiState.update { it.copy(isRecording = true, recordedDurationMs = 0L) }
        camera2DurationJob = viewModelScope.launch {
            // Count from the first recorded frame so the timer matches the saved video.
            firstFrameRecorded.first { it }
            val startedAt = SystemClock.elapsedRealtime()
            while (true) {
                delay(500)
                uiState.update {
                    it.copy(recordedDurationMs = SystemClock.elapsedRealtime() - startedAt)
                }
            }
        }
    }

    private fun stopCamera2Recording() {
        if (!repository.isCamera2Recording) return
        camera2DurationJob?.cancel()
        camera2DurationJob = null
        // isCapturing blocks the buttons while the file is finalized.
        uiState.update { it.copy(isRecording = false, recordedDurationMs = 0L, isCapturing = true) }

        viewModelScope.launch {
            try {
                repository.stopCamera2Recording().fold(
                    onSuccess = { uri -> effects.emit(DualCameraEffect.VideoSaved(uri)) },
                    onFailure = { e ->
                        Log.e(TAG, "Camera2 recording failed", e)
                        effects.emit(DualCameraEffect.Error(R.string.dual_camera_record_failed))
                    },
                )
            } finally {
                uiState.update { it.copy(isCapturing = false) }
            }
        }
    }

    /** Records the composited (Video) binding, then goes back to the Photo binding. */
    private fun startConcurrentRecording() {
        viewModelScope.launch {
            val video = repository.recordConcurrentVideo(
                onStarted = {
                    uiState.update { it.copy(isRecording = true, recordedDurationMs = 0L) }
                },
                onDuration = { durationMs ->
                    uiState.update { it.copy(recordedDurationMs = durationMs) }
                },
            )
            // Back to Photo binding so the shutter is instant again.
            uiState.update {
                it.copy(isRecording = false, recordedDurationMs = 0L, mode = DualCaptureMode.Photo)
            }
            video.fold(
                onSuccess = { uri -> effects.emit(DualCameraEffect.VideoSaved(uri)) },
                onFailure = { e ->
                    Log.e(TAG, "Recording failed", e)
                    effects.emit(DualCameraEffect.Error(R.string.dual_camera_record_failed))
                },
            )
        }
    }

    override fun onCleared() {
        camera2DurationJob?.cancel()
        repository.release()
        super.onCleared()
    }
}

package com.looper.base.screen.dualcamera

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.hardware.camera2.CameraManager
import android.os.SystemClock
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.CompositionSettings
import androidx.camera.core.ConcurrentCamera.SingleCameraConfig
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.looper.base.R
import com.looper.base.screen.dualcamera.DualCameraMedia.toUprightBitmap
import com.looper.base.screen.dualcamera.data.DualCameraEffect
import com.looper.base.screen.dualcamera.data.DualCameraSupport
import com.looper.base.screen.dualcamera.data.DualCameraUiState
import com.looper.base.screen.dualcamera.data.DualCaptureMode
import com.looper.base.screen.dualcamera.data.DualLayout
import com.looper.base.screen.dualcamera.data.PreviewLayout
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val TAG = "DualCameraViewModel"

/**
 * Front camera position inside the composited video, in normalized device coordinates
 * (-1..1, origin at center, +Y up) of the *sensor-oriented* output buffer: CameraX applies
 * rotation after composition, so on a portrait phone (sensor orientation 90°) the buffer's
 * top-left corner ends up at the top-right of the screen. Tune on real devices if needed.
 */
/**
 * false: always use the Camera2 pipeline (Camera2Viewfinder + DualVideoRecorder), which renders
 * every [com.looper.base.screen.dualcamera.data.DualLayout] identically in preview, photo and
 * video. On devices with concurrent camera support both cameras stream with it too.
 * true: use CameraX ConcurrentCamera when supported; its composition video can only offset /
 * scale the second camera, so videos won't match the selected layout.
 */
private const val USE_CAMERAX_CONCURRENT = false

private const val VIDEO_PIP_SCALE = 0.3f
private const val VIDEO_PIP_OFFSET_X = -0.6f
private const val VIDEO_PIP_OFFSET_Y = 0.6f

class DualCameraViewModel(
    private val app: Application,
) : ViewModel() {

    val uiState: StateFlow<DualCameraUiState>
        field = MutableStateFlow(DualCameraUiState())

    val effects: SharedFlow<DualCameraEffect>
        field = MutableSharedFlow(extraBufferCapacity = 1)

    /** Back camera preview (photo mode) or the composited PiP preview (video mode). */
    val primarySurfaceRequest: StateFlow<SurfaceRequest?>
        field = MutableStateFlow<SurfaceRequest?>(null)

    /** Front camera preview, only used in photo mode. */
    val secondarySurfaceRequest: StateFlow<SurfaceRequest?>
        field = MutableStateFlow<SurfaceRequest?>(null)

    /** Still capture / recording handles for the Camera2 fallback viewfinders. */
    val camera2Back = Camera2CameraHandle()
    val camera2Front = Camera2CameraHandle()

    /** Camera2 fallback recording (composited like the preview), null when idle. */
    private var camera2Recorder: DualVideoRecorder? = null
    private var camera2DurationJob: Job? = null

    private val callbackExecutor = Dispatchers.Default.asExecutor()
    private val mainExecutor by lazy { ContextCompat.getMainExecutor(app) }

    private var backImageCapture: ImageCapture? = null
    private var frontImageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var activeRecording: Recording? = null

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

    fun onSwapClick() {
        if (!uiState.value.canEditLayout) return
        uiState.update { it.copy(isSwapped = !it.isSwapped) }
    }

    fun onLayoutSelected(layout: DualLayout) {
        if (!uiState.value.canEditLayout) return
        uiState.update { it.copy(layout = layout) }
    }

    fun onPhotoClick() {
        val state = uiState.value
        if (!state.isPhotoEnabled) return
        if (state.support == DualCameraSupport.Camera2Fallback) captureCamera2Photo() else capturePhoto()
    }

    fun onVideoClick() {
        val state = uiState.value
        when {
            activeRecording != null || camera2Recorder != null -> stopRecording()
            !state.isVideoEnabled -> Unit
            state.support == DualCameraSupport.Camera2Fallback -> startCamera2Recording()
            // Rebind in composition mode; recording starts once bound (see bindToCamera).
            else -> {
                pendingRecord = true
                uiState.update { it.copy(mode = DualCaptureMode.Video) }
            }
        }
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
        val provider = ProcessCameraProvider.awaitInstance(app)

        val (backSelector, frontSelector) = findFrontBackPair(provider) ?: run {
            Log.w(TAG, "No concurrent front+back combination reported, using Camera2 fallback")
            switchToCamera2Fallback()
            return
        }
        uiState.update { it.copy(support = DualCameraSupport.Supported) }

        try {
            primarySurfaceRequest.value = null
            secondarySurfaceRequest.value = null
            val configs = when (mode) {
                DualCaptureMode.Photo -> buildPhotoConfigs(backSelector, frontSelector, lifecycleOwner)
                DualCaptureMode.Video -> buildVideoConfigs(backSelector, frontSelector, lifecycleOwner)
            }
            provider.unbindAll()
            provider.bindToLifecycle(configs)
            uiState.update { it.copy(isCameraBound = true) }
            if (mode == DualCaptureMode.Video) {
                if (pendingRecord) {
                    pendingRecord = false
                    startRecording()
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
                activeRecording?.stop()
                provider.unbindAll()
                backImageCapture = null
                frontImageCapture = null
                videoCapture = null
                primarySurfaceRequest.value = null
                secondarySurfaceRequest.value = null
                uiState.update { it.copy(isCameraBound = false) }
            }
        }
    }

    /** Preview-only path that opens both cameras directly with Camera2 (see [Camera2Viewfinder]). */
    private fun switchToCamera2Fallback() {
        val ids = app.getSystemService(CameraManager::class.java)?.findBackAndFrontIds()
        uiState.update {
            if (ids == null) {
                it.copy(support = DualCameraSupport.Unsupported, camera2Ids = null)
            } else {
                it.copy(support = DualCameraSupport.Camera2Fallback, camera2Ids = ids)
            }
        }
    }

    fun stopRecording() {
        activeRecording?.stop()
        stopCamera2Recording()
    }

    // region Camera2 video

    /**
     * Records both Camera2 streams composited exactly like the preview. A camera that can't
     * stream (e.g. evicted on single-camera devices) is recorded as a red area.
     */
    private fun startCamera2Recording() {
        if (camera2Recorder != null) return
        val layout = previewLayout ?: run {
            effects.tryEmit(DualCameraEffect.Error(R.string.dual_camera_record_failed))
            return
        }
        val recorder = try {
            DualVideoRecorder(
                context = app,
                layout = layout,
                isSwapped = uiState.value.isSwapped,
                backStream = camera2Back.streamInfo,
                frontStream = camera2Front.streamInfo,
                withAudio = hasPermission(Manifest.permission.RECORD_AUDIO),
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start Camera2 recording", e)
            effects.tryEmit(DualCameraEffect.Error(R.string.dual_camera_record_failed))
            return
        }
        camera2Recorder = recorder
        camera2Back.setRecordingSurface(recorder.backSurface)
        camera2Front.setRecordingSurface(recorder.frontSurface)

        uiState.update { it.copy(isRecording = true, recordedDurationMs = 0L) }
        camera2DurationJob = viewModelScope.launch {
            // Count from the first recorded frame so the timer matches the saved video.
            recorder.isStarted.first { it }
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
        val recorder = camera2Recorder ?: return
        camera2Recorder = null
        camera2DurationJob?.cancel()
        // Back to [preview, JPEG] sessions before the recorder releases its surfaces.
        camera2Back.setRecordingSurface(null)
        camera2Front.setRecordingSurface(null)
        // isCapturing blocks the buttons while the file is finalized.
        uiState.update { it.copy(isRecording = false, recordedDurationMs = 0L, isCapturing = true) }

        viewModelScope.launch {
            try {
                effects.emit(DualCameraEffect.VideoSaved(recorder.stop()))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Camera2 recording failed", e)
                effects.emit(DualCameraEffect.Error(R.string.dual_camera_record_failed))
            } finally {
                uiState.update { it.copy(isCapturing = false) }
            }
        }
    }

    // endregion

    // region Photo

    private fun buildPhotoConfigs(
        backSelector: CameraSelector,
        frontSelector: CameraSelector,
        lifecycleOwner: LifecycleOwner,
    ): List<SingleCameraConfig> {
        val resolutionSelector = ratio16x9Selector()

        val backPreview = Preview.Builder()
            .setResolutionSelector(resolutionSelector)
            .build()
            .apply { setSurfaceProvider { request -> primarySurfaceRequest.value = request } }
        val frontPreview = Preview.Builder()
            .setResolutionSelector(resolutionSelector)
            .build()
            .apply { setSurfaceProvider { request -> secondarySurfaceRequest.value = request } }

        val backCapture = newImageCapture(resolutionSelector).also { backImageCapture = it }
        val frontCapture = newImageCapture(resolutionSelector).also { frontImageCapture = it }

        // Non-composition mode: each camera owns its own use cases.
        return listOf(
            SingleCameraConfig(
                backSelector,
                UseCaseGroup.Builder().addUseCase(backPreview).addUseCase(backCapture).build(),
                lifecycleOwner,
            ),
            SingleCameraConfig(
                frontSelector,
                UseCaseGroup.Builder().addUseCase(frontPreview).addUseCase(frontCapture).build(),
                lifecycleOwner,
            ),
        )
    }

    private fun newImageCapture(resolutionSelector: ResolutionSelector) = ImageCapture.Builder()
        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
        .setResolutionSelector(resolutionSelector)
        .build()

    private fun capturePhoto() {
        val back = backImageCapture ?: return
        val front = frontImageCapture ?: return
        if (uiState.value.isCapturing) return

        viewModelScope.launch {
            uiState.update { it.copy(isCapturing = true) }
            try {
                // Fire both shutters at the same time.
                val (backBitmap, frontBitmap) = coroutineScope {
                    val backShot = async { back.takeBitmap(mirror = false) }
                    val frontShot = async { front.takeBitmap(mirror = true) }
                    backShot.await() to frontShot.await()
                }
                effects.emit(DualCameraEffect.PhotoSaved(saveDualPhoto(backBitmap, frontBitmap)))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Dual photo capture failed", e)
                effects.emit(DualCameraEffect.Error(R.string.dual_camera_capture_failed))
            } finally {
                uiState.update { it.copy(isCapturing = false) }
            }
        }
    }

    /** Camera2 fallback: best effort, saves a single image if only one camera delivers. */
    private fun captureCamera2Photo() {
        if (uiState.value.isCapturing) return

        viewModelScope.launch {
            uiState.update { it.copy(isCapturing = true) }
            try {
                val (backBitmap, frontBitmap) = coroutineScope {
                    val backShot = async { camera2Back.takePictureOrNull("back") }
                    val frontShot = async { camera2Front.takePictureOrNull("front") }
                    backShot.await() to frontShot.await()
                }
                val effect = if (backBitmap == null && frontBitmap == null) {
                    DualCameraEffect.Error(R.string.dual_camera_capture_failed)
                } else {
                    DualCameraEffect.PhotoSaved(
                        uri = saveDualPhoto(backBitmap, frontBitmap),
                        isSingleCamera = backBitmap == null || frontBitmap == null,
                    )
                }
                effects.emit(effect)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Camera2 photo capture failed", e)
                effects.emit(DualCameraEffect.Error(R.string.dual_camera_capture_failed))
            } finally {
                uiState.update { it.copy(isCapturing = false) }
            }
        }
    }

    private suspend fun Camera2CameraHandle.takePictureOrNull(label: String): Bitmap? = try {
        takePicture()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "Camera2 $label capture failed", e)
        null
    }

    /**
     * Renders the shots exactly like the on-screen preview (full screen camera + PiP), saves
     * the JPEG and recycles every bitmap.
     *
     * A camera that delivered no frame (null) keeps its slot and is painted red, like on screen
     * (on single-camera devices the back camera is the one kept alive, see [Camera2Viewfinder]
     * open order).
     */
    private suspend fun saveDualPhoto(back: Bitmap?, front: Bitmap?): android.net.Uri {
        check(back != null || front != null) { "No camera frame to save" }
        val (main, pip) = if (uiState.value.isSwapped) front to back else back to front
        val layout = previewLayout ?: (main ?: pip!!).let {
            // Preview never measured: uncropped photo with a top-end PiP.
            PreviewLayout.default(it.width.toFloat(), it.height.toFloat())
        }

        return withContext(Dispatchers.IO) {
            val output = DualCameraMedia.composeLikePreview(main, pip, layout)
            try {
                DualCameraMedia.saveJpeg(app, output)
            } finally {
                output.recycle()
                back?.recycle()
                front?.recycle()
            }
        }
    }

    private suspend fun ImageCapture.takeBitmap(mirror: Boolean): Bitmap =
        suspendCancellableCoroutine { continuation ->
            takePicture(callbackExecutor, object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val result = runCatching { image.toUprightBitmap(mirror) }
                    result.fold(
                        onSuccess = { continuation.resume(it) },
                        onFailure = { continuation.resumeWithException(it) },
                    )
                }

                override fun onError(exception: ImageCaptureException) {
                    continuation.resumeWithException(exception)
                }
            })
        }

    // endregion

    // region Video

    private fun buildVideoConfigs(
        backSelector: CameraSelector,
        frontSelector: CameraSelector,
        lifecycleOwner: LifecycleOwner,
    ): List<SingleCameraConfig> {
        val preview = Preview.Builder()
            .setResolutionSelector(ratio16x9Selector())
            .build()
            .apply { setSurfaceProvider { request -> primarySurfaceRequest.value = request } }

        // Concurrent cameras are limited to ~720p/1440p streams, so ask for HD with fallback.
        val recorder = Recorder.Builder()
            .setQualitySelector(
                QualitySelector.from(
                    Quality.HD,
                    FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)
                )
            )
            .build()
        val capture = VideoCapture.withOutput(recorder).also { videoCapture = it }

        // Composition mode: BOTH configs share the SAME Preview + VideoCapture instances, so
        // CameraX composites the two streams into one (preview and recording). The z-order
        // follows the list order: main camera full frame, the other one on top as PiP.
        val sharedGroup = UseCaseGroup.Builder()
            .addUseCase(preview)
            .addUseCase(capture)
            .build()

        val mainComposition = CompositionSettings.Builder()
            .setAlpha(1f)
            .setOffset(0f, 0f)
            .setScale(1f, 1f)
            .build()
        val pipComposition = CompositionSettings.Builder()
            .setAlpha(1f)
            .setOffset(VIDEO_PIP_OFFSET_X, VIDEO_PIP_OFFSET_Y)
            .setScale(VIDEO_PIP_SCALE, VIDEO_PIP_SCALE)
            .build()

        // Follow the on-screen swap state at the moment recording starts.
        val (mainSelector, pipSelector) = if (uiState.value.isSwapped) {
            frontSelector to backSelector
        } else {
            backSelector to frontSelector
        }
        return listOf(
            SingleCameraConfig(mainSelector, sharedGroup, mainComposition, lifecycleOwner),
            SingleCameraConfig(pipSelector, sharedGroup, pipComposition, lifecycleOwner),
        )
    }

    @SuppressLint("MissingPermission") // Audio is only enabled after an explicit permission check.
    private fun startRecording() {
        val capture = videoCapture ?: return
        if (activeRecording != null) return

        var pending = capture.output.prepareRecording(app, DualCameraMedia.videoOutputOptions(app))
        if (hasPermission(Manifest.permission.RECORD_AUDIO)) {
            pending = pending.withAudioEnabled()
        }

        activeRecording = try {
            pending.start(mainExecutor) { event -> onRecordEvent(event) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording", e)
            effects.tryEmit(DualCameraEffect.Error(R.string.dual_camera_record_failed))
            uiState.update { it.copy(mode = DualCaptureMode.Photo) }
            null
        }
    }

    private fun onRecordEvent(event: VideoRecordEvent) {
        when (event) {
            is VideoRecordEvent.Start -> uiState.update {
                it.copy(isRecording = true, recordedDurationMs = 0L)
            }

            is VideoRecordEvent.Status -> uiState.update {
                it.copy(recordedDurationMs = event.recordingStats.recordedDurationNanos / 1_000_000)
            }

            is VideoRecordEvent.Finalize -> {
                activeRecording = null
                // Back to Photo binding so the shutter is instant again.
                uiState.update {
                    it.copy(
                        isRecording = false,
                        recordedDurationMs = 0L,
                        mode = DualCaptureMode.Photo,
                    )
                }

                val uri = event.outputResults.outputUri
                if (!event.hasError() || uri.isUsable()) {
                    effects.tryEmit(DualCameraEffect.VideoSaved(uri))
                } else {
                    Log.e(TAG, "Recording failed: error=${event.error}", event.cause)
                    effects.tryEmit(DualCameraEffect.Error(R.string.dual_camera_record_failed))
                }
            }
        }
    }

    // endregion

    private fun findFrontBackPair(
        provider: ProcessCameraProvider,
    ): Pair<CameraSelector, CameraSelector>? =
        provider.availableConcurrentCameraInfos.firstNotNullOfOrNull { combination ->
            val back = combination.firstOrNull { it.lensFacing == CameraSelector.LENS_FACING_BACK }
            val front = combination.firstOrNull { it.lensFacing == CameraSelector.LENS_FACING_FRONT }
            if (back != null && front != null) back.cameraSelector to front.cameraSelector else null
        }

    private fun ratio16x9Selector() = ResolutionSelector.Builder()
        .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
        .build()

    private fun hasPermission(permission: String) =
        ContextCompat.checkSelfPermission(app, permission) == PackageManager.PERMISSION_GRANTED

    private fun android.net.Uri.isUsable() = this != android.net.Uri.EMPTY

    override fun onCleared() {
        activeRecording?.stop()
        activeRecording = null
        camera2DurationJob?.cancel()
        camera2Recorder?.cancel()
        camera2Recorder = null
        super.onCleared()
    }
}

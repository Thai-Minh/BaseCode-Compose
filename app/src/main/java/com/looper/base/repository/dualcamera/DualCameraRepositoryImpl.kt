package com.looper.base.repository.dualcamera

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.util.Log
import android.util.Size
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
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.PendingRecording
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.looper.base.repository.dualcamera.data.DualPhoto
import com.looper.base.repository.runCatchingCancellable
import com.looper.base.screen.dualcamera.Camera2CameraHandle
import com.looper.base.screen.dualcamera.DualCameraMedia
import com.looper.base.screen.dualcamera.DualCameraMedia.toUprightBitmap
import com.looper.base.screen.dualcamera.DualVideoRecorder
import com.looper.base.screen.dualcamera.data.Camera2CameraIds
import com.looper.base.screen.dualcamera.data.DualCaptureMode
import com.looper.base.screen.dualcamera.data.PreviewLayout
import com.looper.base.screen.dualcamera.findBackAndFrontIds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val TAG = "DualCameraRepository"

/**
 * Front camera position inside the composited video, in normalized device coordinates
 * (-1..1, origin at center, +Y up) of the *sensor-oriented* output buffer: CameraX applies
 * rotation after composition, so on a portrait phone (sensor orientation 90°) the buffer's
 * top-left corner ends up at the top-right of the screen. Tune on real devices if needed.
 */
private const val VIDEO_PIP_SCALE = 0.3f
private const val VIDEO_PIP_OFFSET_X = -0.6f
private const val VIDEO_PIP_OFFSET_Y = 0.6f

/** CameraX still size budget (sensor orientation). */
private val MAX_STILL_SIZE = Size(1920, 1080)

/**
 * Holds the screen's bound cameras and running recording: one instance per screen (Koin
 * factory), released by [release].
 */
class DualCameraRepositoryImpl(
    private val context: Context,
) : IDualCameraRepository {

    override val camera2Back = Camera2CameraHandle()
    override val camera2Front = Camera2CameraHandle()

    private val _primarySurfaceRequest = MutableStateFlow<SurfaceRequest?>(null)
    override val primarySurfaceRequest: StateFlow<SurfaceRequest?> =
        _primarySurfaceRequest.asStateFlow()

    private val _secondarySurfaceRequest = MutableStateFlow<SurfaceRequest?>(null)
    override val secondarySurfaceRequest: StateFlow<SurfaceRequest?> =
        _secondarySurfaceRequest.asStateFlow()

    private val callbackExecutor = Dispatchers.Default.asExecutor()
    private val mainExecutor by lazy { ContextCompat.getMainExecutor(context) }

    /** Camera2 recording (composited like the preview), null when idle. */
    private var camera2Recorder: DualVideoRecorder? = null

    private var cameraProvider: ProcessCameraProvider? = null
    private var backImageCapture: ImageCapture? = null
    private var frontImageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var activeRecording: Recording? = null

    override val isRecording: Boolean
        get() = activeRecording != null || camera2Recorder != null

    override val isCamera2Recording: Boolean
        get() = camera2Recorder != null

    override suspend fun checkCameraSupport(): Triple<Boolean, Boolean, Boolean> {
        val lensFacings = withContext(Dispatchers.IO) { cameraLensFacings() }
        val hasDual = runCatchingCancellable { hasConcurrentCameras() }
            .onFailure { Log.e(TAG, "Failed to query the concurrent cameras", it) }
            .getOrDefault(false)
        return Triple(
            CameraCharacteristics.LENS_FACING_FRONT in lensFacings,
            CameraCharacteristics.LENS_FACING_BACK in lensFacings,
            hasDual,
        )
    }

    /** Lens facing of every camera (Camera2), empty if they can't be queried. */
    private fun cameraLensFacings(): List<Int> {
        val manager = context.getSystemService(CameraManager::class.java) ?: return emptyList()
        return runCatching {
            manager.cameraIdList.mapNotNull { id ->
                manager.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING)
            }
        }.onFailure { Log.e(TAG, "Failed to query the cameras", it) }.getOrDefault(emptyList())
    }

    // region Camera2 pipeline

    override fun findCamera2Ids(): Camera2CameraIds? =
        context.getSystemService(CameraManager::class.java)?.findBackAndFrontIds()

    override suspend fun captureCamera2Photo(
        isSwapped: Boolean,
        layout: PreviewLayout?,
        isSingleCamera: Boolean,
    ): Result<DualPhoto> = runCatchingCancellable {
        if (isSingleCamera) {
            val shot = (if (isSwapped) camera2Front else camera2Back).takePicture()
            val (backBitmap, frontBitmap) = if (isSwapped) null to shot else shot to null
            DualPhoto(
                uri = saveDualPhoto(
                    backBitmap,
                    frontBitmap,
                    isSwapped,
                    layout,
                    secondPlaceholderColor = PreviewLayout.FAILED_CAMERA_COLOR,
                ),
                // Only one camera is expected here: not reported as a failed capture.
                isSingleCamera = false,
            )
        } else {
            val (backBitmap, frontBitmap) = coroutineScope {
                val backShot = async { camera2Back.takePictureOrNull("back") }
                val frontShot = async { camera2Front.takePictureOrNull("front") }
                backShot.await() to frontShot.await()
            }
            DualPhoto(
                uri = saveDualPhoto(backBitmap, frontBitmap, isSwapped, layout),
                isSingleCamera = backBitmap == null || frontBitmap == null,
            )
        }
    }

    /**
     * A camera that can't stream (e.g. evicted on single-camera devices) is recorded as a red
     * area. The recorder is set up off the main thread; cancelling the caller meanwhile discards
     * it.
     */
    override suspend fun startCamera2Recording(
        layout: PreviewLayout,
        isSwapped: Boolean,
        isSingleCamera: Boolean,
    ): Result<StateFlow<Boolean>> = runCatchingCancellable {
        check(camera2Recorder == null) { "Already recording" }
        // Single camera: only the main one is recorded, the window keeps its placeholder.
        val recordsBack = !isSingleCamera || !isSwapped
        val recordsFront = !isSingleCamera || isSwapped
        val backStream = camera2Back.streamInfo.takeIf { recordsBack }
        val frontStream = camera2Front.streamInfo.takeIf { recordsFront }
        val withAudio = canRecordAudio()

        // Video / audio encoders, muxer, GL and microphone set-up block for hundreds of ms
        // (mostly waiting on the media server and the GL thread): IO, not the main thread.
        var created: DualVideoRecorder? = null
        try {
            withContext(Dispatchers.IO) {
                created = DualVideoRecorder(
                    context = context,
                    layout = layout,
                    isSwapped = isSwapped,
                    backStream = backStream,
                    frontStream = frontStream,
                    withAudio = withAudio,
                    secondPlaceholderColor =
                        PreviewLayout.FAILED_CAMERA_COLOR.takeIf { isSingleCamera },
                )
            }
        } catch (e: CancellationException) {
            // Stopped while it was being set up: release it instead of leaking it.
            created?.cancel()
            throw e
        }
        val recorder = checkNotNull(created)
        camera2Recorder = recorder
        camera2Back.setRecordingSurface(recorder.backSurface)
        camera2Front.setRecordingSurface(recorder.frontSurface)
        recorder.isStarted
    }

    override suspend fun stopCamera2Recording(): Result<Uri> = runCatchingCancellable {
        val recorder = checkNotNull(camera2Recorder) { "No Camera2 recording" }
        camera2Recorder = null
        // Back to [preview, JPEG] sessions before the recorder releases its surfaces.
        camera2Back.setRecordingSurface(null)
        camera2Front.setRecordingSurface(null)
        recorder.stop()
    }

    private suspend fun Camera2CameraHandle.takePictureOrNull(label: String): Bitmap? = try {
        takePicture()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "Camera2 $label capture failed", e)
        null
    }

    // endregion

    // region CameraX concurrent cameras

    override suspend fun hasConcurrentCameras(): Boolean =
        findFrontBackPair(awaitCameraProvider()) != null

    override suspend fun bindConcurrentCameras(
        lifecycleOwner: LifecycleOwner,
        mode: DualCaptureMode,
        isSwapped: Boolean,
    ) {
        val provider = awaitCameraProvider()
        val (backSelector, frontSelector) = checkNotNull(findFrontBackPair(provider)) {
            "No concurrent front + back camera combination"
        }
        _primarySurfaceRequest.value = null
        _secondarySurfaceRequest.value = null
        val configs = when (mode) {
            DualCaptureMode.Photo -> buildPhotoConfigs(backSelector, frontSelector, lifecycleOwner)
            DualCaptureMode.Video ->
                buildVideoConfigs(backSelector, frontSelector, lifecycleOwner, isSwapped)
        }
        provider.unbindAll()
        provider.bindToLifecycle(configs)
    }

    override fun unbindConcurrentCameras() {
        activeRecording?.stop()
        cameraProvider?.unbindAll()
        backImageCapture = null
        frontImageCapture = null
        videoCapture = null
        _primarySurfaceRequest.value = null
        _secondarySurfaceRequest.value = null
    }

    override suspend fun captureConcurrentPhoto(
        isSwapped: Boolean,
        layout: PreviewLayout?,
    ): Result<DualPhoto> = runCatchingCancellable {
        val back = checkNotNull(backImageCapture) { "Photo binding not ready" }
        val front = checkNotNull(frontImageCapture) { "Photo binding not ready" }
        // Fire both shutters at the same time.
        val (backBitmap, frontBitmap) = coroutineScope {
            val backShot = async { back.takeBitmap(mirror = false) }
            val frontShot = async { front.takeBitmap(mirror = true) }
            backShot.await() to frontShot.await()
        }
        DualPhoto(saveDualPhoto(backBitmap, frontBitmap, isSwapped, layout), isSingleCamera = false)
    }

    @SuppressLint("MissingPermission") // Audio is only enabled after an explicit permission check.
    override suspend fun recordConcurrentVideo(
        onStarted: () -> Unit,
        onDuration: (Long) -> Unit,
    ): Result<Uri> = runCatchingCancellable {
        val capture = checkNotNull(videoCapture) { "Video binding not ready" }
        check(activeRecording == null) { "Already recording" }
        var pending = capture.output.prepareRecording(
            context,
            DualCameraMedia.videoOutputOptions(context),
        )
        if (canRecordAudio()) {
            pending = pending.withAudioEnabled()
        }
        val finalize = pending.record(onStarted, onDuration)
        val uri = finalize.outputResults.outputUri
        // An error may still leave a playable video in the gallery.
        check(!finalize.hasError() || uri != Uri.EMPTY) {
            "Recording failed: error=${finalize.error}, cause=${finalize.cause}"
        }
        uri
    }

    override fun stopConcurrentRecording() {
        activeRecording?.stop()
    }

    private fun buildPhotoConfigs(
        backSelector: CameraSelector,
        frontSelector: CameraSelector,
        lifecycleOwner: LifecycleOwner,
    ): List<SingleCameraConfig> {
        val resolutionSelector = ratio16x9Selector()

        val backPreview = Preview.Builder()
            .setResolutionSelector(resolutionSelector)
            .build()
            .apply { setSurfaceProvider { request -> _primarySurfaceRequest.value = request } }
        val frontPreview = Preview.Builder()
            .setResolutionSelector(resolutionSelector)
            .build()
            .apply { setSurfaceProvider { request -> _secondarySurfaceRequest.value = request } }

        val backCapture = newImageCapture().also { backImageCapture = it }
        val frontCapture = newImageCapture().also { frontImageCapture = it }

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

    /**
     * Stills at most ~1080p, like the Camera2 pipeline: the photo is composited at screen scale,
     * and a full-resolution frame plus its rotated copy per camera (~100 MB each at 12 MP) only
     * risks running out of memory.
     */
    private fun newImageCapture() = ImageCapture.Builder()
        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
        .setResolutionSelector(
            ResolutionSelector.Builder()
                .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
                .setResolutionStrategy(
                    ResolutionStrategy(
                        MAX_STILL_SIZE,
                        ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER,
                    )
                )
                .build()
        )
        .build()

    private fun buildVideoConfigs(
        backSelector: CameraSelector,
        frontSelector: CameraSelector,
        lifecycleOwner: LifecycleOwner,
        isSwapped: Boolean,
    ): List<SingleCameraConfig> {
        val preview = Preview.Builder()
            .setResolutionSelector(ratio16x9Selector())
            .build()
            .apply { setSurfaceProvider { request -> _primarySurfaceRequest.value = request } }

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
        val (mainSelector, pipSelector) = if (isSwapped) {
            frontSelector to backSelector
        } else {
            backSelector to frontSelector
        }
        return listOf(
            SingleCameraConfig(mainSelector, sharedGroup, mainComposition, lifecycleOwner),
            SingleCameraConfig(pipSelector, sharedGroup, pipComposition, lifecycleOwner),
        )
    }

    /**
     * Starts this recording and suspends until it is finalized ([stopConcurrentRecording] or
     * the cameras being unbound); cancelling the caller stops it.
     */
    private suspend fun PendingRecording.record(
        onStarted: () -> Unit,
        onDuration: (Long) -> Unit,
    ): VideoRecordEvent.Finalize = suspendCancellableCoroutine { continuation ->
        val recording = start(mainExecutor) { event ->
            when (event) {
                is VideoRecordEvent.Start -> onStarted()
                is VideoRecordEvent.Status ->
                    onDuration(event.recordingStats.recordedDurationNanos / 1_000_000)

                is VideoRecordEvent.Finalize -> {
                    activeRecording = null
                    continuation.resume(event)
                }

                else -> Unit
            }
        }
        activeRecording = recording
        continuation.invokeOnCancellation { recording.stop() }
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

    private suspend fun awaitCameraProvider(): ProcessCameraProvider =
        cameraProvider ?: ProcessCameraProvider.awaitInstance(context).also { cameraProvider = it }

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

    // endregion

    /**
     * Renders the shots exactly like the on-screen preview (full screen camera + PiP), saves
     * the JPEG and recycles every bitmap.
     *
     * A camera that delivered no frame (null) keeps its slot and is painted red, like on screen
     * (on single-camera devices the back camera is the one kept alive, see the Camera2Viewfinder
     * open order). [secondPlaceholderColor] fills the window instead (single-camera mode).
     */
    private suspend fun saveDualPhoto(
        back: Bitmap?,
        front: Bitmap?,
        isSwapped: Boolean,
        layout: PreviewLayout?,
        secondPlaceholderColor: Int? = null,
    ): Uri {
        check(back != null || front != null) { "No camera frame to save" }
        val (main, pip) = if (isSwapped) front to back else back to front
        val photoLayout = layout ?: (main ?: pip!!).let {
            // Preview never measured: uncropped photo with a top-end PiP.
            PreviewLayout.default(it.width.toFloat(), it.height.toFloat())
        }

        return withContext(Dispatchers.IO) {
            val output = DualCameraMedia.composeLikePreview(
                main,
                pip,
                photoLayout,
                secondPlaceholderColor,
            )
            try {
                DualCameraMedia.saveJpeg(context, output)
            } finally {
                output.recycle()
                back?.recycle()
                front?.recycle()
            }
        }
    }

    private fun canRecordAudio() =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    override fun release() {
        activeRecording?.stop()
        activeRecording = null
        camera2Recorder?.cancel()
        camera2Recorder = null
    }
}

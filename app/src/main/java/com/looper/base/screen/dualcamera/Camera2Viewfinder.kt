package com.looper.base.screen.dualcamera

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureFailure
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.media.ImageReader
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Range
import android.util.Size
import android.view.Surface
import androidx.camera.viewfinder.compose.Viewfinder
import androidx.camera.viewfinder.core.ImplementationMode
import androidx.camera.viewfinder.core.TransformationInfo
import androidx.camera.viewfinder.core.ViewfinderSurfaceRequest
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import com.looper.base.screen.dualcamera.data.Camera2CameraIds
import com.looper.base.screen.dualcamera.data.PreviewLayout
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val TAG = "Camera2Viewfinder"

/**
 * Keep both streams small: devices without declared concurrent support are far more likely
 * to accept two simultaneous sessions at <= 720p.
 */
private const val MAX_PREVIEW_PIXELS = 1280 * 720

/** Still (JPEG) stream size budget, kept moderate for the same reason. */
private const val MAX_STILL_PIXELS = 1920 * 1080

private const val STILL_CAPTURE_TIMEOUT_MS = 5_000L

private const val MAX_REOPEN_RETRIES = 2
private const val REOPEN_DELAY_MS = 1_000L

/** Max wait for the on-demand `[preview, JPEG]` session. */
private const val STILL_SESSION_TIMEOUT_MS = 3_000L

/** Lets 3A settle on the new still session before capturing. */
private const val STILL_SETTLE_MS = 300L

/** Never stream faster than this: enough for preview / video, and keeps the HAL cooler. */
private const val MAX_FPS = 30

/** Size of the frames a streaming camera delivers, and their size once upright on screen. */
data class Camera2StreamInfo(
    val bufferSize: Size,
    val uprightWidth: Int,
    val uprightHeight: Int,
)

/**
 * Controls a running [Camera2Viewfinder] from outside the UI: still capture and recording.
 * Pass the same instance to the viewfinder.
 */
class Camera2CameraHandle {
    /** Capture function of the current still-capable session, null otherwise. */
    private val capture = MutableStateFlow<(suspend () -> Bitmap)?>(null)
    private val stillMutex = Mutex()

    /** Non-null while the camera is open and streaming. */
    @Volatile
    var streamInfo: Camera2StreamInfo? = null
        internal set

    internal val recordingSurface = MutableStateFlow<Surface?>(null)

    /**
     * True only while a still is being taken: the JPEG output is added to the session on demand
     * because keeping it configured makes the HAL run its snapshot pipeline all the time (heat).
     */
    internal val stillMode = MutableStateFlow(false)

    internal fun attach(block: suspend () -> Bitmap) {
        capture.value = block
    }

    internal fun detach(block: suspend () -> Bitmap) {
        capture.compareAndSet(block, null)
    }

    /**
     * Returns an upright bitmap, mirrored for the front lens to match its preview.
     * Briefly rebuilds the session as [preview, JPEG] (the preview may pause a moment).
     */
    suspend fun takePicture(): Bitmap = stillMutex.withLock {
        if (streamInfo == null) throw CameraUnavailableException("Camera is not streaming")
        stillMode.value = true
        try {
            val block = withTimeoutOrNull(STILL_SESSION_TIMEOUT_MS) {
                capture.filterNotNull().first()
            } ?: throw CameraUnavailableException("Still capture session not ready")
            // Let auto exposure / focus settle on the freshly configured session.
            delay(STILL_SETTLE_MS)
            block()
        } finally {
            stillMode.value = false
        }
    }

    /**
     * Also streams the camera into [surface] (null stops it). The capture session is rebuilt
     * as [preview, surface]; still capture is unavailable meanwhile.
     */
    fun setRecordingSurface(surface: Surface?) {
        recordingSurface.value = surface
    }
}

/** Finds the first back-facing and first front-facing camera ids via Camera2. */
fun CameraManager.findBackAndFrontIds(): Camera2CameraIds? = runCatching {
    val ids = cameraIdList
    fun firstWithFacing(facing: Int) = ids.firstOrNull {
        getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) == facing
    }

    val back = firstWithFacing(CameraCharacteristics.LENS_FACING_BACK)
    val front = firstWithFacing(CameraCharacteristics.LENS_FACING_FRONT)
    if (back != null && front != null) Camera2CameraIds(back, front) else null
}.onFailure { Log.e(TAG, "Failed to query camera ids", it) }.getOrNull()

/**
 * Shows the preview of a single camera opened directly with Camera2.
 *
 * Two of these can be on screen at the same time (one per camera) to get a dual preview on
 * devices where CameraX refuses concurrent mode. The camera is opened when the viewfinder's
 * surface becomes available and closed when this composable leaves composition.
 *
 * @param handle optional still capture / recording control for this camera.
 * @param awaitBeforeOpen suspends before opening the camera, to control the open order.
 * @param onSettled called once the preview is streaming, or when opening failed.
 */
@Composable
fun Camera2Viewfinder(
    cameraId: String,
    modifier: Modifier = Modifier,
    implementationMode: ImplementationMode? = null,
    handle: Camera2CameraHandle? = null,
    awaitBeforeOpen: suspend () -> Unit = {},
    onSettled: () -> Unit = {},
) {
    val context = LocalContext.current
    val cameraManager = remember { context.getSystemService(CameraManager::class.java) }
    val spec = remember(cameraId) { cameraManager.previewSpec(cameraId) }

    val view = LocalView.current
    val configuration = LocalConfiguration.current
    val displayRotationDegrees = remember(configuration) {
        when (view.display?.rotation ?: Surface.ROTATION_0) {
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> 0
        }
    }

    var errorMessage by remember(cameraId) { mutableStateOf<String?>(null) }
    val relativeRotation = spec?.relativeRotation(displayRotationDegrees) ?: 0
    // Read lazily at capture time, so stills follow the current display rotation.
    val currentRotation by rememberUpdatedState(relativeRotation)

    Box(modifier = modifier) {
        if (spec != null) {
            val surfaceRequest = remember(cameraId, spec.previewSize, implementationMode) {
                ViewfinderSurfaceRequest(
                    width = spec.previewSize.width,
                    height = spec.previewSize.height,
                    implementationMode = implementationMode,
                    requestId = "camera2-$cameraId",
                )
            }
            // The camera framework rotates (and mirrors, for the front lens) the buffer itself;
            // Viewfinder only needs the relative rotation to compute scaling / cropping.
            val transformationInfo = TransformationInfo(
                sourceRotation = relativeRotation,
                isSourceMirroredHorizontally = spec.isFront,
            )

            Viewfinder(
                surfaceRequest = surfaceRequest,
                transformationInfo = transformationInfo,
                // Own layer: each camera frame invalidates the TextureView, and only this small
                // layer is re-recorded instead of the parent's background / clip draw.
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer(),
            ) {
                onSurfaceSession {
                    errorMessage = null
                    val config = PreviewConfig(
                        cameraId = cameraId,
                        previewSurface = surface,
                        spec = spec,
                        handle = handle,
                        stillRotationDegrees = { currentRotation },
                        onStreaming = onSettled,
                    )
                    awaitBeforeOpen()
                    var retries = 0
                    while (true) {
                        try {
                            cameraManager.runPreview(config)
                            break
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            // Transient HAL failures (e.g. overloaded / hot): reopen a few times.
                            if (e is CameraUnavailableException && e.retryable &&
                                retries++ < MAX_REOPEN_RETRIES
                            ) {
                                Log.w(TAG, "Camera $cameraId failed, reopening ($retries)", e)
                                delay(REOPEN_DELAY_MS)
                                continue
                            }
                            Log.e(TAG, "Camera $cameraId preview stopped", e)
                            errorMessage = e.message ?: e.javaClass.simpleName
                            onSettled()
                            break
                        }
                    }
                }
            }
        }

        // Camera unavailable: solid red, same as in saved photos / videos (cause is in Logcat).
        if (spec == null || errorMessage != null) {
            Box(
                Modifier
                    .matchParentSize()
                    .background(Color(PreviewLayout.FAILED_CAMERA_COLOR))
            )
        }
    }
}

// region Camera2 plumbing

private class Camera2PreviewSpec(
    val previewSize: Size,
    val stillSize: Size?,
    val sensorOrientation: Int,
    val isFront: Boolean,
    /** Variable range up to [MAX_FPS]: AE may lower the frame rate in low light (less work). */
    val previewFpsRange: Range<Int>?,
    /** Steadiest range up to [MAX_FPS] for recording (ideally fixed 30). */
    val recordFpsRange: Range<Int>?,
) {
    /** See https://developer.android.com/media/camera/camera2/camera-preview#relative-rotation */
    fun relativeRotation(displayRotationDegrees: Int): Int {
        val sign = if (isFront) 1 else -1
        return (sensorOrientation - displayRotationDegrees * sign + 360) % 360
    }

    /**
     * Preview buffers get the sensor rotation applied by the framework (buffer transform), so
     * in the device's natural (portrait) orientation they are seen with swapped dimensions.
     */
    fun streamInfo(): Camera2StreamInfo {
        val swapped = sensorOrientation % 180 == 90
        return Camera2StreamInfo(
            bufferSize = previewSize,
            uprightWidth = if (swapped) previewSize.height else previewSize.width,
            uprightHeight = if (swapped) previewSize.width else previewSize.height,
        )
    }
}

private fun CameraManager.previewSpec(cameraId: String): Camera2PreviewSpec? = runCatching {
    val characteristics = getCameraCharacteristics(cameraId)
    val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        ?: return@runCatching null
    val sizes = map.getOutputSizes(SurfaceTexture::class.java)?.toList().orEmpty()
    val previewSize = chooseSize(sizes, MAX_PREVIEW_PIXELS) ?: return@runCatching null
    val jpegSizes = map.getOutputSizes(ImageFormat.JPEG)?.toList().orEmpty()
    val fpsRanges = characteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
        ?.filter { it.upper <= MAX_FPS }
        .orEmpty()

    Camera2PreviewSpec(
        previewSize = previewSize,
        stillSize = chooseSize(jpegSizes, MAX_STILL_PIXELS),
        sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0,
        isFront = characteristics.get(CameraCharacteristics.LENS_FACING) ==
                CameraCharacteristics.LENS_FACING_FRONT,
        // Highest max, then widest range (lowest min).
        previewFpsRange = fpsRanges.maxWithOrNull(compareBy({ it.upper }, { -it.lower })),
        // Highest max, then narrowest range (highest min) -> e.g. [30, 30].
        recordFpsRange = fpsRanges.maxWithOrNull(compareBy({ it.upper }, { it.lower })),
    )
}.onFailure { Log.e(TAG, "Failed to read characteristics of $cameraId", it) }.getOrNull()

/** Largest 16:9 size within [maxPixels], else largest size within it, else smallest. */
private fun chooseSize(sizes: List<Size>, maxPixels: Int): Size? {
    val withinBudget = sizes.filter { it.width * it.height <= maxPixels }
    val is16x9 = { s: Size -> s.width * 9 == s.height * 16 || s.height * 9 == s.width * 16 }
    return withinBudget.filter(is16x9).maxByOrNull { it.width * it.height }
        ?: withinBudget.maxByOrNull { it.width * it.height }
        ?: sizes.minByOrNull { it.width * it.height }
}

/** @param retryable transient device / service error: reopening the camera may work. */
private class CameraUnavailableException(
    message: String,
    val retryable: Boolean = false,
) : Exception(message)

private class PreviewConfig(
    val cameraId: String,
    val previewSurface: Surface,
    val spec: Camera2PreviewSpec,
    val handle: Camera2CameraHandle?,
    val stillRotationDegrees: () -> Int,
    val onStreaming: () -> Unit,
)

private sealed interface DeviceEvent {
    class Opened(val device: CameraDevice) : DeviceEvent
    data object Disconnected : DeviceEvent
    class Error(val code: Int) : DeviceEvent
}

/**
 * Camera callbacks (device state, per-frame capture results, OEM statistics hooks, JPEG reader)
 * run on this background thread: on the main looper they cost several % of UI thread time.
 */
private val cameraHandler by lazy {
    Handler(HandlerThread("DualCamera2").apply { start() }.looper)
}
private val cameraExecutor = Executor { cameraHandler.post(it) }

/**
 * Opens the camera, streams preview (plus stills or recording, see [Camera2CameraHandle]) and
 * suspends until cancelled. Throws if the camera can't be opened, or is later disconnected
 * (e.g. evicted because the HAL can't keep both cameras open).
 */
@SuppressLint("MissingPermission") // Only composed after CAMERA permission is granted.
private suspend fun CameraManager.runPreview(config: PreviewConfig) {
    val cameraId = config.cameraId
    val events = Channel<DeviceEvent>(Channel.UNLIMITED)
    val stateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(device: CameraDevice) {
            // Channel closed => caller already gave up, release the device right away.
            if (events.trySend(DeviceEvent.Opened(device)).isFailure) device.close()
        }

        override fun onDisconnected(device: CameraDevice) {
            device.close()
            events.trySend(DeviceEvent.Disconnected)
        }

        override fun onError(device: CameraDevice, error: Int) {
            device.close()
            events.trySend(DeviceEvent.Error(error))
        }
    }

    var device: CameraDevice? = null
    val reader = if (config.handle != null && config.spec.stillSize != null) {
        val size = config.spec.stillSize
        ImageReader.newInstance(size.width, size.height, ImageFormat.JPEG, 2)
    } else {
        null
    }
    try {
        openCamera(cameraId, stateCallback, cameraHandler)

        val openedDevice = when (val event = events.receive()) {
            is DeviceEvent.Opened -> event.device
            else -> throw event.toException(cameraId)
        }
        device = openedDevice
        config.handle?.streamInfo = config.spec.streamInfo()

        coroutineScope {
            // Disconnect / error ends the preview (and cancels the session below).
            launch { throw events.receive().toException(cameraId) }

            val handle = config.handle
            val outputs = if (handle == null) {
                flowOf(null to false)
            } else {
                combine(handle.recordingSurface, handle.stillMode) { recording, still ->
                    recording to still
                }
            }
            // Session = preview + at most one extra output, only while it's needed.
            outputs.collectLatest { (recordingSurface, stillMode) ->
                val stillReader = reader.takeIf { stillMode && recordingSurface == null }
                openedDevice.runSession(config, stillReader, recordingSurface)
            }
        }
    } catch (e: IllegalStateException) {
        // "CameraDevice was already closed": usually evicted because opening the other camera
        // took over. Report the real cause when known.
        throw events.tryReceive().getOrNull()?.toException(cameraId) ?: e
    } finally {
        config.handle?.streamInfo = null
        events.close()
        device?.close() // Also closes the capture session.
        reader?.close()
    }
}

/**
 * Configures one capture session and keeps it running until cancelled:
 * - idle: `[preview]` only;
 * - taking a still: `[preview, JPEG reader]` with still capture attached to the handle;
 * - recording: `[preview, recordingSurface]`.
 * Falls back to preview only if the HAL refuses the extra output.
 */
private suspend fun CameraDevice.runSession(
    config: PreviewConfig,
    reader: ImageReader?,
    recordingSurface: Surface?,
) {
    val preview = config.previewSurface
    val extraOutput = recordingSurface ?: reader?.surface
    val (session, extraConfigured) = try {
        createSession(listOfNotNull(preview, extraOutput)) to (extraOutput != null)
    } catch (e: CameraUnavailableException) {
        if (extraOutput == null) throw e
        // Weak HALs may refuse a second output while another camera is open: keep the preview.
        val what = if (recordingSurface != null) "recording" else "still capture"
        Log.w(TAG, "Camera ${config.cameraId}: preview + $what refused, preview only")
        createSession(listOf(preview)) to false
    }

    val isRecording = recordingSurface != null && extraConfigured
    val template = if (isRecording) CameraDevice.TEMPLATE_RECORD else CameraDevice.TEMPLATE_PREVIEW
    val fpsRange = if (isRecording) config.spec.recordFpsRange else config.spec.previewFpsRange
    val request = createCaptureRequest(template).apply {
        addTarget(preview)
        if (isRecording) addTarget(recordingSurface)
        fpsRange?.let { set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, it) }
        // EIS costs a lot of HAL / GPU work and isn't needed for a dual preview.
        set(
            CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE,
            CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_OFF,
        )
    }.build()
    session.setRepeatingRequest(request, null, cameraHandler)
    config.onStreaming()

    val handle = config.handle
    val stillBlock = if (handle != null && reader != null && extraConfigured && recordingSurface == null) {
        createStillBlock(config, session, reader).also(handle::attach)
    } else {
        null
    }
    try {
        awaitCancellation()
    } finally {
        stillBlock?.let { handle?.detach(it) }
    }
}

private fun CameraDevice.createStillBlock(
    config: PreviewConfig,
    session: CameraCaptureSession,
    reader: ImageReader,
): suspend () -> Bitmap {
    val device = this
    val mutex = Mutex()
    return {
        mutex.withLock {
            val jpeg = withTimeoutOrNull(STILL_CAPTURE_TIMEOUT_MS) {
                session.captureJpeg(device, config.previewSurface, reader)
            } ?: throw CameraUnavailableException("Camera ${config.cameraId} capture timed out")
            withContext(Dispatchers.Default) {
                val bitmap = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)
                    ?: throw CameraUnavailableException("Camera ${config.cameraId}: bad JPEG")
                // No JPEG_ORIENTATION is set, so the JPEG is in sensor orientation.
                DualCameraMedia.rotateAndMirror(
                    bitmap,
                    config.stillRotationDegrees(),
                    config.spec.isFront,
                )
            }
        }
    }
}

/** Fires one still capture into [reader] and returns the JPEG bytes. */
private suspend fun CameraCaptureSession.captureJpeg(
    device: CameraDevice,
    previewSurface: Surface,
    reader: ImageReader,
): ByteArray = suspendCancellableCoroutine { continuation ->
    // Drop any stale frame so we return the one from this request.
    reader.acquireLatestImage()?.close()

    reader.setOnImageAvailableListener({ source ->
        source.setOnImageAvailableListener(null, null)
        val result = runCatching {
            val image = source.acquireNextImage()
                ?: throw CameraUnavailableException("Camera ${device.id}: no image")
            image.use {
                val buffer = it.planes[0].buffer
                ByteArray(buffer.remaining()).also { bytes -> buffer.get(bytes) }
            }
        }
        if (continuation.isActive) {
            result.fold(continuation::resume, continuation::resumeWithException)
        }
    }, cameraHandler)
    continuation.invokeOnCancellation { reader.setOnImageAvailableListener(null, null) }

    val failureCallback = object : CameraCaptureSession.CaptureCallback() {
        override fun onCaptureFailed(
            session: CameraCaptureSession,
            request: CaptureRequest,
            failure: CaptureFailure,
        ) {
            reader.setOnImageAvailableListener(null, null)
            if (continuation.isActive) {
                continuation.resumeWithException(
                    CameraUnavailableException(
                        "Camera ${device.id} capture failed (reason ${failure.reason})"
                    )
                )
            }
        }
    }

    try {
        val request = device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
            addTarget(previewSurface)
            addTarget(reader.surface)
        }.build()
        capture(request, failureCallback, cameraHandler)
    } catch (e: Exception) {
        // CameraAccessException / IllegalStateException: device closed or evicted.
        reader.setOnImageAvailableListener(null, null)
        if (continuation.isActive) continuation.resumeWithException(e)
    }
}

private fun DeviceEvent.toException(cameraId: String) = CameraUnavailableException(
    retryable = this is DeviceEvent.Error && (
        code == CameraDevice.StateCallback.ERROR_CAMERA_DEVICE ||
            code == CameraDevice.StateCallback.ERROR_CAMERA_SERVICE
        ),
    message = when (this) {
        is DeviceEvent.Opened -> "Camera $cameraId reopened unexpectedly"
        // CameraService evicts an open camera when the device can't afford both
        // (e.g. every camera declares resource cost 100 in `dumpsys media.camera`).
        DeviceEvent.Disconnected -> "Camera $cameraId was closed by the system: " +
                "this device can't keep two cameras open at once"
        is DeviceEvent.Error -> "Camera $cameraId error ${errorName(code)}"
    }
)

private fun errorName(code: Int) = when (code) {
    CameraDevice.StateCallback.ERROR_CAMERA_IN_USE -> "CAMERA_IN_USE"
    CameraDevice.StateCallback.ERROR_MAX_CAMERAS_IN_USE -> "MAX_CAMERAS_IN_USE"
    CameraDevice.StateCallback.ERROR_CAMERA_DISABLED -> "CAMERA_DISABLED"
    CameraDevice.StateCallback.ERROR_CAMERA_DEVICE -> "CAMERA_DEVICE"
    CameraDevice.StateCallback.ERROR_CAMERA_SERVICE -> "CAMERA_SERVICE"
    else -> code.toString()
}

private suspend fun CameraDevice.createSession(surfaces: List<Surface>): CameraCaptureSession =
    suspendCancellableCoroutine { continuation ->
        val callback = object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(session: CameraCaptureSession) {
                if (continuation.isActive) continuation.resume(session) else session.close()
            }

            override fun onConfigureFailed(session: CameraCaptureSession) {
                if (continuation.isActive) {
                    continuation.resumeWithException(
                        CameraUnavailableException("Camera $id session configuration failed")
                    )
                }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            createCaptureSession(
                SessionConfiguration(
                    SessionConfiguration.SESSION_REGULAR,
                    surfaces.map { OutputConfiguration(it) },
                    cameraExecutor,
                    callback,
                )
            )
        } else {
            @Suppress("DEPRECATION")
            createCaptureSession(surfaces, callback, cameraHandler)
        }
    }

// endregion

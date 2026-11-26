package com.looper.base.screen.dualcamera.data

import com.looper.base.screen.common.data.DualLayout
import com.looper.base.screen.common.data.PipPosition
import com.looper.base.screen.common.data.pipPosition

enum class DualCaptureMode {
    /** Two independent streams (Preview + ImageCapture per camera), merged into one photo. */
    Photo,

    /** CameraX composition mode: both cameras share one Preview + VideoCapture (PiP video). */
    Video,
}

enum class DualCameraSupport {
    Checking,

    /** Device declares concurrent camera support -> CameraX ConcurrentCamera (preview + capture). */
    Supported,

    /**
     * Both cameras opened directly with Camera2 (preview, photo and composited video in every
     * layout). Used on all devices by default; without concurrent support the HAL may refuse
     * one camera, which is then shown / recorded as red.
     */
    Camera2Fallback,

    /** Device lacks a front or back camera. */
    Unsupported,
}

data class Camera2CameraIds(
    val backId: String,
    val frontId: String,
)

data class DualCameraUiState(
    /**
     * Current camera binding. Stays [DualCaptureMode.Photo] by default (instant shutter); the
     * record button rebinds to [DualCaptureMode.Video] and goes back to Photo when it stops.
     */
    val mode: DualCaptureMode = DualCaptureMode.Photo,
    val support: DualCameraSupport = DualCameraSupport.Checking,
    val camera2Ids: Camera2CameraIds? = null,
    val isCameraBound: Boolean = false,
    /** false: back camera is the main feed. true: front camera is the main feed. */
    val isSwapped: Boolean = false,
    val layout: DualLayout = DualLayout.PipTopEnd,
    /** Window position of [DualLayout.PipCustom], moved by long-pressing and dragging it. */
    val customPipPosition: PipPosition = PipPosition.of(DualLayout.PipTopEnd),
    val isCapturing: Boolean = false,
    val isRecording: Boolean = false,
    val recordedDurationMs: Long = 0L,
) {
    /** Camera2 fallback has no CameraX binding: its viewfinders open the cameras themselves. */
    private val isReady: Boolean
        get() = isCameraBound || support == DualCameraSupport.Camera2Fallback

    /** Swap / layout changes. Disabled during video: the recorded layout is fixed at start. */
    val canEditLayout: Boolean
        get() = mode == DualCaptureMode.Photo && !isCapturing && !isRecording

    /** Where the PiP window sits: the layout's corner or the custom position. */
    val pipPosition: PipPosition
        get() = layout.pipPosition(customPipPosition)

    /** The PiP window can be dragged (custom layout, same rules as other layout changes). */
    val canMovePip: Boolean
        get() = layout == DualLayout.PipCustom && canEditLayout

    val isPhotoEnabled: Boolean
        get() = isReady && mode == DualCaptureMode.Photo && !isCapturing && !isRecording

    /** Enabled to start (from Photo) or to stop an ongoing recording. */
    val isVideoEnabled: Boolean
        get() = !isCapturing && (isRecording || (isReady && mode == DualCaptureMode.Photo))
}

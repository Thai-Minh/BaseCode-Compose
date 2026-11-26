package com.looper.base.screen.dualcamera.data

import com.looper.base.screen.common.data.DualLayout

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

    val isPhotoEnabled: Boolean
        get() = isReady && mode == DualCaptureMode.Photo && !isCapturing && !isRecording

    /** Enabled to start (from Photo) or to stop an ongoing recording. */
    val isVideoEnabled: Boolean
        get() = !isCapturing && (isRecording || (isReady && mode == DualCaptureMode.Photo))
}

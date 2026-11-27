package com.looper.base.repository.dualcamera

import android.net.Uri
import androidx.camera.core.SurfaceRequest
import androidx.lifecycle.LifecycleOwner
import com.looper.base.repository.dualcamera.data.DualPhoto
import com.looper.base.screen.dualcamera.Camera2CameraHandle
import com.looper.base.screen.dualcamera.data.Camera2CameraIds
import com.looper.base.screen.dualcamera.data.DualCaptureMode
import com.looper.base.screen.dualcamera.data.PreviewLayout
import kotlinx.coroutines.flow.StateFlow

/**
 * Cameras of the dual camera screen: still capture, recording and saving into the gallery, with
 * the Camera2 pipeline (default) or CameraX concurrent cameras.
 */
interface IDualCameraRepository {

    /** A recording (Camera2 or CameraX) is running. */
    val isRecording: Boolean

    /** Still capture / recording handles of the screen's Camera2 viewfinders. */
    val camera2Back: Camera2CameraHandle
    val camera2Front: Camera2CameraHandle

    /** A Camera2 recording is running. */
    val isCamera2Recording: Boolean

    /** Back camera preview (photo binding) or the composited PiP preview (video binding). */
    val primarySurfaceRequest: StateFlow<SurfaceRequest?>

    /** Front camera preview, photo binding only. */
    val secondarySurfaceRequest: StateFlow<SurfaceRequest?>

    /**
     * What the device's cameras support, in this order: a front camera, a back camera, and dual
     * (a front and a back camera that can stream at the same time, see [hasConcurrentCameras]).
     * Without dual the screen still opens both cameras with Camera2, but the device may refuse
     * one of them.
     */
    suspend fun checkCameraSupport(): Triple<Boolean, Boolean, Boolean>

    /** First back and front camera ids, or null if the device lacks one of them. */
    fun findCamera2Ids(): Camera2CameraIds?

    /**
     * Takes both shots together and saves them composited like the preview ([layout] is null
     * if the preview was never measured). Best effort: if only one camera delivers, the other
     * slot is painted red ([DualPhoto.isSingleCamera]).
     *
     * [isSingleCamera]: only the main camera is open (the front one when [isSwapped]); its shot
     * is saved with the window filled with [PreviewLayout.FAILED_CAMERA_COLOR], like on screen.
     */
    suspend fun captureCamera2Photo(
        isSwapped: Boolean,
        layout: PreviewLayout?,
        isSingleCamera: Boolean,
    ): Result<DualPhoto>

    /**
     * Starts recording both streams composited like the preview. The returned flow turns true
     * with the first recorded frame, the video's time 0. The encoders are set up off the main
     * thread; cancelling the caller meanwhile discards them.
     *
     * [isSingleCamera]: only the main camera is recorded, the window is filled with
     * [PreviewLayout.FAILED_CAMERA_COLOR] like on screen.
     */
    suspend fun startCamera2Recording(
        layout: PreviewLayout,
        isSwapped: Boolean,
        isSingleCamera: Boolean,
    ): Result<StateFlow<Boolean>>

    /** Finalizes the running recording and saves it into the gallery. */
    suspend fun stopCamera2Recording(): Result<Uri>

    /** CameraX reports a back + front pair that can stream at the same time. */
    suspend fun hasConcurrentCameras(): Boolean

    /**
     * Binds both cameras for [mode]; in video, [isSwapped] picks the camera that fills the
     * composition. Throws if the stream combination isn't supported or a camera is in use.
     */
    suspend fun bindConcurrentCameras(
        lifecycleOwner: LifecycleOwner,
        mode: DualCaptureMode,
        isSwapped: Boolean,
    )

    /** Stops the running recording, unbinds both cameras and clears the previews. */
    fun unbindConcurrentCameras()

    /**
     * Photo binding: fires both shutters together and saves the photo composited like the
     * preview.
     */
    suspend fun captureConcurrentPhoto(isSwapped: Boolean, layout: PreviewLayout?): Result<DualPhoto>

    /**
     * Video binding: records the composited stream into the gallery until
     * [stopConcurrentRecording] (or until the cameras are unbound) and returns the saved video.
     * [onStarted] and [onDuration] (recorded ms) are called on the main thread.
     */
    suspend fun recordConcurrentVideo(
        onStarted: () -> Unit,
        onDuration: (Long) -> Unit,
    ): Result<Uri>

    fun stopConcurrentRecording()

    // endregion

    /** Discards the running recordings (the screen is gone). */
    fun release()
}

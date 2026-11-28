package com.looper.base.repository.reaction

import android.net.Uri
import androidx.camera.core.SurfaceRequest
import androidx.lifecycle.LifecycleOwner
import com.looper.base.screen.common.data.DualAudioMode
import com.looper.base.screen.common.data.DualLayout
import com.looper.base.screen.common.data.PipPosition
import com.looper.base.screen.reaction.data.PickedVideo
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/**
 * Reaction video: the camera records the reaction into a temporary file, then it is composited
 * with the background video and saved into the gallery (`Movies/Reaction`).
 */
interface IReactionRepository {

    /** Preview of the bound camera, for the reaction window's viewfinder. */
    val surfaceRequest: StateFlow<SurfaceRequest?>

    /** Metadata of a picked background video, or null if it isn't a playable video. */
    suspend fun readVideo(uri: Uri): PickedVideo?

    /**
     * Binds the front or back camera (preview + video capture) and suspends until cancelled,
     * then stops the recording and unbinds it. Throws if the camera can't be opened.
     */
    suspend fun bindCamera(lifecycleOwner: LifecycleOwner, useFrontCamera: Boolean)

    /**
     * Records the bound camera into a temporary file until [stopRecording] (or until the camera
     * is unbound) and returns it; pass it to [exportReaction]. [onStarted] (first recorded
     * frame) and [onDuration] (recorded ms) are called on the main thread.
     */
    suspend fun recordReaction(
        onStarted: () -> Unit,
        onDuration: (Long) -> Unit,
    ): Result<File>

    fun stopRecording()

    /**
     * Composites [background] and the [reactionFile] recording like the preview, saves the video
     * into the gallery and deletes [reactionFile]. PiP layouts keep only the full-frame area of
     * the preview: the background's size when it is full frame, else [pipCanvasAspectRatio]
     * (the preview stage's width / height). [audioMode] keeps the full-frame slot's and / or the
     * window's sound, whichever video is in it; a kept sound shorter than the output is followed
     * by silence. [onProgress] receives 0..100 on the main thread.
     */
    suspend fun exportReaction(
        background: PickedVideo,
        reactionFile: File,
        arrangement: DualLayout,
        audioMode: DualAudioMode,
        reactionFullScreen: Boolean,
        pipCanvasAspectRatio: Float,
        pipPosition: PipPosition,
        onProgress: (Int) -> Unit,
    ): Result<Uri>

    /** Discards the running recording (the screen is gone). */
    fun release()
}

package com.looper.base.repository.reaction

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.MirrorMode
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.camera.video.AudioSpec
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.PendingRecording
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.looper.base.repository.runCatchingCancellable
import com.looper.base.screen.common.data.DualAudioMode
import com.looper.base.screen.common.data.DualLayout
import com.looper.base.screen.common.data.PipPosition
import com.looper.base.screen.dualcamera.DualCameraMedia
import com.looper.base.screen.reaction.ReactionExporter
import com.looper.base.screen.reaction.data.PickedVideo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

private const val TAG = "ReactionRepository"
private const val ALBUM_NAME = "Reaction"
private const val FILE_PREFIX = "REACTION_"

/**
 * Holds the screen's bound camera and running recording: one instance per screen (Koin
 * factory), released by [release]. The export is done by [ReactionExporter].
 */
class ReactionRepositoryImpl(
    private val context: Context,
) : IReactionRepository {

    private val _surfaceRequest = MutableStateFlow<SurfaceRequest?>(null)
    override val surfaceRequest: StateFlow<SurfaceRequest?> = _surfaceRequest.asStateFlow()

    private val exporter = ReactionExporter(context)
    private val mainExecutor by lazy { ContextCompat.getMainExecutor(context) }

    private var videoCapture: VideoCapture<Recorder>? = null
    private var activeRecording: Recording? = null

    override suspend fun readVideo(uri: Uri): PickedVideo? =
        withContext(Dispatchers.IO) { PickedVideo.read(context, uri) }

    override suspend fun bindCamera(lifecycleOwner: LifecycleOwner, useFrontCamera: Boolean) {
        val provider = ProcessCameraProvider.awaitInstance(context)
        var ownRequest: SurfaceRequest? = null
        val preview = Preview.Builder()
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
                    .build()
            )
            .build()
            .apply {
                setSurfaceProvider { request ->
                    ownRequest = request
                    _surfaceRequest.value = request
                }
            }
        val recorder = Recorder.Builder()
            .setQualitySelector(
                QualitySelector.from(
                    Quality.HD,
                    FallbackStrategy.lowerQualityOrHigherThan(Quality.SD),
                )
            )
            .setAspectRatio(AspectRatio.RATIO_16_9)
            .withEchoCancellation()
            .build()
        // Recorded like the (mirrored) preview, so the saved reaction matches the screen.
        val capture = VideoCapture.Builder(recorder)
            .setMirrorMode(MirrorMode.MIRROR_MODE_ON_FRONT_ONLY)
            .build()

        try {
            provider.unbindAll()
            provider.bindToLifecycle(
                lifecycleOwner,
                if (useFrontCamera) {
                    CameraSelector.DEFAULT_FRONT_CAMERA
                } else {
                    CameraSelector.DEFAULT_BACK_CAMERA
                },
                preview,
                capture,
            )
            videoCapture = capture
            awaitCancellation()
        } finally {
            stopRecording()
            if (videoCapture === capture) videoCapture = null
            // A rebind (camera switch) may already have published its own request.
            _surfaceRequest.update { current -> if (current === ownRequest) null else current }
            provider.unbind(preview, capture)
        }
    }

    @SuppressLint("MissingPermission") // Audio is only enabled after an explicit permission check.
    override suspend fun recordReaction(
        onStarted: () -> Unit,
        onDuration: (Long) -> Unit,
    ): Result<File> = runCatchingCancellable {
        val capture = checkNotNull(videoCapture) { "The camera is not bound" }
        check(activeRecording == null) { "Already recording" }
        val file = File(context.cacheDir, "reaction_camera_${System.nanoTime()}.mp4")
        try {
            var pending = capture.output.prepareRecording(
                context,
                FileOutputOptions.Builder(file).build(),
            )
            if (canRecordAudio()) {
                pending = pending.withAudioEnabled()
            }
            val finalize = pending.record(onStarted, onDuration)
            if (finalize.hasError()) {
                Log.w(TAG, "Recording finalized with error ${finalize.error}", finalize.cause)
            }
            // Some errors (e.g. the camera closed) still leave a playable file.
            check(file.length() > 0) { "Nothing was recorded" }
            file
        } catch (e: Throwable) {
            file.delete()
            throw e
        }
    }

    override fun stopRecording() {
        activeRecording?.stop()
        activeRecording = null
    }

    override suspend fun exportReaction(
        background: PickedVideo,
        reactionFile: File,
        arrangement: DualLayout,
        audioMode: DualAudioMode,
        reactionFullScreen: Boolean,
        pipCanvasAspectRatio: Float,
        pipPosition: PipPosition,
        onProgress: (Int) -> Unit,
    ): Result<Uri> = runCatchingCancellable {
        try {
            val output = exporter.exportReaction(
                background = background,
                reactionFile = reactionFile,
                arrangement = arrangement,
                audioMode = audioMode,
                reactionFullScreen = reactionFullScreen,
                pipCanvasAspectRatio = pipCanvasAspectRatio,
                pipPosition = pipPosition,
                onProgress = onProgress,
            )
            withContext(Dispatchers.IO) {
                try {
                    DualCameraMedia.saveVideoFile(context, output, ALBUM_NAME, FILE_PREFIX)
                } finally {
                    output.delete()
                }
            }
        } finally {
            reactionFile.delete()
        }
    }

    override fun release() {
        activeRecording?.close()
        activeRecording = null
    }

    /**
     * Starts this recording and suspends until it is finalized ([stopRecording] or the camera
     * being unbound); cancelling the caller closes it.
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
        continuation.invokeOnCancellation { recording.close() }
    }

    private fun canRecordAudio() =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
}

/**
 * Records the microphone with the voice communication source. Unlike the default camcorder
 * source, it runs the platform's acoustic echo canceller (and noise suppressor), which takes the
 * background playing through the speaker out of the recording. Otherwise, when both sounds are
 * kept, the recorded copy of the background is heard as an echo of its own track.
 *
 * [Recorder.Builder.setAudioSource] is still library-restricted, but CameraX 1.6 hands the source
 * to its AudioRecord as is.
 */
@SuppressLint("RestrictedApi")
private fun Recorder.Builder.withEchoCancellation(): Recorder.Builder =
    setAudioSource(AudioSpec.SOURCE_VOICE_COMMUNICATION)

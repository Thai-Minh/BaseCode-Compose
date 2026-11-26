package com.looper.base.screen.reaction

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
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
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
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
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.looper.base.R
import com.looper.base.screen.common.data.DualAudioMode
import com.looper.base.screen.common.data.DualLayout
import com.looper.base.screen.dualcamera.DualCameraMedia
import com.looper.base.screen.reaction.data.PickedVideo
import com.looper.base.screen.reaction.data.ReactionEffect
import com.looper.base.screen.reaction.data.ReactionLayout
import com.looper.base.screen.reaction.data.ReactionUiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val TAG = "ReactionViewModel"
private const val ALBUM_NAME = "Reaction"
private const val FILE_PREFIX = "REACTION_"

/**
 * Reaction recording: the picked background video plays while the front camera records the
 * reaction. On stop, both are composited by [ReactionExporter] and saved into `Movies/Reaction`.
 */
class ReactionViewModel(
    private val app: Application,
) : ViewModel() {

    val uiState: StateFlow<ReactionUiState>
        field = MutableStateFlow(ReactionUiState())

    val effects: SharedFlow<ReactionEffect>
        field = MutableSharedFlow(extraBufferCapacity = 4)

    /** Front camera preview, rendered by the screen's viewfinder. */
    val surfaceRequest: StateFlow<SurfaceRequest?>
        field = MutableStateFlow(null)

    /** Plays the background video on screen (the export reads the file again, untouched). */
    val player: ExoPlayer = ExoPlayer.Builder(app).build().apply {
        addListener(ReactionFrameRedrawer(this))
    }

    private val exporter = ReactionExporter(app)

    /** Width / height of the preview stage: the PiP canvas, reused by the export. */
    private var stageAspectRatio = ReactionLayout.ASPECT_RATIO
    private var videoCapture: VideoCapture<Recorder>? = null
    private var activeRecording: Recording? = null
    private var recordingFile: File? = null

    // region User actions

    fun onBackgroundPicked(uri: Uri?) {
        if (uri == null || !uiState.value.canEdit) return
        viewModelScope.launch {
            val video = withContext(Dispatchers.IO) { PickedVideo.read(app, uri) }
            if (video == null) {
                effects.emit(ReactionEffect.Error(R.string.reaction_background_failed))
                return@launch
            }
            uiState.update { it.copy(background = video) }
            player.setMediaItem(MediaItem.fromUri(uri))
            player.playWhenReady = false
            player.prepare()
        }
    }

    /** The camera permission is granted: show the front camera in the reaction window. */
    fun onCameraEnabled() {
        if (!uiState.value.canEdit) return
        uiState.update { it.copy(isCameraOn = true) }
    }

    /** Front <-> back camera for the reaction (rebinds the camera). */
    fun onSwitchCamera() {
        if (!uiState.value.canSwitchCamera) return
        uiState.update { it.copy(useFrontCamera = !it.useFrontCamera) }
    }

    /** Exchanges the background's and the reaction's places in the current arrangement. */
    fun onSwapClick() {
        if (!uiState.value.canSwap) return
        uiState.update { it.copy(reactionWindowFullScreen = !it.reactionWindowFullScreen) }
    }

    /** The preview stage was measured; PiP exports use the same canvas shape. */
    fun onStageSizeChanged(width: Int, height: Int) {
        if (width > 0 && height > 0) stageAspectRatio = width.toFloat() / height
    }

    fun onArrangementSelected(arrangement: DualLayout) {
        if (!uiState.value.canEdit) return
        uiState.update { it.copy(arrangement = arrangement) }
    }

    fun onAudioModeSelected(mode: DualAudioMode) {
        if (!uiState.value.canEdit) return
        uiState.update { it.copy(audioMode = mode) }
    }

    fun onRecordClick() {
        val state = uiState.value
        when {
            state.isRecording -> stopRecording()
            state.isReadyToRecord && !state.isExporting -> startRecording()
        }
    }

    /** The screen is no longer visible: recording can't go on without the camera. */
    fun onStop() {
        stopRecording()
        player.pause()
    }

    // endregion

    // region Camera

    /** Binds the front or back camera (preview + video) until cancelled. */
    suspend fun bindCamera(lifecycleOwner: LifecycleOwner, useFrontCamera: Boolean) {
        val provider = ProcessCameraProvider.awaitInstance(app)
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
                    surfaceRequest.value = request
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
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open the ${if (useFrontCamera) "front" else "back"} camera", e)
            effects.tryEmit(ReactionEffect.Error(R.string.reaction_camera_failed))
            // Back camera failed: return to the front one; front failed: no camera.
            uiState.update {
                if (useFrontCamera) it.copy(isCameraOn = false) else it.copy(useFrontCamera = true)
            }
        } finally {
            stopRecording()
            if (videoCapture === capture) videoCapture = null
            // A rebind (camera switch) may already have published its own request.
            surfaceRequest.update { current -> if (current === ownRequest) null else current }
            provider.unbind(preview, capture)
        }
    }

    // endregion

    // region Recording

    @SuppressLint("MissingPermission") // Audio is only enabled after an explicit permission check.
    private fun startRecording() {
        val capture = videoCapture ?: run {
            effects.tryEmit(ReactionEffect.Error(R.string.reaction_record_failed))
            return
        }
        if (activeRecording != null) return

        val file = File(app.cacheDir, "reaction_camera_${System.nanoTime()}.mp4")
        var pending = capture.output.prepareRecording(app, FileOutputOptions.Builder(file).build())
        if (hasAudioPermission()) {
            pending = pending.withAudioEnabled()
        }

        player.pause()
        player.seekTo(0)
        recordingFile = file
        activeRecording = try {
            pending.start(ContextCompat.getMainExecutor(app), ::onRecordEvent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording", e)
            recordingFile = null
            file.delete()
            effects.tryEmit(ReactionEffect.Error(R.string.reaction_record_failed))
            return
        }
        uiState.update { it.copy(isRecording = true, recordedDurationMs = 0L) }
    }

    private fun stopRecording() {
        activeRecording?.stop()
        activeRecording = null
    }

    private fun onRecordEvent(event: VideoRecordEvent) {
        when (event) {
            // Start the background together with the camera so both share time 0.
            is VideoRecordEvent.Start -> {
                player.seekTo(0)
                player.play()
            }

            is VideoRecordEvent.Status -> uiState.update {
                it.copy(recordedDurationMs = event.recordingStats.recordedDurationNanos / 1_000_000)
            }

            is VideoRecordEvent.Finalize -> {
                activeRecording = null
                player.pause()
                uiState.update { it.copy(isRecording = false, recordedDurationMs = 0L) }

                val file = recordingFile
                recordingFile = null
                if (event.hasError()) {
                    Log.w(TAG, "Recording finalized with error ${event.error}", event.cause)
                }
                // Some errors (e.g. the camera closed) still leave a playable file.
                if (file != null && file.length() > 0) {
                    exportReaction(file)
                } else {
                    file?.delete()
                    effects.tryEmit(ReactionEffect.Error(R.string.reaction_record_failed))
                }
            }

            else -> Unit
        }
    }

    // endregion

    // region Export

    private fun exportReaction(reactionFile: File) {
        val state = uiState.value
        val background = state.background ?: run {
            reactionFile.delete()
            return
        }
        uiState.update { it.copy(isExporting = true, exportProgress = 0) }
        viewModelScope.launch {
            try {
                val output = exporter.exportReaction(
                    background = background,
                    reactionFile = reactionFile,
                    arrangement = state.arrangement,
                    audioMode = state.audioMode,
                    reactionFullScreen = state.reactionWindowFullScreen,
                    pipCanvasAspectRatio = stageAspectRatio,
                    onProgress = { progress -> uiState.update { it.copy(exportProgress = progress) } },
                )
                val uri = withContext(Dispatchers.IO) {
                    try {
                        DualCameraMedia.saveVideoFile(app, output, ALBUM_NAME, FILE_PREFIX)
                    } finally {
                        output.delete()
                    }
                }
                effects.emit(ReactionEffect.VideoSaved(uri))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Export failed", e)
                effects.emit(ReactionEffect.Error(R.string.reaction_export_failed))
            } finally {
                reactionFile.delete()
                uiState.update { it.copy(isExporting = false, exportProgress = 0) }
                player.seekTo(0)
            }
        }
    }

    // endregion

    private fun hasAudioPermission() =
        ContextCompat.checkSelfPermission(app, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    override fun onCleared() {
        activeRecording?.close()
        activeRecording = null
        recordingFile?.delete()
        player.release()
    }
}

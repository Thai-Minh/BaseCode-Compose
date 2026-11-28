package com.looper.base.screen.reaction

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.camera.core.SurfaceRequest
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.looper.base.R
import com.looper.base.repository.reaction.IReactionRepository
import com.looper.base.screen.common.data.DualAudioMode
import com.looper.base.screen.common.data.DualLayout
import com.looper.base.screen.common.data.PipPosition
import com.looper.base.screen.reaction.data.PickedVideo
import com.looper.base.screen.reaction.data.ReactionEffect
import com.looper.base.screen.reaction.data.ReactionLayout
import com.looper.base.screen.reaction.data.ReactionUiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

private const val TAG = "ReactionViewModel"

/**
 * Reaction recording: the picked background video plays while the camera records the reaction.
 * On stop, [IReactionRepository] composites both and saves the result into `Movies/Reaction`.
 */
class ReactionViewModel(
    app: Application,
    private val repository: IReactionRepository,
) : ViewModel() {

    val uiState: StateFlow<ReactionUiState>
        field = MutableStateFlow(ReactionUiState())

    val effects: SharedFlow<ReactionEffect>
        field = MutableSharedFlow(extraBufferCapacity = 4)

    /**
     * Window position of [DualLayout.PipCustom], moved by long-pressing and dragging it. Kept out
     * of [uiState] so a drag only moves the window: the screen reads it in the layout phase.
     */
    val pipPosition: StateFlow<PipPosition>
        field = MutableStateFlow(PipPosition.BottomLeft)

    /** Camera preview, rendered by the screen's viewfinder. */
    val surfaceRequest: StateFlow<SurfaceRequest?> = repository.surfaceRequest

    /** Plays the background video on screen (the export reads the file again, untouched). */
    val player: ExoPlayer = ExoPlayer.Builder(app).build().apply {
        addListener(ReactionFrameRedrawer(this))
    }

    /** Width / height of the preview stage: the PiP canvas, reused by the export. */
    private var stageAspectRatio = ReactionLayout.ASPECT_RATIO

    init {
        applyBackgroundVolume()
    }

    // region User actions

    fun onBackgroundPicked(uri: Uri?) {
        if (uri == null || !uiState.value.canEdit) return
        viewModelScope.launch {
            val video = repository.readVideo(uri)
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
        // The kept sound follows the slots.
        applyBackgroundVolume()
    }

    /** The preview stage was measured; PiP exports use the same canvas shape. */
    fun onStageSizeChanged(width: Int, height: Int) {
        if (width > 0 && height > 0) stageAspectRatio = width.toFloat() / height
    }

    fun onArrangementSelected(arrangement: DualLayout) {
        if (!uiState.value.canEdit) return
        // The PiP window keeps its position across layout changes.
        uiState.update { it.copy(arrangement = arrangement) }
    }

    /** The PiP window was dragged by fractions of its free space (see [PipPosition]). */
    fun onPipDragged(dx: Float, dy: Float) {
        if (!uiState.value.canMovePip) return
        pipPosition.update { it.movedBy(dx, dy) }
    }

    fun onAudioModeSelected(mode: DualAudioMode) {
        if (!uiState.value.canEdit) return
        uiState.update { it.copy(audioMode = mode) }
        applyBackgroundVolume()
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
        try {
            repository.bindCamera(lifecycleOwner, useFrontCamera)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open the ${if (useFrontCamera) "front" else "back"} camera", e)
            effects.tryEmit(ReactionEffect.Error(R.string.reaction_camera_failed))
            // Back camera failed: return to the front one; front failed: no camera.
            uiState.update {
                if (useFrontCamera) it.copy(isCameraOn = false) else it.copy(useFrontCamera = true)
            }
        }
    }

    // endregion

    // region Recording

    private fun startRecording() {
        // Can't change while recording / exporting, so it is the one to export with.
        val background = uiState.value.background ?: return
        player.pause()
        player.seekTo(0)
        uiState.update { it.copy(isRecording = true, recordedDurationMs = 0L) }

        viewModelScope.launch {
            val recording = repository.recordReaction(
                // Start the background together with the camera so both share time 0.
                onStarted = {
                    player.seekTo(0)
                    player.play()
                },
                onDuration = { durationMs ->
                    // Status events come many times a second; the screen shows whole seconds,
                    // so equal values don't emit and the screen recomposes once a second.
                    uiState.update { it.copy(recordedDurationMs = durationMs / 1_000 * 1_000) }
                },
            )
            player.pause()
            uiState.update { it.copy(isRecording = false, recordedDurationMs = 0L) }
            recording.fold(
                onSuccess = { reactionFile -> exportReaction(background, reactionFile) },
                onFailure = { e ->
                    Log.e(TAG, "Recording failed", e)
                    effects.emit(ReactionEffect.Error(R.string.reaction_record_failed))
                },
            )
        }
    }

    private fun stopRecording() = repository.stopRecording()

    /**
     * The background plays aloud only if the export keeps its sound. The microphone picks up
     * what the speaker plays, so a background heard while recording would end up in a reaction
     * whose sound alone is kept. With both sounds kept it is heard: the recording's echo
     * canceller takes it out, and the export lines up what is left with the background's own
     * sound (see [EchoDelayEstimator]).
     */
    private fun applyBackgroundVolume() {
        val state = uiState.value
        // Not swapped: the background is in the full-frame slot.
        val keepsBackground = state.audioMode.keeps(fullScreenSlot = !state.reactionWindowFullScreen)
        player.volume = if (keepsBackground) 1f else 0f
    }

    // endregion

    // region Export

    private suspend fun exportReaction(background: PickedVideo, reactionFile: File) {
        val state = uiState.value
        uiState.update { it.copy(isExporting = true, exportProgress = 0) }
        try {
            repository.exportReaction(
                background = background,
                reactionFile = reactionFile,
                arrangement = state.arrangement,
                audioMode = state.audioMode,
                reactionFullScreen = state.reactionWindowFullScreen,
                pipCanvasAspectRatio = stageAspectRatio,
                pipPosition = pipPosition.value,
                onProgress = { progress -> uiState.update { it.copy(exportProgress = progress) } },
            ).fold(
                onSuccess = { uri -> effects.emit(ReactionEffect.VideoSaved(uri)) },
                onFailure = { e ->
                    Log.e(TAG, "Export failed", e)
                    effects.emit(ReactionEffect.Error(R.string.reaction_export_failed))
                },
            )
        } finally {
            uiState.update { it.copy(isExporting = false, exportProgress = 0) }
            player.seekTo(0)
        }
    }

    // endregion

    override fun onCleared() {
        repository.release()
        player.release()
    }
}

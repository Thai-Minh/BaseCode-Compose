package com.looper.base.screen.combiner

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.looper.base.R
import com.looper.base.repository.combiner.ICombinerRepository
import com.looper.base.screen.combiner.data.CombinerEffect
import com.looper.base.screen.combiner.data.CombinerUiState
import com.looper.base.screen.combiner.data.CombinerVideo
import com.looper.base.screen.common.data.DualAudioMode
import com.looper.base.screen.common.data.DualLayout
import com.looper.base.screen.common.data.PipPosition
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val TAG = "CombinerViewModel"

/**
 * Video combiner: two videos picked from the device are previewed together, then
 * [ICombinerRepository] composites them into `Movies/Combiner`.
 */
class CombinerViewModel(
    app: Application,
    private val repository: ICombinerRepository,
) : ViewModel() {

    val uiState: StateFlow<CombinerUiState>
        field = MutableStateFlow(CombinerUiState())

    val effects: SharedFlow<CombinerEffect>
        field = MutableSharedFlow(extraBufferCapacity = 4)

    /**
     * Window position of [DualLayout.PipCustom], moved by long-pressing and dragging it. Kept out
     * of [uiState] so a drag only moves the window: the screen reads it in the layout phase.
     */
    val pipPosition: StateFlow<PipPosition>
        field = MutableStateFlow(PipPosition.BottomLeft)

    val mainPlayer: ExoPlayer = ExoPlayer.Builder(app).build()
    val overlayPlayer: ExoPlayer = ExoPlayer.Builder(app).build()
    private val players = listOf(mainPlayer, overlayPlayer)

    private val previewListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            val playing = players.any { it.playWhenReady && it.playbackState != Player.STATE_ENDED }
            uiState.update { it.copy(isPreviewPlaying = playing) }
        }
    }

    private val frameRedrawers = players.map { PausedFrameRedrawer(it) }

    init {
        players.forEachIndexed { index, player ->
            player.addListener(previewListener)
            player.addListener(frameRedrawers[index])
        }
        applyPreviewVolumes()
    }

    // region User actions

    fun onMainPicked(uri: Uri?) = loadVideo(uri, mainPlayer) { copy(mainVideo = it) }

    fun onOverlayPicked(uri: Uri?) = loadVideo(uri, overlayPlayer) { copy(overlayVideo = it) }

    /** Exchanges the main and the overlay video's places in the current arrangement. */
    fun onSwapClick() {
        if (!uiState.value.canSwap) return
        uiState.update { it.copy(overlayFullScreen = !it.overlayFullScreen) }
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
        applyPreviewVolumes()
    }

    /** Plays / pauses both videos together; restarts them once both have ended. */
    fun onPreviewClick() {
        val state = uiState.value
        if (!state.isReady || !state.canEdit) return
        if (state.isPreviewPlaying) {
            pausePreview()
            return
        }
        if (players.all { it.playbackState == Player.STATE_ENDED }) {
            players.forEach { it.seekTo(0) }
        }
        players.forEach { it.play() }
    }

    fun onCombineClick() {
        val state = uiState.value
        val mainVideo = state.mainVideo ?: return
        val overlayVideo = state.overlayVideo ?: return
        if (!state.canEdit) return

        pausePreview()
        uiState.update { it.copy(isExporting = true, exportProgress = 0) }
        viewModelScope.launch {
            try {
                repository.combine(
                    mainVideo = mainVideo,
                    overlayVideo = overlayVideo,
                    overlayFullScreen = state.overlayFullScreen,
                    arrangement = state.arrangement,
                    audioMode = state.audioMode,
                    pipPosition = pipPosition.value,
                    onProgress = { progress ->
                        uiState.update { it.copy(exportProgress = progress) }
                    },
                ).fold(
                    onSuccess = { uri -> effects.emit(CombinerEffect.VideoSaved(uri)) },
                    onFailure = { e ->
                        Log.e(TAG, "Export failed", e)
                        effects.emit(CombinerEffect.Error(R.string.combiner_export_failed))
                    },
                )
            } finally {
                uiState.update { it.copy(isExporting = false, exportProgress = 0) }
            }
        }
    }

    /** The screen is no longer visible. */
    fun onStop() = pausePreview()

    // endregion

    private fun loadVideo(
        uri: Uri?,
        player: ExoPlayer,
        apply: CombinerUiState.(CombinerVideo) -> CombinerUiState,
    ) {
        if (uri == null || !uiState.value.canEdit) return
        viewModelScope.launch {
            val video = repository.readVideo(uri)
            if (video == null) {
                effects.emit(CombinerEffect.Error(R.string.combiner_video_failed))
                return@launch
            }
            pausePreview()
            uiState.update { it.apply(video) }
            player.setMediaItem(MediaItem.fromUri(uri))
            player.prepare()
            // Keep both videos in step: a new video restarts the preview from the start.
            players.forEach { it.seekTo(0) }
        }
    }

    private fun pausePreview() = players.forEach { it.pause() }

    /** The preview plays the sound the export will keep. */
    private fun applyPreviewVolumes() {
        val mode = uiState.value.audioMode
        mainPlayer.volume = if (mode.keepsMain) 1f else 0f
        overlayPlayer.volume = if (mode.keepsSecondary) 1f else 0f
    }

    override fun onCleared() {
        players.forEachIndexed { index, player ->
            player.removeListener(previewListener)
            player.removeListener(frameRedrawers[index])
            player.release()
        }
    }
}

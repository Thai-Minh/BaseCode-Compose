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
import com.looper.base.screen.combiner.data.CombinerEffect
import com.looper.base.screen.combiner.data.CombinerUiState
import com.looper.base.screen.combiner.data.CombinerVideo
import com.looper.base.screen.common.data.DualAudioMode
import com.looper.base.screen.common.data.DualLayout
import com.looper.base.screen.common.data.PipPosition
import com.looper.base.screen.dualcamera.DualCameraMedia
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "CombinerViewModel"
private const val ALBUM_NAME = "Combiner"
private const val FILE_PREFIX = "COMBINE_"

/**
 * Video combiner: two videos picked from the device are previewed together and composited by
 * [CombinerExporter] into `Movies/Combiner`.
 */
class CombinerViewModel(
    private val app: Application,
) : ViewModel() {

    val uiState: StateFlow<CombinerUiState>
        field = MutableStateFlow(CombinerUiState())

    val effects: SharedFlow<CombinerEffect>
        field = MutableSharedFlow(extraBufferCapacity = 4)

    val mainPlayer: ExoPlayer = ExoPlayer.Builder(app).build()
    val overlayPlayer: ExoPlayer = ExoPlayer.Builder(app).build()
    private val players = listOf(mainPlayer, overlayPlayer)

    private val exporter = CombinerExporter(app)

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
        uiState.update {
            it.copy(
                arrangement = arrangement,
                // The movable window starts where the previous corner layout had it.
                customPipPosition = if (arrangement == DualLayout.PipCustom) {
                    PipPosition.whenSwitchingFrom(it.arrangement, it.customPipPosition)
                } else {
                    it.customPipPosition
                },
            )
        }
    }

    /** The custom PiP window was dragged by fractions of its free space (see [PipPosition]). */
    fun onPipDragged(dx: Float, dy: Float) {
        if (!uiState.value.canMovePip) return
        uiState.update { it.copy(customPipPosition = it.customPipPosition.movedBy(dx, dy)) }
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
        val full = state.fullVideo ?: return
        val window = state.windowVideo ?: return
        if (!state.canEdit) return

        // v1 = the main video's sound, v2 = the overlay's, wherever each one is shown.
        val keepsMainAudio = state.audioMode.keepsMain
        val keepsOverlayAudio = state.audioMode.keepsSecondary

        pausePreview()
        uiState.update { it.copy(isExporting = true, exportProgress = 0) }
        viewModelScope.launch {
            try {
                val output = exporter.export(
                    firstWidth = full.width,
                    firstHeight = full.height,
                    full = full.toSource(
                        if (state.overlayFullScreen) keepsOverlayAudio else keepsMainAudio
                    ),
                    window = window.toSource(
                        if (state.overlayFullScreen) keepsMainAudio else keepsOverlayAudio
                    ),
                    windowAspectRatio = window.aspectRatio,
                    arrangement = state.arrangement,
                    pipPosition = state.pipPosition,
                    onProgress = { progress -> uiState.update { it.copy(exportProgress = progress) } },
                )
                val uri = withContext(Dispatchers.IO) {
                    try {
                        DualCameraMedia.saveVideoFile(app, output, ALBUM_NAME, FILE_PREFIX)
                    } finally {
                        output.delete()
                    }
                }
                effects.emit(CombinerEffect.VideoSaved(uri))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Export failed", e)
                effects.emit(CombinerEffect.Error(R.string.combiner_export_failed))
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
            val video = withContext(Dispatchers.IO) { CombinerVideo.read(app, uri) }
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

    private fun CombinerVideo.toSource(keepAudio: Boolean) =
        CombinerExporter.Source(uri, durationUs, keepAudio)

    override fun onCleared() {
        players.forEachIndexed { index, player ->
            player.removeListener(previewListener)
            player.removeListener(frameRedrawers[index])
            player.release()
        }
    }
}

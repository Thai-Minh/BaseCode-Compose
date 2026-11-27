package com.looper.base.screen.combiner.data

import com.looper.base.screen.common.data.DualAudioMode
import com.looper.base.screen.common.data.DualLayout
import com.looper.base.screen.common.data.PipPosition

/**
 * Video combiner: a main video and an overlay video, both picked from the device, arranged by
 * [arrangement]: one full frame and the other in a window movable anywhere over it (1/3 of the
 * width at its own aspect ratio), or one per half of the frame.
 */
data class CombinerUiState(
    val mainVideo: CombinerVideo? = null,
    val overlayVideo: CombinerVideo? = null,
    /** false: main video first (full frame / top / left); true: the overlay first. */
    val overlayFullScreen: Boolean = false,
    val arrangement: DualLayout = DualLayout.PipCustom,
    /** Window position of [DualLayout.PipCustom], moved by long-pressing and dragging it. */
    val pipPosition: PipPosition = PipPosition.BottomLeft,
    val audioMode: DualAudioMode = DualAudioMode.Both,
    /** Both videos are playing (or the longer one is, the shorter one holding its last frame). */
    val isPreviewPlaying: Boolean = false,
    val isExporting: Boolean = false,
    /** Export progress in percent, 0..100. */
    val exportProgress: Int = 0,
) {
    val canEdit: Boolean get() = !isExporting

    /** Both videos are chosen: the controls are shown. */
    val isReady: Boolean get() = mainVideo != null && overlayVideo != null

    val canSwap: Boolean get() = isReady && canEdit

    /** The PiP window can be dragged (custom layout, not while saving). */
    val canMovePip: Boolean
        get() = arrangement == DualLayout.PipCustom && canEdit

    /** The first video: full frame in PiP, top / left half in the split layouts. */
    val fullVideo: CombinerVideo? get() = if (overlayFullScreen) overlayVideo else mainVideo

    /** The second video: PiP window, bottom / right half in the split layouts. */
    val windowVideo: CombinerVideo? get() = if (overlayFullScreen) mainVideo else overlayVideo

    /** Width / height of the first slot: its video's, or 9:16 for the placeholder. */
    val fullAspectRatio: Float
        get() = fullVideo?.aspectRatio ?: CombinerLayout.PLACEHOLDER_ASPECT_RATIO

    /** Width / height of the second slot: its video's, or 9:16 for the placeholder. */
    val windowAspectRatio: Float
        get() = windowVideo?.aspectRatio ?: CombinerLayout.PLACEHOLDER_ASPECT_RATIO

    /**
     * Width / height of the whole canvas, or null to fill the available space (PiP before the
     * full-frame video is picked).
     */
    val canvasAspectRatio: Float?
        get() = if (arrangement.isPip && fullVideo == null) {
            null
        } else {
            CombinerLayout.canvasAspectRatio(arrangement, fullAspectRatio, windowAspectRatio)
        }
}

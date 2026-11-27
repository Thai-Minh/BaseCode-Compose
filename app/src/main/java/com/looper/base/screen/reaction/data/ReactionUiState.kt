package com.looper.base.screen.reaction.data

import com.looper.base.screen.common.data.DualAudioMode
import com.looper.base.screen.common.data.DualLayout
import com.looper.base.screen.common.data.PipPosition

data class ReactionUiState(
    val background: PickedVideo? = null,
    /** The user asked for the reaction camera (and granted the permission). */
    val isCameraOn: Boolean = false,
    /** Front (default) or back camera for the reaction. */
    val useFrontCamera: Boolean = true,
    /** false: background first (full frame / top / left); true: the reaction first. */
    val reactionWindowFullScreen: Boolean = false,
    val arrangement: DualLayout = DualLayout.PipCustom,
    /** Window position of [DualLayout.PipCustom], moved by long-pressing and dragging it. */
    val pipPosition: PipPosition = PipPosition.BottomLeft,
    val audioMode: DualAudioMode = DualAudioMode.Both,
    val isRecording: Boolean = false,
    val recordedDurationMs: Long = 0L,
    val isExporting: Boolean = false,
    /** Export progress in percent, 0..100. */
    val exportProgress: Int = 0,
) {
    /** Background, camera, layout and audio can only change while idle. */
    val canEdit: Boolean get() = !isRecording && !isExporting

    /** Both inputs are chosen: the start/stop button is shown. */
    val isReadyToRecord: Boolean get() = background != null && isCameraOn

    val canSwitchCamera: Boolean get() = isCameraOn && canEdit

    /** Swapping needs both videos (otherwise a placeholder would fill the frame). */
    val canSwap: Boolean get() = isReadyToRecord && canEdit

    /** Width / height of the PiP window for the current layout. */
    val windowAspectRatio: Float?
        get() = ReactionLayout.windowAspectRatio(reactionWindowFullScreen, background)

    /** Width / height of the full-frame video in PiP layouts, or null when it fills the frame. */
    val fullAspectRatio: Float?
        get() = ReactionLayout.fullAspectRatio(reactionWindowFullScreen, background)

    /** The PiP window can be dragged (custom layout, not while recording / saving). */
    val canMovePip: Boolean
        get() = arrangement == DualLayout.PipCustom && canEdit

    /** Width / height of the whole canvas, or null to fill the stage (PiP layouts). */
    val canvasAspectRatio: Float?
        get() = ReactionLayout.splitCanvasAspectRatio(arrangement)
}

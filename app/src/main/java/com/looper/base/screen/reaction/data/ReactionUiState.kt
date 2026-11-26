package com.looper.base.screen.reaction.data

import android.net.Uri
import androidx.annotation.StringRes

/** Where the reaction window sits on top of the background video. */
enum class ReactionCorner(val isStart: Boolean, val isTop: Boolean) {
    TopStart(isStart = true, isTop = true),
    TopEnd(isStart = false, isTop = true),
    BottomStart(isStart = true, isTop = false),
    BottomEnd(isStart = false, isTop = false),
}

/** Which audio ends up in the exported video. */
enum class ReactionAudioMode(val keepsBackground: Boolean, val keepsReaction: Boolean) {
    /** v1: only the background video's sound. */
    Background(keepsBackground = true, keepsReaction = false),

    /** v2: only the reaction (microphone) sound. */
    Reaction(keepsBackground = false, keepsReaction = true),

    /** v1 + v2 mixed. */
    Both(keepsBackground = true, keepsReaction = true),
}

/** The picked background video; [width] x [height] is the upright (displayed) size. */
data class BackgroundVideo(
    val uri: Uri,
    val width: Int,
    val height: Int,
    val durationUs: Long,
) {
    val aspectRatio: Float get() = width.toFloat() / height
}

data class ReactionUiState(
    val background: BackgroundVideo? = null,
    /** The user asked for the reaction camera (and granted the permission). */
    val isCameraOn: Boolean = false,
    /** Front (default) or back camera for the reaction. */
    val useFrontCamera: Boolean = true,
    /** false: background full frame + reaction window; true: the opposite. */
    val reactionWindowFullScreen: Boolean = false,
    val corner: ReactionCorner = ReactionCorner.BottomStart,
    val audioMode: ReactionAudioMode = ReactionAudioMode.Both,
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

    /** Width / height of the corner window for the current layout. */
    val windowAspectRatio: Float
        get() = ReactionLayout.windowAspectRatio(reactionWindowFullScreen, background)
}

sealed interface ReactionEffect {
    data class VideoSaved(val uri: Uri) : ReactionEffect
    data class Error(@StringRes val messageRes: Int) : ReactionEffect
}

/**
 * Geometry of the reaction window, shared by the screen and the exporter so the saved video
 * looks like the preview.
 */
object ReactionLayout {
    /** Reaction width relative to the background video's width. */
    const val WIDTH_FRACTION = 1f / 3f

    /** Camera window width / height (portrait 9:16). */
    const val ASPECT_RATIO = 9f / 16f

    /** Distance from the background's edges, relative to its width. */
    const val MARGIN_FRACTION = 0.03f

    /** Margin in pixels for a background [mainWidth] wide. */
    fun margin(mainWidth: Float): Float = mainWidth * MARGIN_FRACTION

    /**
     * Window aspect ratio: the camera is shown at 9:16; the background video (when swapped into
     * the window) keeps its own aspect ratio.
     */
    fun windowAspectRatio(isSwapped: Boolean, background: BackgroundVideo?): Float =
        if (isSwapped && background != null) background.aspectRatio else ASPECT_RATIO

    /**
     * Window size (width, height) inside a [mainWidth] x [mainHeight] canvas: 1/3 of its width at
     * [aspectRatio] (width / height), shrunk if the canvas is too short for it.
     */
    fun windowSize(
        mainWidth: Float,
        mainHeight: Float,
        aspectRatio: Float = ASPECT_RATIO,
    ): Pair<Float, Float> {
        var width = mainWidth * WIDTH_FRACTION
        var height = width / aspectRatio
        val maxHeight = mainHeight - 2 * margin(mainWidth)
        if (height > maxHeight) {
            height = maxHeight
            width = height * aspectRatio
        }
        return width to height
    }
}

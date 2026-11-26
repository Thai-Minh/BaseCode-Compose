package com.looper.base.screen.reaction.data

import com.looper.base.screen.common.data.DualLayout

/**
 * Geometry of the reaction layouts, shared by the screen and the exporter so the saved video
 * looks like the preview.
 */
object ReactionLayout {
    /** PiP window width relative to the canvas width. */
    const val WIDTH_FRACTION = 1f / 3f

    /** Camera window width / height (portrait 9:16). */
    const val ASPECT_RATIO = 9f / 16f

    /** PiP distance from the canvas' edges, relative to its width. */
    const val MARGIN_FRACTION = 0.03f

    /** Canvas width / height of [DualLayout.SplitVertical]: a portrait 9:16 frame. */
    const val SPLIT_VERTICAL_ASPECT_RATIO = 9f / 16f

    /** Canvas width / height of [DualLayout.SplitHorizontal]: two 9:16 halves. */
    const val SPLIT_HORIZONTAL_ASPECT_RATIO = 2 * 9f / 16f

    /** Margin in pixels for a canvas [mainWidth] wide. */
    fun margin(mainWidth: Float): Float = mainWidth * MARGIN_FRACTION

    /**
     * PiP window aspect ratio: the camera is shown at 9:16; the background video (when swapped
     * into the window) keeps its own aspect ratio.
     */
    fun windowAspectRatio(isSwapped: Boolean, background: PickedVideo?): Float =
        if (isSwapped && background != null) background.aspectRatio else ASPECT_RATIO

    /**
     * Canvas width / height: the background's in PiP layouts (null until it is picked), fixed
     * for the split layouts.
     */
    fun canvasAspectRatio(arrangement: DualLayout, background: PickedVideo?): Float? =
        when (arrangement) {
            DualLayout.SplitVertical -> SPLIT_VERTICAL_ASPECT_RATIO
            DualLayout.SplitHorizontal -> SPLIT_HORIZONTAL_ASPECT_RATIO
            else -> background?.aspectRatio
        }

    /**
     * Where the first and the second video go in a [canvasWidth] x [canvasHeight] canvas.
     * [windowAspectRatio] is the PiP window's width / height (unused by the split layouts).
     */
    fun slots(
        canvasWidth: Float,
        canvasHeight: Float,
        arrangement: DualLayout,
        windowAspectRatio: Float,
    ): Pair<ReactionRect, ReactionRect> = when (arrangement) {
        DualLayout.SplitVertical -> {
            val half = canvasHeight / 2
            ReactionRect(0f, 0f, canvasWidth, half) to
                ReactionRect(0f, half, canvasWidth, canvasHeight - half)
        }

        DualLayout.SplitHorizontal -> {
            val half = canvasWidth / 2
            ReactionRect(0f, 0f, half, canvasHeight) to
                ReactionRect(half, 0f, canvasWidth - half, canvasHeight)
        }

        else -> {
            val (width, height) = windowSize(canvasWidth, canvasHeight, windowAspectRatio)
            val margin = margin(canvasWidth)
            val left = if (arrangement.isStart) margin else canvasWidth - margin - width
            val top = if (arrangement.isTop) margin else canvasHeight - margin - height
            ReactionRect(0f, 0f, canvasWidth, canvasHeight) to
                ReactionRect(left, top, width, height)
        }
    }

    /**
     * PiP window size (width, height) inside a [mainWidth] x [mainHeight] canvas: 1/3 of its
     * width at [aspectRatio] (width / height), shrunk if the canvas is too short for it.
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

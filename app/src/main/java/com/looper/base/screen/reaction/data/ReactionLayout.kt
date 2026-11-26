package com.looper.base.screen.reaction.data

import com.looper.base.screen.common.data.DualLayout

/**
 * Geometry of the reaction layouts, shared by the screen and the exporter so the saved video
 * looks like the preview.
 *
 * PiP layouts use the whole stage as the canvas: the full-frame camera fills it, a full-frame
 * video is fitted at its original aspect ratio (centered, empty bands around it). The window sits
 * inside that full-frame area, with margins relative to it. The camera window follows the
 * full-frame area's orientation: 9:16 and 1/3 of its width when it is portrait, 3:4 and 1/2 of
 * its height when it is landscape (the camera is center-cropped into it). A video window is 1/3
 * of its width at the video's own aspect ratio.
 */
object ReactionLayout {
    /** PiP window width relative to the full-frame area's width. */
    const val WIDTH_FRACTION = 1f / 3f

    /** Camera PiP window height relative to a landscape full-frame area's height. */
    const val CAMERA_HEIGHT_FRACTION = 1f / 2f

    /**
     * Camera width / height (portrait 9:16): the camera window over a portrait full frame, and
     * the stage before it is measured.
     */
    const val ASPECT_RATIO = 9f / 16f

    /** Camera window width / height over a landscape full frame (3:4). */
    const val LANDSCAPE_CAMERA_ASPECT_RATIO = 3f / 4f

    /** PiP distance from the full-frame area's edges, relative to its width. */
    const val MARGIN_FRACTION = 0.03f

    /** Canvas width / height of [DualLayout.SplitVertical]: a portrait 9:16 frame. */
    const val SPLIT_VERTICAL_ASPECT_RATIO = 9f / 16f

    /** Canvas width / height of [DualLayout.SplitHorizontal]: two 9:16 halves. */
    const val SPLIT_HORIZONTAL_ASPECT_RATIO = 2 * 9f / 16f

    /** Margin in pixels for a full-frame area [mainWidth] wide. */
    fun margin(mainWidth: Float): Float = mainWidth * MARGIN_FRACTION

    /**
     * PiP window aspect ratio: the background video's own when it is in the window, null for the
     * camera window (shaped by the full frame's orientation, see [windowSize]).
     */
    fun windowAspectRatio(isSwapped: Boolean, background: PickedVideo?): Float? =
        if (isSwapped && background != null) background.aspectRatio else null

    /**
     * Aspect ratio of the full-frame video in PiP layouts, or null when the full frame is
     * filled (the camera, or the placeholder before a background is picked).
     */
    fun fullAspectRatio(isSwapped: Boolean, background: PickedVideo?): Float? =
        if (isSwapped) null else background?.aspectRatio

    /**
     * Canvas width / height of the split layouts; null for PiP layouts, whose canvas is the
     * whole stage.
     */
    fun splitCanvasAspectRatio(arrangement: DualLayout): Float? = when (arrangement) {
        DualLayout.SplitVertical -> SPLIT_VERTICAL_ASPECT_RATIO
        DualLayout.SplitHorizontal -> SPLIT_HORIZONTAL_ASPECT_RATIO
        else -> null
    }

    /**
     * Where the first and the second video go in a [canvasWidth] x [canvasHeight] canvas.
     * PiP only: [fullAspectRatio] fits the full-frame video at that aspect ratio (null fills the
     * canvas) and [windowAspectRatio] is the window's width / height (null for the camera
     * window); the window is placed in a corner of the full-frame area and sized relative to it.
     */
    fun slots(
        canvasWidth: Float,
        canvasHeight: Float,
        arrangement: DualLayout,
        fullAspectRatio: Float?,
        windowAspectRatio: Float?,
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
            val full = fullAspectRatio
                ?.let { fit(canvasWidth, canvasHeight, it) }
                ?: ReactionRect(0f, 0f, canvasWidth, canvasHeight)
            val (width, height) = windowSize(full.width, full.height, windowAspectRatio)
            val margin = margin(full.width)
            val left = if (arrangement.isStart) {
                full.left + margin
            } else {
                full.left + full.width - margin - width
            }
            val top = if (arrangement.isTop) {
                full.top + margin
            } else {
                full.top + full.height - margin - height
            }
            full to ReactionRect(left, top, width, height)
        }
    }

    /** The largest [aspectRatio] rect centered in a [canvasWidth] x [canvasHeight] canvas. */
    private fun fit(canvasWidth: Float, canvasHeight: Float, aspectRatio: Float): ReactionRect {
        val (width, height) = if (canvasWidth / canvasHeight > aspectRatio) {
            canvasHeight * aspectRatio to canvasHeight
        } else {
            canvasWidth to canvasWidth / aspectRatio
        }
        return ReactionRect((canvasWidth - width) / 2, (canvasHeight - height) / 2, width, height)
    }

    /**
     * PiP window size (width, height) inside a [mainWidth] x [mainHeight] full-frame area:
     * - camera ([aspectRatio] null) over a portrait area: 9:16, 1/3 of its width;
     * - camera over a landscape (or square) area: 3:4, 1/2 of its height;
     * - video: 1/3 of its width at [aspectRatio] (width / height).
     * Shrunk (keeping its aspect ratio) only if it wouldn't fit inside the area's margins.
     */
    fun windowSize(
        mainWidth: Float,
        mainHeight: Float,
        aspectRatio: Float?,
    ): Pair<Float, Float> {
        var (width, height) = when {
            aspectRatio != null -> {
                val width = mainWidth * WIDTH_FRACTION
                width to width / aspectRatio
            }

            mainWidth >= mainHeight -> {
                val height = mainHeight * CAMERA_HEIGHT_FRACTION
                height * LANDSCAPE_CAMERA_ASPECT_RATIO to height
            }

            else -> {
                val width = mainWidth * WIDTH_FRACTION
                width to width / ASPECT_RATIO
            }
        }
        val margin = margin(mainWidth)
        val scale = minOf(
            1f,
            (mainWidth - 2 * margin) / width,
            (mainHeight - 2 * margin) / height,
        )
        width *= scale
        height *= scale
        return width to height
    }
}

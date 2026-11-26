package com.looper.base.screen.combiner.data

import com.looper.base.screen.common.data.DualLayout

/**
 * Geometry of the combiner layouts, shared by the screen and the exporter so the saved video
 * looks like the preview. Every video keeps its original aspect ratio (nothing is cropped).
 */
object CombinerLayout {
    /** PiP window width relative to the canvas width. */
    const val WIDTH_FRACTION = 1f / 3f

    /** Width / height of an empty slot's placeholder (portrait 9:16). */
    const val PLACEHOLDER_ASPECT_RATIO = 9f / 16f

    /** PiP distance from the canvas' edges, relative to its width. */
    const val MARGIN_FRACTION = 0.03f

    /** Margin in pixels for a canvas [mainWidth] wide. */
    fun margin(mainWidth: Float): Float = mainWidth * MARGIN_FRACTION

    /**
     * Canvas width / height for videos of [firstAspectRatio] and [secondAspectRatio]
     * (width / height):
     * - PiP: the first video's;
     * - split vertical: each video in half of the height, as wide as the wider one;
     * - split horizontal: each video in half of the width, as tall as the taller one.
     */
    fun canvasAspectRatio(
        arrangement: DualLayout,
        firstAspectRatio: Float,
        secondAspectRatio: Float,
    ): Float = when (arrangement) {
        DualLayout.SplitVertical -> maxOf(firstAspectRatio, secondAspectRatio) / 2
        DualLayout.SplitHorizontal -> 2 * minOf(firstAspectRatio, secondAspectRatio)
        else -> firstAspectRatio
    }

    /**
     * Where the first and the second video go in a [canvasWidth] x [canvasHeight] canvas of
     * [canvasAspectRatio]: each slot has its video's aspect ratio. In the split layouts each slot
     * is half of the canvas, centered across it (the narrower / shorter video gets empty bands).
     */
    fun slots(
        canvasWidth: Float,
        canvasHeight: Float,
        arrangement: DualLayout,
        firstAspectRatio: Float,
        secondAspectRatio: Float,
    ): Pair<CombinerRect, CombinerRect> = when (arrangement) {
        DualLayout.SplitVertical -> {
            val half = canvasHeight / 2
            val firstWidth = (half * firstAspectRatio).coerceAtMost(canvasWidth)
            val secondWidth = ((canvasHeight - half) * secondAspectRatio).coerceAtMost(canvasWidth)
            CombinerRect((canvasWidth - firstWidth) / 2, 0f, firstWidth, half) to
                CombinerRect((canvasWidth - secondWidth) / 2, half, secondWidth, canvasHeight - half)
        }

        DualLayout.SplitHorizontal -> {
            val half = canvasWidth / 2
            val firstHeight = (half / firstAspectRatio).coerceAtMost(canvasHeight)
            val secondHeight = ((canvasWidth - half) / secondAspectRatio).coerceAtMost(canvasHeight)
            CombinerRect(0f, (canvasHeight - firstHeight) / 2, half, firstHeight) to
                CombinerRect(half, (canvasHeight - secondHeight) / 2, canvasWidth - half, secondHeight)
        }

        else -> {
            val (width, height) = windowSize(canvasWidth, canvasHeight, secondAspectRatio)
            val margin = margin(canvasWidth)
            val left = if (arrangement.isStart) margin else canvasWidth - margin - width
            val top = if (arrangement.isTop) margin else canvasHeight - margin - height
            CombinerRect(0f, 0f, canvasWidth, canvasHeight) to
                CombinerRect(left, top, width, height)
        }
    }

    /**
     * PiP window size (width, height) inside a [mainWidth] x [mainHeight] canvas: 1/3 of its
     * width at [aspectRatio] (width / height), shrunk if the canvas is too short for it.
     */
    fun windowSize(mainWidth: Float, mainHeight: Float, aspectRatio: Float): Pair<Float, Float> {
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

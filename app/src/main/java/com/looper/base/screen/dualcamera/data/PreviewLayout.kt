package com.looper.base.screen.dualcamera.data

/** How the two camera feeds are arranged, on screen and in saved photos / videos. */
enum class DualLayout {
    /** Two equal halves: main on top, second at the bottom. */
    SplitVertical,

    /**
     * Two equal halves side by side: main on the left, second on the right. Together they fill
     * the width; each half keeps [PreviewLayout.SPLIT_HORIZONTAL_ASPECT_RATIO] and the pair is
     * vertically centered.
     */
    SplitHorizontal,

    /** Main full screen, second as a small window in a corner. */
    PipTopStart,
    PipTopEnd,
    PipBottomStart,
    PipBottomEnd;

    val isPip: Boolean
        get() = this != SplitVertical && this != SplitHorizontal
}

/** Rectangle in pixels, relative to the viewport's top-left corner. */
data class LayoutRect(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
) {
    val right: Float get() = left + width
    val bottom: Float get() = top + height
}

/**
 * On-screen geometry of the dual preview, in pixels, so saved photos / videos reproduce exactly
 * what the user saw.
 *
 * [main] is the camera drawn first (full screen in PiP layouts), [second] is drawn on top of it
 * with [secondCornerRadius] (0 for split layouts).
 */
data class PreviewLayout(
    val viewportWidth: Float,
    val viewportHeight: Float,
    val main: LayoutRect,
    val second: LayoutRect,
    val secondCornerRadius: Float,
) {
    /**
     * Same layout restricted to the area the feeds cover (e.g. side by side halves don't fill
     * the screen height): saved photos / videos contain no empty bands.
     */
    fun croppedToContent(): PreviewLayout {
        val contentLeft = minOf(main.left, second.left).coerceAtLeast(0f)
        val contentTop = minOf(main.top, second.top).coerceAtLeast(0f)
        val contentRight = maxOf(main.right, second.right).coerceAtMost(viewportWidth)
        val contentBottom = maxOf(main.bottom, second.bottom).coerceAtMost(viewportHeight)
        if (contentRight <= contentLeft || contentBottom <= contentTop) return this
        fun LayoutRect.shifted() = copy(left = left - contentLeft, top = top - contentTop)
        return copy(
            viewportWidth = contentRight - contentLeft,
            viewportHeight = contentBottom - contentTop,
            main = main.shifted(),
            second = second.shifted(),
        )
    }

    companion object {
        /** Width / height of each half in [DualLayout.SplitHorizontal]: the camera's portrait frame. */
        const val SPLIT_HORIZONTAL_ASPECT_RATIO = 9f / 16f

        /** PiP width relative to the viewport width. */
        const val PIP_WIDTH_FRACTION = 1f / 3f

        /** PiP margin from the screen edges, relative to the viewport width. */
        const val PIP_MARGIN_FRACTION = 0.03f

        /** PiP width / height. */
        const val PIP_ASPECT_RATIO = 3f / 4f

        /** PiP corner radius relative to the PiP width. */
        const val PIP_CORNER_FRACTION = 0.1f

        /** Fill color of a camera that couldn't deliver frames, on screen and in the output. */
        const val FAILED_CAMERA_COLOR = 0xFFFF0000.toInt()

        /** Layout used when the preview was never measured: PiP in the top-end corner. */
        fun default(viewportWidth: Float, viewportHeight: Float): PreviewLayout {
            val pipWidth = viewportWidth * PIP_WIDTH_FRACTION
            val margin = viewportWidth * PIP_MARGIN_FRACTION
            return PreviewLayout(
                viewportWidth = viewportWidth,
                viewportHeight = viewportHeight,
                main = LayoutRect(0f, 0f, viewportWidth, viewportHeight),
                second = LayoutRect(
                    left = viewportWidth - margin - pipWidth,
                    top = margin,
                    width = pipWidth,
                    height = pipWidth / PIP_ASPECT_RATIO,
                ),
                secondCornerRadius = pipWidth * PIP_CORNER_FRACTION,
            )
        }
    }
}

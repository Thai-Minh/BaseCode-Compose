package com.looper.base.screen.common.data

/**
 * Where a PiP window sits inside its full frame, as fractions of the space it can move in (the
 * full frame minus the window itself), so the same position fits any frame size (preview and
 * export):
 * - [x]: 0 = against the left edge, 1 = against the right edge;
 * - [y]: 0 = against the top edge, 1 = against the bottom edge.
 */
data class PipPosition(val x: Float, val y: Float) {

    /** Moved by fractions of the free space, kept inside the frame. */
    fun movedBy(dx: Float, dy: Float): PipPosition =
        PipPosition((x + dx).coerceIn(0f, 1f), (y + dy).coerceIn(0f, 1f))

    companion object {
        val TopLeft = PipPosition(0f, 0f)
        val TopRight = PipPosition(1f, 0f)
        val BottomLeft = PipPosition(0f, 1f)
    }
}

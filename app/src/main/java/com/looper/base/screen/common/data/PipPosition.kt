package com.looper.base.screen.common.data

/**
 * Where a PiP window sits inside its full frame, as fractions of the space it can move in (the
 * full frame minus its margins and the window itself), so the same position fits any frame size
 * (preview and export):
 * - [x]: 0 = against the left margin, 1 = against the right margin;
 * - [y]: 0 = against the top margin, 1 = against the bottom margin.
 */
data class PipPosition(val x: Float, val y: Float) {

    /** Moved by fractions of the free space, kept inside the frame. */
    fun movedBy(dx: Float, dy: Float): PipPosition =
        PipPosition((x + dx).coerceIn(0f, 1f), (y + dy).coerceIn(0f, 1f))

    companion object {
        /** The corner of a corner PiP layout (start = left). */
        fun of(layout: DualLayout): PipPosition =
            PipPosition(if (layout.isStart) 0f else 1f, if (layout.isTop) 0f else 1f)

        /**
         * Custom position to use when switching from [previous] to [DualLayout.PipCustom]: the
         * window starts where the previous corner layout had it, else stays at [current].
         */
        fun whenSwitchingFrom(previous: DualLayout, current: PipPosition): PipPosition =
            if (previous.isPip && previous != DualLayout.PipCustom) of(previous) else current
    }
}

/** Window position for [layout]: its corner, or [custom] for [DualLayout.PipCustom]. */
fun DualLayout.pipPosition(custom: PipPosition): PipPosition =
    if (this == DualLayout.PipCustom) custom else PipPosition.of(this)

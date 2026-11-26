package com.looper.base.screen.common.data

/**
 * How two feeds (cameras or videos) are arranged, on screen and in the saved photo / video.
 * Shared by the dual camera, reaction and video combiner screens; each screen computes its own
 * geometry for it.
 *
 * The "first" feed is the main one (full frame in PiP, top / left half in the split layouts),
 * the "second" one the other feed.
 */
enum class DualLayout(val isStart: Boolean, val isTop: Boolean) {
    /** Two halves: first feed on top, second at the bottom. */
    SplitVertical(isStart = true, isTop = true),

    /** Two halves side by side: first feed on the left, second on the right. */
    SplitHorizontal(isStart = true, isTop = true),

    /** First feed full frame, second as a small window in a corner. */
    PipTopStart(isStart = true, isTop = true),
    PipTopEnd(isStart = false, isTop = true),
    PipBottomStart(isStart = true, isTop = false),
    PipBottomEnd(isStart = false, isTop = false),

    /**
     * Like the corner PiP layouts (same sizes), but the user places the window anywhere by
     * long-pressing and dragging it; see [PipPosition].
     */
    PipCustom(isStart = true, isTop = false);

    val isPip: Boolean
        get() = this != SplitVertical && this != SplitHorizontal
}

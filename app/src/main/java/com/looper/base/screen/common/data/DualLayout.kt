package com.looper.base.screen.common.data

/**
 * How two feeds (cameras or videos) are arranged, on screen and in the saved photo / video.
 * Shared by the dual camera, reaction and video combiner screens; each screen computes its own
 * geometry for it.
 *
 * The "first" feed is the main one (full frame in PiP, top / left half in the split layouts),
 * the "second" one the other feed.
 */
enum class DualLayout {
    /** Two halves: first feed on top, second at the bottom. */
    SplitVertical,

    /** Two halves side by side: first feed on the left, second on the right. */
    SplitHorizontal,

    /**
     * First feed full frame, second as a small window over it that the user places anywhere (up
     * to the frame's edges) by long-pressing and dragging it; see [PipPosition].
     */
    PipCustom;

    val isPip: Boolean
        get() = this == PipCustom
}

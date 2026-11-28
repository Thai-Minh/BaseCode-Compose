package com.looper.base.screen.common.data

/**
 * Which sound ends up in a video made of two videos, shared by the reaction and video combiner
 * screens. It follows the slots, whichever video is in them (a swap swaps whose sound is kept):
 * the full-frame slot is the full frame of the PiP layout and the top / left half of the split
 * layouts, the window slot is the PiP window and the bottom / right half.
 *
 * A kept sound that ends before the output (its video is the shorter one) is followed by silence
 * until the end.
 */
enum class DualAudioMode(val keepsFullScreen: Boolean, val keepsWindow: Boolean) {
    /** Only the full-frame slot's sound. */
    FullScreen(keepsFullScreen = true, keepsWindow = false),

    /** Only the window slot's sound. */
    Window(keepsFullScreen = false, keepsWindow = true),

    /** Both sounds mixed. */
    Both(keepsFullScreen = true, keepsWindow = true),
    ;

    /** Whether the sound of the full-frame slot ([fullScreenSlot]) or of the window is kept. */
    fun keeps(fullScreenSlot: Boolean): Boolean = if (fullScreenSlot) keepsFullScreen else keepsWindow
}

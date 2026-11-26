package com.looper.base.screen.common.data

/**
 * Which audio ends up in a video made of two videos. Shared by the reaction and video combiner
 * screens; each screen names the options itself.
 *
 * "Main" is the original video (reaction: the background, combiner: the main video) and
 * "secondary" the added one (reaction: the camera recording, combiner: the overlay), wherever
 * each one is placed by the [DualLayout] or a swap.
 */
enum class DualAudioMode(val keepsMain: Boolean, val keepsSecondary: Boolean) {
    /** v1: only the main video's sound. */
    Main(keepsMain = true, keepsSecondary = false),

    /** v2: only the secondary video's sound. */
    Secondary(keepsMain = false, keepsSecondary = true),

    /** v1 + v2 mixed. */
    Both(keepsMain = true, keepsSecondary = true),
}

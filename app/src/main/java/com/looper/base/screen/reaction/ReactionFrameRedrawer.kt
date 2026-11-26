package com.looper.base.screen.reaction

import androidx.media3.common.Player

/**
 * A paused player doesn't redraw its current frame on a new (or resized) output surface, so a
 * video prepared before its TextureView was ready can stay blank until it plays. Seeking to the
 * current position makes it render that frame again.
 */
internal class ReactionFrameRedrawer(private val player: Player) : Player.Listener {

    override fun onSurfaceSizeChanged(width: Int, height: Int) {
        if (width > 0 && height > 0 &&
            !player.playWhenReady &&
            player.playbackState == Player.STATE_READY
        ) {
            player.seekTo(player.currentPosition)
        }
    }
}

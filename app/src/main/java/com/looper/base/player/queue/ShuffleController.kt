package com.looper.base.player.queue

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import java.util.concurrent.atomic.AtomicInteger

@OptIn(UnstableApi::class)
class ShuffleController(
    private val player: ExoPlayer,
) {

    private val anchorIndex = AtomicInteger(C.INDEX_UNSET)
    private val anchor: () -> Int = {
        anchorIndex.get()
    }

    private val listener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            anchorIndex.set(player.currentMediaItemIndex)
        }

        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            anchorIndex.set(player.currentMediaItemIndex)
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            if (shuffleModeEnabled) {
                player.shuffleOrder = QueueShuffleOrder.startingWith(
                    firstIndex = player.currentMediaItemIndex,
                    length = player.mediaItemCount,
                    anchor = anchor,
                )
            }
        }
    }

    init {
        player.shuffleOrder = QueueShuffleOrder.startingWith(
            firstIndex = player.currentMediaItemIndex,
            length = player.mediaItemCount,
            anchor = anchor,
        )
        player.addListener(listener)
    }

    fun restore(shuffledIndices: IntArray) {
        if (shuffledIndices.size == player.mediaItemCount) {
            player.shuffleOrder =
                QueueShuffleOrder(shuffledIndices = shuffledIndices, anchor = anchor)
        }
    }

    fun release() {
        player.removeListener(listener)
    }
}

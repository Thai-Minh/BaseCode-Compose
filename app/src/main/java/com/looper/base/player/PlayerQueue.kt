package com.looper.base.player

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.Player

data class QueueItem(
    val index: Int,
    val mediaId: String,
    val title: String?,
    val artist: String?,
    val artworkUri: Uri?,
    val durationMs: Long,
)

data class PlayerQueue(
    val items: List<QueueItem> = emptyList(),
    val currentIndex: Int = C.INDEX_UNSET,
    val isShuffled: Boolean = false,
) {
    val currentPosition: Int
        get() = items.indexOfFirst {
            it.index == currentIndex
        }

    val current: QueueItem?
        get() = items.getOrNull(index = currentPosition)

    val upNext: List<QueueItem>
        get() = if (currentPosition < 0) {
            items
        } else {
            items.drop(n = currentPosition + 1)
        }

    val canReorder: Boolean
        get() = !isShuffled

    companion object {
        fun from(player: Player): PlayerQueue {
            val count = player.mediaItemCount
            if (count == 0) {
                return PlayerQueue(isShuffled = player.shuffleModeEnabled)
            }
            val items = playbackOrder(player = player, count = count).map { index ->
                val mediaItem = player.getMediaItemAt(index)
                val metadata = mediaItem.mediaMetadata
                QueueItem(
                    index = index,
                    mediaId = mediaItem.mediaId,
                    title = metadata.title?.toString(),
                    artist = metadata.artist?.toString(),
                    artworkUri = metadata.artworkUri,
                    durationMs = metadata.durationMs ?: 0L,
                )
            }
            return PlayerQueue(
                items = items,
                currentIndex = player.currentMediaItemIndex,
                isShuffled = player.shuffleModeEnabled,
            )
        }

        private fun playbackOrder(player: Player, count: Int): List<Int> {
            val timeline = player.currentTimeline
            val shuffled = player.shuffleModeEnabled
            if (!shuffled || timeline.windowCount != count) {
                return List(size = count) {
                    it
                }
            }
            val order = mutableListOf<Int>()
            var index = timeline.getFirstWindowIndex(shuffled)
            while (index != C.INDEX_UNSET && order.size < count) {
                order += index
                index = timeline.getNextWindowIndex(index, Player.REPEAT_MODE_OFF, shuffled)
            }
            return if (order.size == count) {
                order
            } else {
                List(size = count) {
                    it
                }
            }
        }
    }
}

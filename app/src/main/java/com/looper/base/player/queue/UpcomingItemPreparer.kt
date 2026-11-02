package com.looper.base.player.queue

import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.looper.base.player.source.MediaItemKeys
import com.looper.base.player.source.PlaybackItemFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
class UpcomingItemPreparer(
    private val player: ExoPlayer,
    private val scope: CoroutineScope,
    private val itemFactory: PlaybackItemFactory,
) {

    private companion object {
        const val START_DELAY_MS = 2_000L
        const val REPREPARE_AFTER_MS = 10 * 60_000L
    }

    private val preparedAt = mutableMapOf<String, Long>()
    private var job: Job? = null

    private val listener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            schedule()
        }

        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) {
                schedule()
            }
        }
    }

    init {
        player.addListener(listener)
    }

    fun release() {
        job?.cancel()
        player.removeListener(listener)
    }

    private fun schedule() {
        job?.cancel()
        job = scope.launch {
            delay(timeMillis = START_DELAY_MS)
            val index = player.nextMediaItemIndex
            if (index == C.INDEX_UNSET) {
                return@launch
            }
            val upcoming = player.getMediaItemAt(index)
            val mediaItemId =
                MediaItemKeys.streamMediaItemId(uri = upcoming.localConfiguration?.uri)
                    ?: return@launch
            val now = SystemClock.elapsedRealtime()
            if (preparedAt[mediaItemId]?.let { now - it < REPREPARE_AFTER_MS } == true) {
                return@launch
            }
            val rebuilt = runCatching {
                itemFactory.build(mediaItemId = mediaItemId, resolve = true)
            }.getOrNull() ?: return@launch
            preparedAt[mediaItemId] = SystemClock.elapsedRealtime()
            if (index >= player.mediaItemCount || player.getMediaItemAt(index).mediaId != mediaItemId) {
                return@launch
            }
            val current = player.getMediaItemAt(index).localConfiguration
            val next = rebuilt.localConfiguration
            if (current?.uri != next?.uri || current?.mimeType != next?.mimeType || current?.customCacheKey != next?.customCacheKey) {
                player.replaceMediaItem(index, rebuilt)
            }
        }
    }
}

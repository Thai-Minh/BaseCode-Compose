package com.looper.base.player

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.looper.base.base.storage.playbackSnapshot
import com.looper.base.base.storage.updatePlaybackSnapshot
import com.looper.base.database.dao.QueueDao
import com.looper.base.database.data.QueueItemEntity
import com.looper.base.player.model.PlaybackSnapshot
import com.looper.base.player.queue.ShuffleController
import com.looper.base.player.source.PlaybackItemFactory
import com.looper.base.player.source.StreamRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class RestoredQueue(
    val items: List<MediaItem>,
    val startIndex: Int,
    val positionMs: Long,
    @Player.RepeatMode val repeatMode: Int,
    val shuffleEnabled: Boolean,
    val shuffledIndices: IntArray?,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (javaClass != other?.javaClass) {
            return false
        }

        other as RestoredQueue

        if (startIndex != other.startIndex) {
            return false
        }
        if (positionMs != other.positionMs) {
            return false
        }
        if (repeatMode != other.repeatMode) {
            return false
        }
        if (shuffleEnabled != other.shuffleEnabled) {
            return false
        }
        if (items != other.items) {
            return false
        }
        if (!shuffledIndices.contentEquals(other.shuffledIndices)) {
            return false
        }

        return true
    }

    override fun hashCode(): Int {
        var result = startIndex
        result = 31 * result + positionMs.hashCode()
        result = 31 * result + repeatMode
        result = 31 * result + shuffleEnabled.hashCode()
        result = 31 * result + items.hashCode()
        result = 31 * result + (shuffledIndices?.contentHashCode() ?: 0)
        return result
    }
}

@OptIn(UnstableApi::class)
class PlaybackPersistence(
    private val player: ExoPlayer,
    private val scope: CoroutineScope,
    private val queueDao: QueueDao,
    private val repository: StreamRepository,
    private val itemFactory: PlaybackItemFactory,
    private val shuffleController: ShuffleController,
) {

    private companion object {
        const val POSITION_SAVE_INTERVAL_MS = 5_000L
        const val QUEUE_SAVE_DEBOUNCE_MS = 500L
    }

    private var queueSaveJob: Job? = null
    private var positionJob: Job? = null

    private val listener = object : Player.Listener {
        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) {
                scheduleQueueSave()
            }
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            scheduleQueueSave()
            saveSnapshot()
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            saveSnapshot()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            saveSnapshot()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) {
                startPositionTicker()
            } else {
                positionJob?.cancel()
                saveSnapshot()
            }
        }
    }

    init {
        player.addListener(listener)
    }

    fun release() {
        saveSnapshot()
        queueSaveJob?.cancel()
        positionJob?.cancel()
        player.removeListener(listener)
    }

    fun saveSnapshot() {
        if (player.mediaItemCount == 0) {
            return
        }
        updatePlaybackSnapshot(
            value = PlaybackSnapshot(
                currentIndex = player.currentMediaItemIndex,
                positionMs = player.currentPosition.coerceAtLeast(minimumValue = 0L),
                repeatMode = player.repeatMode,
                shuffleEnabled = player.shuffleModeEnabled,
            )
        )
    }

    suspend fun load(): RestoredQueue? {
        val rows = queueDao.getAll()
        if (rows.isEmpty()) {
            return null
        }
        val items = itemFactory.buildAll(
            ids = rows.map {
                it.mediaItemId
            }
        )
        if (items.isEmpty()) {
            return null
        }
        val snapshot = playbackSnapshot.first()
        val aligned = items.size == rows.size
        val startIndex = if (aligned) {
            rows.indexOfFirst {
                it.position == snapshot.currentIndex
            }.coerceAtLeast(minimumValue = 0)
        } else {
            0
        }
        return RestoredQueue(
            items = items,
            startIndex = startIndex,
            positionMs = if (aligned) {
                snapshot.positionMs
            } else {
                0L
            },
            repeatMode = snapshot.repeatMode,
            shuffleEnabled = snapshot.shuffleEnabled,
            shuffledIndices = if (aligned) {
                shuffledIndices(rows = rows)
            } else {
                null
            },
        )
    }

    fun apply(restored: RestoredQueue) {
        player.setMediaItems(restored.items, restored.startIndex, restored.positionMs)
        player.repeatMode = restored.repeatMode
        player.shuffleModeEnabled = restored.shuffleEnabled
        restored.shuffledIndices?.let {
            shuffleController.restore(shuffledIndices = it)
        }
    }

    private fun shuffledIndices(rows: List<QueueItemEntity>): IntArray? {
        val order = IntArray(rows.size) {
            -1
        }
        rows.forEachIndexed { windowIndex, row ->
            val shuffleIndex = row.shuffleIndex ?: return null
            if (shuffleIndex !in order.indices || order[shuffleIndex] != -1) {
                return null
            }
            order[shuffleIndex] = windowIndex
        }
        return order
    }

    private fun scheduleQueueSave() {
        queueSaveJob?.cancel()
        queueSaveJob = scope.launch {
            delay(timeMillis = QUEUE_SAVE_DEBOUNCE_MS)
            val queue = currentQueue()
            val knownIds = repository.getMediaItems(
                ids = queue.map {
                    it.mediaItemId
                }
            ).mapTo(destination = HashSet()) {
                it.id
            }
            queueDao.replace(
                items = queue.filter {
                    it.mediaItemId in knownIds
                }
            )
        }
    }

    private fun currentQueue(): List<QueueItemEntity> {
        val timeline = player.currentTimeline
        val shuffleIndices = IntArray(size = timeline.windowCount) {
            -1
        }
        var windowIndex = timeline.getFirstWindowIndex(true)
        var shufflePosition = 0
        while (windowIndex != C.INDEX_UNSET) {
            shuffleIndices[windowIndex] = shufflePosition++
            windowIndex = timeline.getNextWindowIndex(windowIndex, Player.REPEAT_MODE_OFF, true)
        }
        return List(size = player.mediaItemCount) { index ->
            QueueItemEntity(
                position = index,
                mediaItemId = player.getMediaItemAt(index).mediaId,
                shuffleIndex = shuffleIndices.getOrNull(index)?.takeIf {
                    it >= 0
                },
            )
        }
    }

    private fun startPositionTicker() {
        positionJob?.cancel()
        positionJob = scope.launch {
            while (isActive) {
                delay(timeMillis = POSITION_SAVE_INTERVAL_MS)
                saveSnapshot()
            }
        }
    }
}

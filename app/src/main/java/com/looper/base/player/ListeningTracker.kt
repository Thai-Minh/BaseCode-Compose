package com.looper.base.player

import android.os.SystemClock
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.looper.base.database.dao.MediaItemDao
import com.looper.base.database.dao.PlaybackEventDao
import com.looper.base.database.data.PlaybackEventEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class ListeningTracker(
    private val player: Player,
    private val scope: CoroutineScope,
    private val playbackEventDao: PlaybackEventDao,
    private val mediaItemDao: MediaItemDao,
    private val onRecorded: suspend () -> Unit = {},
) {

    private companion object {
        const val MIN_LISTENED_FRACTION = 0.2
        const val FULL_LISTEN_FRACTION = 0.8
    }

    private var mediaItemId: String? = null
    private var startedAt: Long = 0L
    private var durationMs: Long = 0L
    private var listenedMs: Long = 0L
    private var playingSince: Long? = null

    private val listener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            flush()
            begin(mediaItemId = mediaItem?.mediaId)
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) {
                if (mediaItemId == null) {
                    begin(mediaItemId = player.currentMediaItem?.mediaId)
                }
                playingSince = SystemClock.elapsedRealtime()
            } else {
                accumulate()
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) {
                player.duration.takeIf { it != C.TIME_UNSET }?.let {
                    durationMs = it
                }
            }
        }
    }

    init {
        player.addListener(listener)
        begin(mediaItemId = player.currentMediaItem?.mediaId)
    }

    fun release() {
        flush()
        player.removeListener(listener)
    }

    private fun begin(mediaItemId: String?) {
        this.mediaItemId = mediaItemId
        startedAt = System.currentTimeMillis()
        durationMs = player.duration.takeIf {
            it != C.TIME_UNSET
        } ?: 0L
        listenedMs = 0L
        playingSince = if (player.isPlaying) {
            SystemClock.elapsedRealtime()
        } else {
            null
        }
    }

    private fun accumulate() {
        val since = playingSince ?: return
        val elapsed = SystemClock.elapsedRealtime() - since
        listenedMs += (elapsed * player.playbackParameters.speed).toLong()
        playingSince = null
    }

    private fun flush() {
        accumulate()
        val id = mediaItemId ?: return
        val duration = durationMs
        val listened = listenedMs
        mediaItemId = null
        if (duration <= 0L || listened < duration * MIN_LISTENED_FRACTION) {
            return
        }
        val event = PlaybackEventEntity(
            mediaItemId = id,
            startedAt = startedAt,
            durationMs = duration,
            listenedMs = if (listened >= duration * FULL_LISTEN_FRACTION) {
                duration
            } else {
                listened
            },
        )
        scope.launch {
            runCatching {
                playbackEventDao.insert(event = event)
                mediaItemDao.incrementPlayCount(id = id)
                onRecorded()
            }
        }
    }
}

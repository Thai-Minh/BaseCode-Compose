package com.looper.base.player.audio

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.looper.base.database.dao.MediaItemDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class LoudnessTracker(
    private val player: Player,
    private val processor: LoudnessNormalizerProcessor,
    private val mediaItemDao: MediaItemDao,
    private val scope: CoroutineScope,
) {

    private var mediaItemId: String? = null
    private var knownLoudness = false
    private var lookupJob: Job? = null

    private val listener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            persistMeasurement()
            begin(mediaItemId = mediaItem?.mediaId)
        }
    }

    init {
        player.addListener(listener)
        begin(mediaItemId = player.currentMediaItem?.mediaId)
    }

    fun release() {
        persistMeasurement()
        lookupJob?.cancel()
        player.removeListener(listener)
    }

    private fun begin(mediaItemId: String?) {
        this.mediaItemId = mediaItemId
        knownLoudness = false
        processor.startTrack(knownLoudnessDb = null)
        lookupJob?.cancel()
        if (mediaItemId == null) {
            return
        }
        lookupJob = scope.launch {
            val loudness = mediaItemDao.get(id = mediaItemId)?.loudnessDb ?: return@launch
            if (this@LoudnessTracker.mediaItemId == mediaItemId) {
                knownLoudness = true
                processor.startTrack(knownLoudnessDb = loudness)
            }
        }
    }

    private fun persistMeasurement() {
        val id = mediaItemId ?: return
        if (knownLoudness) {
            return
        }
        val measured = processor.measuredLoudness() ?: return
        scope.launch {
            runCatching {
                mediaItemDao.setLoudness(id = id, loudnessDb = measured)
            }
        }
    }
}

package com.looper.base.player

import androidx.annotation.OptIn
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.looper.base.player.source.PlaybackItemFactory
import com.looper.base.player.source.StreamRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
class PlaybackRecovery(
    private val player: ExoPlayer,
    private val scope: CoroutineScope,
    private val repository: StreamRepository,
    private val itemFactory: PlaybackItemFactory,
) {

    private companion object {
        const val MAX_RETRIES = 2

        val RETRYABLE_ERRORS = setOf(
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
            PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
            PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE,
            PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
        )
    }

    private val retries = mutableMapOf<String, Int>()
    private var recoveryJob: Job? = null

    private val listener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            recover(error = error)
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) {
                player.currentMediaItem?.mediaId?.let {
                    retries.remove(key = it)
                }
            }
        }
    }

    init {
        player.addListener(listener)
    }

    fun release() {
        recoveryJob?.cancel()
        player.removeListener(listener)
    }

    private fun recover(error: PlaybackException) {
        if (error.errorCode !in RETRYABLE_ERRORS) {
            return
        }
        val mediaItemId = player.currentMediaItem?.mediaId ?: return
        val attempts = retries[mediaItemId] ?: 0
        if (attempts >= MAX_RETRIES) {
            return
        }
        retries[mediaItemId] = attempts + 1

        val index = player.currentMediaItemIndex
        val positionMs = player.currentPosition
        val playWhenReady = player.playWhenReady
        recoveryJob?.cancel()
        recoveryJob = scope.launch {
            repository.invalidate(mediaItemId = mediaItemId)
            val rebuilt =
                itemFactory.build(mediaItemId = mediaItemId, resolve = true) ?: return@launch
            if (player.currentMediaItemIndex != index || player.currentMediaItem?.mediaId != mediaItemId) {
                return@launch
            }
            player.replaceMediaItem(index, rebuilt)
            player.seekTo(index, positionMs)
            player.playWhenReady = playWhenReady
            player.prepare()
        }
    }
}

package com.looper.base.lastfm.data

import android.os.SystemClock
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.looper.base.base.storage.lastFmSettings
import com.looper.base.lastfm.model.LastFmSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class LastFmScrobbler(
    private val player: Player,
    private val scope: CoroutineScope,
    private val client: LastFmClient,
    private val repository: LastFmRepository,
) {

    private companion object {
        const val MIN_DURATION_MS = 30_000L
        const val MAX_REQUIRED_MS = 240_000L
        const val CHECK_INTERVAL_MS = 5_000L
    }

    private var settings = LastFmSettings()
    private var track: LastFmTrack? = null
    private var startedAtSeconds = 0L
    private var listenedMs = 0L
    private var playingSince: Long? = null
    private var scrobbled = false
    private var checkJob: Job? = null
    private var settingsJob: Job? = null

    private val listener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            begin(mediaItem = mediaItem)
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) {
                playingSince = SystemClock.elapsedRealtime()
                startChecks()
            } else {
                accumulate()
                checkJob?.cancel()
            }
        }
    }

    init {
        settingsJob = scope.launch {
            lastFmSettings.collect {
                settings = it
            }
        }
        player.addListener(listener)
    }

    fun release() {
        checkJob?.cancel()
        settingsJob?.cancel()
        player.removeListener(listener)
    }

    private fun begin(mediaItem: MediaItem?) {
        accumulate()
        val metadata = mediaItem?.mediaMetadata
        val artist = metadata?.artist?.toString()?.takeIf {
            it.isNotBlank()
        }
        val title = metadata?.title?.toString()?.takeIf {
            it.isNotBlank()
        }
        track = if (artist != null && title != null) {
            LastFmTrack(
                artist = artist,
                title = title,
                album = metadata.albumTitle?.toString(),
                durationSeconds = metadata.durationMs?.div(other = 1000),
            )
        } else {
            null
        }
        startedAtSeconds = System.currentTimeMillis() / 1000
        listenedMs = 0L
        scrobbled = false
        playingSince = if (player.isPlaying) {
            SystemClock.elapsedRealtime()
        } else {
            null
        }
        val current = track ?: return
        val sessionKey = activeSessionKey() ?: return
        scope.launch {
            runCatching {
                client.updateNowPlaying(sessionKey = sessionKey, track = current)
            }.onFailure(action = ::handleFailure)
        }
    }

    private fun startChecks() {
        checkJob?.cancel()
        checkJob = scope.launch {
            while (isActive) {
                delay(timeMillis = CHECK_INTERVAL_MS)
                maybeScrobble()
            }
        }
    }

    private fun maybeScrobble() {
        if (scrobbled) {
            return
        }
        val current = track ?: return
        val durationMs = player.duration.takeIf {
            it != C.TIME_UNSET
        } ?: return
        if (durationMs < MIN_DURATION_MS) {
            return
        }
        accumulate()
        if (player.isPlaying) {
            playingSince = SystemClock.elapsedRealtime()
        }
        if (listenedMs < minOf(a = durationMs / 2, b = MAX_REQUIRED_MS)) {
            return
        }
        val sessionKey = activeSessionKey() ?: return
        scrobbled = true
        val timestamp = startedAtSeconds
        scope.launch {
            runCatching {
                client.scrobble(
                    sessionKey = sessionKey,
                    track = current.copy(durationSeconds = durationMs / 1000),
                    timestampSeconds = timestamp,
                )
            }.onFailure(action = ::handleFailure)
        }
    }

    private fun accumulate() {
        val since = playingSince ?: return
        listenedMs += ((SystemClock.elapsedRealtime() - since) * player.playbackParameters.speed).toLong()
        playingSince = null
    }

    private fun activeSessionKey(): String? =
        settings.sessionKey?.takeIf {
            settings.scrobbleEnabled && client.isConfigured
        }

    private fun handleFailure(error: Throwable) {
        if (error is LastFmException && error.isSessionInvalid) {
            scope.launch {
                repository.logout()
            }
        }
    }
}

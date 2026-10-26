package com.looper.base.player.sleeptimer

import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
class SleepTimerController(
    private val player: ExoPlayer,
    private val scope: CoroutineScope,
    private val onStateChanged: (SleepTimerState) -> Unit,
) {

    private companion object {
        const val FADE_MS = 3_000L
        const val FADE_STEPS = 30
        const val MAX_SLEEP_MS = 30_000L
    }

    val state: StateFlow<SleepTimerState>
        field = MutableStateFlow(value = SleepTimerState.Inactive)

    private var countdownJob: Job? = null
    private var fadeJob: Job? = null
    private var volumeBeforeFade: Float? = null

    private val listener = object : Player.Listener {
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (playWhenReady) return
            when {
                reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM &&
                        state.value.mode == SleepTimerMode.END_OF_TRACK -> finish()

                reason == Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST &&
                        fadeJob?.isActive == true -> cancel()
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED &&
                state.value.mode == SleepTimerMode.END_OF_TRACK
            ) {
                finish()
            }
        }
    }

    init {
        player.addListener(listener)
    }

    fun start(mode: SleepTimerMode, durationMs: Long = 0L) {
        clearJobs()
        when (mode) {
            SleepTimerMode.DURATION -> {
                if (durationMs <= 0L) {
                    cancel()
                    return
                }
                player.pauseAtEndOfMediaItems = false
                setState(
                    SleepTimerState(
                        mode = mode,
                        endAtMs = System.currentTimeMillis() + durationMs,
                    )
                )
                scheduleCountdown()
            }

            SleepTimerMode.END_OF_TRACK -> {
                player.pauseAtEndOfMediaItems = true
                setState(SleepTimerState(mode = mode))
            }
        }
    }

    fun extend(byMs: Long) {
        val current = state.value
        when (current.mode) {
            SleepTimerMode.DURATION -> {
                val base = maxOf(a = current.endAtMs ?: 0L, b = System.currentTimeMillis())
                clearJobs()
                setState(current.copy(endAtMs = base + byMs))
                scheduleCountdown()
            }

            SleepTimerMode.END_OF_TRACK -> start(mode = SleepTimerMode.DURATION, durationMs = byMs)

            null -> start(mode = SleepTimerMode.DURATION, durationMs = byMs)
        }
    }

    fun cancel() {
        clearJobs()
        player.pauseAtEndOfMediaItems = false
        restoreVolume()
        setState(SleepTimerState.Inactive)
    }

    fun release() {
        clearJobs()
        player.removeListener(listener)
    }

    private fun scheduleCountdown() {
        countdownJob = scope.launch {
            while (isActive) {
                val endAt = state.value.endAtMs ?: return@launch
                val untilFade = endAt - FADE_MS - System.currentTimeMillis()
                if (untilFade <= 0L) {
                    fadeOutAndPause()
                    return@launch
                }
                delay(timeMillis = untilFade.coerceAtMost(maximumValue = MAX_SLEEP_MS))
            }
        }
    }

    private fun fadeOutAndPause() {
        if (!player.isPlaying) {
            finish()
            return
        }
        fadeJob = scope.launch {
            val startVolume = player.volume
            volumeBeforeFade = startVolume
            val stepDelay = FADE_MS / FADE_STEPS
            for (step in 1..FADE_STEPS) {
                player.volume = startVolume * (1f - step / FADE_STEPS.toFloat())
                delay(timeMillis = stepDelay)
            }
            fadeJob = null
            player.pause()
            finish()
        }
    }

    private fun finish() {
        clearJobs()
        player.pauseAtEndOfMediaItems = false
        restoreVolume()
        setState(next = SleepTimerState.Inactive)
    }

    private fun restoreVolume() {
        volumeBeforeFade?.let {
            player.volume = it
        }
        volumeBeforeFade = null
    }

    private fun clearJobs() {
        countdownJob?.cancel()
        countdownJob = null
        fadeJob?.cancel()
        fadeJob = null
        restoreVolume()
    }

    private fun setState(next: SleepTimerState) {
        state.value = next
        onStateChanged(next)
    }
}

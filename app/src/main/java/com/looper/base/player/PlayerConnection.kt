package com.looper.base.player

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.looper.base.player.sleeptimer.SleepTimerCommands
import com.looper.base.player.sleeptimer.SleepTimerCommands.readSleepTimerState
import com.looper.base.player.sleeptimer.SleepTimerMode
import com.looper.base.player.sleeptimer.SleepTimerState
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive

class PlayerConnection(
    private val context: Context,
) {

    private companion object {
        const val POSITION_TICK_MS = 500L
    }

    private var controllerFuture: ListenableFuture<MediaController>? = null

    val controller: StateFlow<MediaController?>
        field = MutableStateFlow(value = null)

    val state: StateFlow<PlayerState>
        field = MutableStateFlow(value = PlayerState())

    val sleepTimer: StateFlow<SleepTimerState>
        field = MutableStateFlow(value = SleepTimerState.Inactive)

    val position: Flow<Long> = flow {
        while (currentCoroutineContext().isActive) {
            emit(value = controller.value?.currentPosition ?: 0L)
            delay(timeMillis = POSITION_TICK_MS)
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            state.value = PlayerState.from(player = player)
        }
    }

    private val controllerListener = object : MediaController.Listener {
        override fun onExtrasChanged(controller: MediaController, extras: Bundle) {
            sleepTimer.value = extras.readSleepTimerState()
        }

        override fun onDisconnected(controller: MediaController) {
            clearController()
        }
    }

    private val processObserver = object : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) = connect()
        override fun onStop(owner: LifecycleOwner) = release()
    }

    fun bindToProcessLifecycle() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(processObserver)
    }

    fun connect() {
        if (controllerFuture != null) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token)
            .setListener(controllerListener)
            .buildAsync()
        controllerFuture = future
        future.addListener(
            {
                if (controllerFuture !== future) {
                    return@addListener
                }
                runCatching {
                    future.get()
                }
                    .onSuccess(action = ::onConnected)
                    .onFailure {
                        controllerFuture = null
                    }
            },
            ContextCompat.getMainExecutor(context),
        )
    }

    fun release() {
        controllerFuture?.let(block = MediaController::releaseFuture)
        clearController()
    }

    private fun onConnected(connected: MediaController) {
        connected.addListener(playerListener)
        controller.value = connected
        sleepTimer.value = connected.sessionExtras.readSleepTimerState()
        state.value = PlayerState.from(player = connected)
    }

    private fun clearController() {
        controllerFuture = null
        controller.value?.removeListener(playerListener)
        controller.value = null
        state.update {
            it.copy(isConnected = false, isPlaying = false)
        }
    }


    fun play() {
        controller.value?.play()
    }

    fun pause() {
        controller.value?.pause()
    }

    fun togglePlayPause() {
        val current = controller.value ?: return
        if (current.isPlaying) {
            current.pause()
        } else {
            current.play()
        }
    }

    fun seekTo(positionMs: Long) {
        controller.value?.seekTo(positionMs)
    }

    fun skipToNext() {
        controller.value?.seekToNextMediaItem()
    }

    fun skipToPrevious() {
        controller.value?.seekToPreviousMediaItem()
    }

    fun setMediaItems(
        items: List<MediaItem>,
        startIndex: Int = 0,
        startPositionMs: Long = 0L,
        playWhenReady: Boolean = true,
    ) {
        val current = controller.value ?: return
        current.setMediaItems(items, startIndex, startPositionMs)
        current.prepare()
        current.playWhenReady = playWhenReady
    }

    fun startSleepTimer(durationMs: Long) {
        sendCustomCommand(
            action = SleepTimerCommands.ACTION_START,
            args = Bundle().apply {
                putString(SleepTimerCommands.EXTRA_MODE, SleepTimerMode.DURATION.name)
                putLong(SleepTimerCommands.EXTRA_DURATION_MS, durationMs)
            },
        )
    }

    fun startSleepTimerAtEndOfTrack() {
        sendCustomCommand(
            action = SleepTimerCommands.ACTION_START,
            args = Bundle().apply {
                putString(SleepTimerCommands.EXTRA_MODE, SleepTimerMode.END_OF_TRACK.name)
            },
        )
    }

    fun extendSleepTimer(byMs: Long) {
        sendCustomCommand(
            action = SleepTimerCommands.ACTION_EXTEND,
            args = Bundle().apply {
                putLong(SleepTimerCommands.EXTRA_DURATION_MS, byMs)
            },
        )
    }

    fun cancelSleepTimer() {
        sendCustomCommand(action = SleepTimerCommands.ACTION_CANCEL)
    }

    private fun sendCustomCommand(action: String, args: Bundle = Bundle.EMPTY) {
        controller.value?.sendCustomCommand(SessionCommand(action, Bundle.EMPTY), args)
    }
}

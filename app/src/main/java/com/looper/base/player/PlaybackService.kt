package com.looper.base.player

import android.content.Intent
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSession.ConnectionResult
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.looper.base.equalizer.audio.AudioEffectController
import com.looper.base.equalizer.data.EqRepository
import com.looper.base.player.sleeptimer.SleepTimerCommands
import com.looper.base.player.sleeptimer.SleepTimerCommands.writeTo
import com.looper.base.player.sleeptimer.SleepTimerController
import com.looper.base.player.sleeptimer.SleepTimerMode
import com.looper.base.player.sleeptimer.SleepTimerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private val effectController: AudioEffectController by inject()
    private val eqRepository: EqRepository by inject()

    private val serviceScope = CoroutineScope(context = SupervisorJob() + Dispatchers.Main)

    private var player: ExoPlayer? = null
    private var mediaSession: MediaSession? = null
    private var sleepTimer: SleepTimerController? = null

    private val playerListener = object : Player.Listener {
        override fun onAudioSessionIdChanged(audioSessionId: Int) {
            effectController.attach(audioSessionId)
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY && !effectController.capabilities.value.attached) {
                player?.audioSessionId?.let(block = effectController::attach)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()

        val exoPlayer = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
        player = exoPlayer

        exoPlayer.addListener(playerListener)
        effectController.attach(exoPlayer.audioSessionId)

        val timer = SleepTimerController(
            player = exoPlayer,
            scope = serviceScope,
            onStateChanged = ::publishSessionExtras,
        )
        sleepTimer = timer

        mediaSession = MediaSession.Builder(this, exoPlayer)
            .setCallback(SessionCallback())
            .setSessionExtras(buildSessionExtras(timer.state.value))
            .build()

        serviceScope.launch {
            eqRepository.settings.collect {
                effectController.apply(settings = it)
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val current = player
        if (current == null || !current.playWhenReady || current.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        effectController.detach()
        sleepTimer?.release()
        sleepTimer = null
        mediaSession?.run {
            player.removeListener(playerListener)
            player.release()
            release()
        }
        mediaSession = null
        player = null
        super.onDestroy()
    }

    private fun buildSessionExtras(timer: SleepTimerState): Bundle =
        timer.writeTo(Bundle())

    private fun publishSessionExtras(timer: SleepTimerState) {
        mediaSession?.setSessionExtras(buildSessionExtras(timer = timer))
    }


    private inner class SessionCallback : MediaSession.Callback {

        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): ConnectionResult {
            val sessionCommands = ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                .apply { SleepTimerCommands.all.forEach(action = ::add) }
                .build()
            return ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(sessionCommands)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            val timer = sleepTimer
                ?: return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_INVALID_STATE))

            when (customCommand.customAction) {
                SleepTimerCommands.ACTION_START -> {
                    val mode = args.getString(SleepTimerCommands.EXTRA_MODE)
                        ?.let { name ->
                            SleepTimerMode.entries.firstOrNull {
                                it.name == name
                            }
                        } ?: SleepTimerMode.DURATION
                    timer.start(
                        mode = mode,
                        durationMs = args.getLong(SleepTimerCommands.EXTRA_DURATION_MS, 0L),
                    )
                }

                SleepTimerCommands.ACTION_EXTEND ->
                    timer.extend(byMs = args.getLong(SleepTimerCommands.EXTRA_DURATION_MS, 0L))

                SleepTimerCommands.ACTION_CANCEL -> timer.cancel()

                else -> return Futures.immediateFuture(
                    SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED)
                )
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }
    }
}

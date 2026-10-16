package com.looper.base.audio.service

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.google.common.util.concurrent.SettableFuture
import com.looper.base.R
import com.looper.base.activities.MainActivity
import com.looper.base.audio.lockscreen.LockScreenPlayerManager
import com.looper.base.audio.playback.AudioPlaybackSnapshot
import com.looper.base.audio.widget.AudioHomeWidget
import com.looper.base.base.storage.audioPlaybackSnapshot
import com.looper.base.base.storage.updateAudioPlaybackSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration.Companion.milliseconds

@androidx.annotation.OptIn(UnstableApi::class)
class AudioPlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private var lockScreenPlayerManager: LockScreenPlayerManager? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val restorationReady = SettableFuture.create<Unit>()
    private var playbackRestored = false
    private var restorationFailed = false
    private var serviceStarted = false
    private val widgetListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            if (events.containsAny(
                    Player.EVENT_PLAY_WHEN_READY_CHANGED,
                    Player.EVENT_IS_PLAYING_CHANGED,
                    Player.EVENT_PLAYBACK_STATE_CHANGED,
                    Player.EVENT_MEDIA_ITEM_TRANSITION,
                    Player.EVENT_POSITION_DISCONTINUITY,
                    Player.EVENT_MEDIA_METADATA_CHANGED,
                    Player.EVENT_TIMELINE_CHANGED,
                )) {
                updateAudioPlaybackSnapshot(AudioPlaybackSnapshot.fromPlayer(player))
            }
            AudioHomeWidget.update(this@AudioPlaybackService, player)
        }
    }

    companion object {
        internal var currentPlayer: Player? = null
            private set
        @SuppressLint("StaticFieldLeak")
        internal var lockScreenManager: LockScreenPlayerManager? = null
            private set
    }

    override fun onCreate() {
        super.onCreate()

        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .setMaxSeekToPreviousPositionMs(Long.MAX_VALUE)
            .build()
            .apply { repeatMode = Player.REPEAT_MODE_ALL }

        val activityIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Media3 owns foreground promotion, notification actions and lock-screen metadata.
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .setChannelName(R.string.audio_notification_channel)
                .build()
                .apply { setSmallIcon(R.drawable.ic_audio) }
        )
        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(activityIntent)
            .setCallback(object : MediaSession.Callback {
                override fun onConnectAsync(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo,
                ): ListenableFuture<MediaSession.ConnectionResult> {
                    val accepted = MediaSession.ConnectionResult.AcceptedResultBuilder(session, controller).build()
                    // No controller can send playback commands until DataStore restoration finishes.
                    return Futures.transform(
                        Futures.nonCancellationPropagating(restorationReady),
                        { accepted },
                        MoreExecutors.directExecutor(),
                    )
                }
            })
            .build()
        addSession(requireNotNull(mediaSession))
        lockScreenPlayerManager = LockScreenPlayerManager(this, player)
        lockScreenManager = lockScreenPlayerManager
        serviceScope.launch {
            try {
                val snapshot = audioPlaybackSnapshot.firstOrNull()

                snapshot?.let {
                    player.setMediaItem(it.toMediaItem(), it.positionMs)
                    player.prepare()
                }

                currentPlayer = player
                player.addListener(widgetListener)
                playbackRestored = true
                AudioHomeWidget.update(this@AudioPlaybackService, player)
                restorationReady.set(Unit)
            } catch (error: TimeoutCancellationException) {
                restorationFailed = true
                restorationReady.setException(error)
                Log.w("AudioPlaybackService", "Playback restoration timed out", error)
                stopAfterRestorationFailure()
            } catch (error: CancellationException) {
                restorationReady.cancel(false)
                throw error
            } catch (error: Exception) {
                restorationFailed = true
                restorationReady.setException(error)
                Log.w("AudioPlaybackService", "Unable to restore playback snapshot", error)
                stopAfterRestorationFailure()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        serviceStarted = true
        val result = super.onStartCommand(intent, flags, startId)
        if (restorationFailed) {
            stopAfterRestorationFailure()
            return START_NOT_STICKY
        }
        return result
    }

    private fun stopAfterRestorationFailure() {
        if (!serviceStarted) return
        try {
            val channelId = DefaultMediaNotificationProvider.DEFAULT_CHANNEL_ID
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                getSystemService(NotificationManager::class.java).createNotificationChannel(
                    NotificationChannel(
                        channelId, getString(R.string.audio_notification_channel), NotificationManager.IMPORTANCE_LOW,
                    )
                )
            }
            val notification = NotificationCompat.Builder(this, channelId)
                .setSmallIcon(R.drawable.ic_audio)
                .setOnlyAlertOnce(true)
                .setVisibility(NotificationCompat.VISIBILITY_SECRET)
                .build()
            // A foreground-started service must promote even when restoration fails, then stop.
            ServiceCompat.startForeground(
                this, DefaultMediaNotificationProvider.DEFAULT_NOTIFICATION_ID,
                notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
            )
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (error: RuntimeException) {
            Log.w("AudioPlaybackService", "Unable to stop failed playback startup safely", error)
        } finally {
            stopSelf()
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession.takeIf {
            controllerInfo.packageName == packageName || controllerInfo.isTrusted
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        restorationReady.cancel(false)
        if (playbackRestored) {
            val snapshot = currentPlayer?.let(AudioPlaybackSnapshot::fromPlayer)
            updateAudioPlaybackSnapshot(snapshot)
            if (snapshot != null) {
                AudioHomeWidget.update(this, snapshot)
            } else {
                AudioHomeWidget.clear(this)
            }
        }
        lockScreenManager = null
        lockScreenPlayerManager?.release()
        lockScreenPlayerManager = null
        currentPlayer?.removeListener(widgetListener)
        currentPlayer = null
        mediaSession?.let { session ->
            val player = session.player
            session.release()
            player.release()
        }
        mediaSession = null
        super.onDestroy()
    }
}

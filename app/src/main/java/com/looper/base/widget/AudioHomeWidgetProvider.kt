package com.looper.base.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.looper.base.service.AudioPlaybackService
import com.looper.base.base.storage.audioPlaybackSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration.Companion.milliseconds

@OptIn(UnstableApi::class)
class AudioHomeWidgetProvider : AppWidgetProvider() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in setOf(
                AudioHomeWidget.ACTION_PREVIOUS,
                AudioHomeWidget.ACTION_TOGGLE_PLAY_PAUSE,
                AudioHomeWidget.ACTION_NEXT,
            )) {
            super.onReceive(context, intent)
            return
        }
        val pendingResult = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.Main).launch {
            var future: ListenableFuture<MediaController>? = null
            try {
                withTimeout(8_000L.milliseconds) {
                    val controllerFuture = MediaController.Builder(
                        appContext,
                        SessionToken(appContext, ComponentName(appContext, AudioPlaybackService::class.java)),
                    ).buildAsync()

                    future = controllerFuture

                    val controller = suspendCancellableCoroutine<MediaController> { continuation ->
                        continuation.invokeOnCancellation { controllerFuture.cancel(false) }
                        controllerFuture.addListener({
                            if (continuation.isActive) {
                                continuation.resumeWith(runCatching { controllerFuture.get() })
                            }
                        }, ContextCompat.getMainExecutor(appContext))
                    }

                    when (intent.action) {
                        AudioHomeWidget.ACTION_PREVIOUS -> controller.seekToPreviousMediaItem()
                        AudioHomeWidget.ACTION_TOGGLE_PLAY_PAUSE -> Util.handlePlayPauseButtonAction(controller)
                        AudioHomeWidget.ACTION_NEXT -> controller.seekToNextMediaItem()
                    }
                }
            } catch (error: Exception) {
                Log.w("hungdm", "Unable to connect to playback", error)
            } finally {
                future?.let(MediaController::releaseFuture)
                pendingResult.finish()
            }
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        AudioPlaybackService.currentPlayer?.let {
            AudioHomeWidget.update(context, it)
            return
        }
        val pendingResult = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.Main).launch {
            try {
                withTimeout(8_000L.milliseconds) {
                    val snapshot = audioPlaybackSnapshot.firstOrNull()

                    val player = AudioPlaybackService.currentPlayer

                    when {
                        player != null -> AudioHomeWidget.update(appContext, player)
                        snapshot != null -> AudioHomeWidget.update(appContext, snapshot)
                        else -> AudioHomeWidget.clear(appContext)
                    }
                }
            } catch (error: Exception) {
                Log.w("hungdm", "Unable to read playback snapshot", error)
            } finally {
                pendingResult?.finish()
            }
        }
    }
}

package com.looper.base.widget

import android.content.ComponentName
import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.looper.base.player.PlaybackService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class PlaybackWidgetAction : ActionCallback {

    companion object {
        const val PREVIOUS = "previous"
        const val TOGGLE = "toggle"
        const val NEXT = "next"
    }

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val command = parameters[PlaybackWidgetKeys.COMMAND] ?: return
        withContext(Dispatchers.Main) {
            val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
            val controller = MediaController.Builder(context, token).buildAsync().await()
            try {
                when (command) {
                    PREVIOUS -> controller.seekToPrevious()
                    NEXT -> controller.seekToNext()
                    TOGGLE -> if (controller.isPlaying) {
                        controller.pause()
                    } else {
                        controller.play()
                    }
                }
            } finally {
                controller.release()
            }
        }
    }

    private suspend fun <T> ListenableFuture<T>.await(): T =
        suspendCancellableCoroutine { continuation ->
            addListener(
                {
                    runCatching {
                        get()
                    }.onSuccess {
                        continuation.resume(value = it)
                    }.onFailure {
                        continuation.resumeWithException(it)
                    }
                },
                MoreExecutors.directExecutor(),
            )
            continuation.invokeOnCancellation {
                cancel(false)
            }
        }
}

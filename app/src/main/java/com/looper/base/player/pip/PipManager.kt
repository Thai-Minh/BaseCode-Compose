package com.looper.base.player.pip

import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Build
import android.util.Rational
import com.looper.base.player.PlayerState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class PipManager {

    companion object {
        const val ACTION_PLAY_PAUSE = "com.looper.base.pip.PLAY_PAUSE"
        private const val REQUEST_PLAY_PAUSE = 1001

        private val MIN_RATIO = Rational(100, 239)
        private val MAX_RATIO = Rational(239, 100)
        private val DEFAULT_RATIO = Rational(16, 9)
    }

    val isInPip: StateFlow<Boolean>
        field = MutableStateFlow(value = false)

    fun onPipModeChanged(inPip: Boolean) {
        isInPip.value = inPip
    }

    fun isSupported(context: Context): Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)

    fun shouldEnter(state: PlayerState): Boolean = state.hasVideo && state.isPlaying

    fun buildParams(context: Context, state: PlayerState): PictureInPictureParams {
        val builder = PictureInPictureParams.Builder()
            .setAspectRatio(aspectRatioOf(state))
            .setActions(listOf(playPauseAction(context, state.isPlaying)))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(shouldEnter(state))
            builder.setSeamlessResizeEnabled(true)
        }
        return builder.build()
    }

    private fun aspectRatioOf(state: PlayerState): Rational {
        if (!state.hasVideo) return DEFAULT_RATIO
        val size = state.videoSize
        val width =
            (size.width * size.pixelWidthHeightRatio).toInt().coerceAtLeast(minimumValue = 1)
        val ratio = Rational(width, size.height)
        return when {
            ratio < MIN_RATIO -> MIN_RATIO
            ratio > MAX_RATIO -> MAX_RATIO
            else -> ratio
        }
    }

    private fun playPauseAction(context: Context, isPlaying: Boolean): RemoteAction {
        val intent = android.content.Intent(ACTION_PLAY_PAUSE).setPackage(context.packageName)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_PLAY_PAUSE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val iconRes = if (isPlaying) {
            android.R.drawable.ic_media_pause
        } else {
            android.R.drawable.ic_media_play
        }
        val label = if (isPlaying) {
            "Pause"
        } else {
            "Play"
        }
        return RemoteAction(Icon.createWithResource(context, iconRes), label, label, pendingIntent)
    }
}

package com.looper.base.equalizer.audio

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.audiofx.AudioEffect
import androidx.media3.common.C

object SystemEqualizer {

    fun intent(context: Context, audioSessionId: Int): Intent? {
        if (audioSessionId == C.AUDIO_SESSION_ID_UNSET) {
            return null
        }
        val intent = Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL)
            .putExtra(AudioEffect.EXTRA_AUDIO_SESSION, audioSessionId)
            .putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
            .putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
        val available =
            context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
                .isNotEmpty()
        return intent.takeIf {
            available
        }
    }
}

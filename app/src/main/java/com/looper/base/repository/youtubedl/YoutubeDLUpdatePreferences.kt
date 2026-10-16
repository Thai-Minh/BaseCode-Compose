package com.looper.videodownloader.repository.youtubedl

import android.content.Context
import androidx.core.content.edit

internal class YoutubeDLUpdatePreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    fun shouldCheckForUpdate(nowMillis: Long = System.currentTimeMillis()): Boolean {
        val lastSuccessfulCheck = preferences.getLong(KEY_LAST_SUCCESSFUL_CHECK, 0L)
        return lastSuccessfulCheck !in 1..nowMillis || nowMillis - lastSuccessfulCheck >= UPDATE_CHECK_INTERVAL_MILLIS
    }

    fun markUpdateCheckSuccessful(nowMillis: Long = System.currentTimeMillis()) {
        preferences.edit {
            putLong(KEY_LAST_SUCCESSFUL_CHECK, nowMillis)
        }
    }

    private companion object {
        const val PREFERENCES_NAME = "youtube_dl_update_preferences"
        const val KEY_LAST_SUCCESSFUL_CHECK = "last_successful_check"
        const val UPDATE_CHECK_INTERVAL_MILLIS = 24L * 60L * 60L * 1_000L
    }
}

package com.looper.ads

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

object AdsDataPreference {
    private lateinit var sharedPreferences: SharedPreferences
    const val PREFS_FILE_NAME = "ads_data_prefs"
    const val LANGUAGE_CODE_ADS = "LANGUAGE_CODE_ADS"

    fun init(context: Context) {
        sharedPreferences = context.getSharedPreferences(PREFS_FILE_NAME, Context.MODE_PRIVATE)
    }

    var langCodeAds: String
        get() = sharedPreferences.getString(LANGUAGE_CODE_ADS, "en") ?: "en"
        set(value) {
            sharedPreferences.edit { putString(LANGUAGE_CODE_ADS, value) }
        }

    const val INTERS_SPLASH_ALLOW_AUTO_CLOSE = "INTERS_SPLASH_ALLOW_AUTO_CLOSE"
    const val INTERS_AUTO_CLOSE_DELAY_MS = "INTERS_AUTO_CLOSE_DELAY_MS"


    var intersSplashAllowAutoClose: Boolean
        get() = sharedPreferences.getBoolean(INTERS_SPLASH_ALLOW_AUTO_CLOSE, false)
        set(value) {
            sharedPreferences.edit { putBoolean(INTERS_SPLASH_ALLOW_AUTO_CLOSE, value) }
        }

    var intersAutoCloseDelayMs: Long
        get() = sharedPreferences.getLong(INTERS_AUTO_CLOSE_DELAY_MS, 5000)
        set(value) {
            sharedPreferences.edit { putLong(INTERS_AUTO_CLOSE_DELAY_MS, value) }
        }
}
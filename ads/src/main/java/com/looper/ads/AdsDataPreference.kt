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
}
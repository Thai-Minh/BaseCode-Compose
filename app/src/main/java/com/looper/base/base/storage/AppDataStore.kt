package com.looper.base.base.storage

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.annotation.MainThread
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.looper.ads.AdsDataPreference
import com.looper.base.MyApplication
import com.looper.base.audio.playback.AudioPlaybackSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.IOException
import androidx.core.net.toUri
import androidx.datastore.dataStore
import kotlin.collections.remove
import kotlin.collections.set
import kotlin.text.set
import kotlin.toString

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "my_app_name_data_store") // TODO: change name
private val dataStore: DataStore<Preferences>
    get() = MyApplication.instance.dataStore

private val storeScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

private fun dataStoreWrite(block: suspend () -> Unit) {
    storeScope.launch {
        try {
            block()
        } catch (_: Exception) {
        }
    }
}

private val data = dataStore.data.catch { exception ->
    if (exception is IOException) {
        emit(emptyPreferences())
    } else {
        throw exception
    }
}

/**
 * ----------------------------------------------------------------- SAVE BELOW ------------------------------------------------------------------------------
 *
 * */
val LANGUAGE_CODE_KEY = stringPreferencesKey("LANGUAGE_CODE_KEY")
val LanguageCode: Flow<String> = data.map { preferences ->
    preferences[LANGUAGE_CODE_KEY] ?: "en"
}.distinctUntilChanged()

fun updateLanguageCode(value: String) {
    AdsDataPreference.langCodeAds = value

    dataStoreWrite {
        dataStore.edit { settings ->
            settings[LANGUAGE_CODE_KEY] = value
        }
    }
}

val FIRST_OPEN = booleanPreferencesKey("FIRST_OPEN")
val firstOpen: Flow<Boolean> = data.map { preferences ->
    preferences[FIRST_OPEN] ?: true
}.distinctUntilChanged()

fun updateFirstOpen(value: Boolean) = dataStoreWrite {
    dataStore.edit { settings ->
        settings[FIRST_OPEN] = value
    }
}

private val AUDIO_LOCK_SCREEN_ENABLED = booleanPreferencesKey("AUDIO_LOCK_SCREEN_ENABLED")
val audioLockScreenEnabled: Flow<Boolean> = data.map { preferences ->
    preferences[AUDIO_LOCK_SCREEN_ENABLED] ?: false
}.distinctUntilChanged()

fun updateAudioLockScreenEnabled(value: Boolean) = dataStoreWrite {
    dataStore.edit { settings -> settings[AUDIO_LOCK_SCREEN_ENABLED] = value }
}

private val AUDIO_PLAYBACK_ID = stringPreferencesKey("AUDIO_PLAYBACK_ID")
private val AUDIO_PLAYBACK_URI = stringPreferencesKey("AUDIO_PLAYBACK_URI")
private val AUDIO_PLAYBACK_TITLE = stringPreferencesKey("AUDIO_PLAYBACK_TITLE")
private val AUDIO_PLAYBACK_ARTIST = stringPreferencesKey("AUDIO_PLAYBACK_ARTIST")
private val AUDIO_PLAYBACK_ARTWORK_URI = stringPreferencesKey("AUDIO_PLAYBACK_ARTWORK_URI")
private val AUDIO_PLAYBACK_POSITION = longPreferencesKey("AUDIO_PLAYBACK_POSITION")

val audioPlaybackSnapshot: Flow<AudioPlaybackSnapshot?> = dataStore.data.map { preferences ->
    val id = preferences[AUDIO_PLAYBACK_ID] ?: return@map null
    val uri = preferences[AUDIO_PLAYBACK_URI]?.takeIf { it.isNotEmpty() } ?: return@map null
    AudioPlaybackSnapshot(
        mediaId = id,
        uri = uri.toUri(),
        title = preferences[AUDIO_PLAYBACK_TITLE].orEmpty(),
        artist = preferences[AUDIO_PLAYBACK_ARTIST].orEmpty(),
        artworkUri = preferences[AUDIO_PLAYBACK_ARTWORK_URI]?.let(Uri::parse),
        positionMs = (preferences[AUDIO_PLAYBACK_POSITION] ?: 0L).coerceAtLeast(0L),
    )
}.distinctUntilChanged()

fun updateAudioPlaybackSnapshot(value: AudioPlaybackSnapshot?) = dataStoreWrite {
    dataStore.edit { settings ->
        if (value == null) {
            settings.remove(AUDIO_PLAYBACK_ID)
            settings.remove(AUDIO_PLAYBACK_URI)
            settings.remove(AUDIO_PLAYBACK_TITLE)
            settings.remove(AUDIO_PLAYBACK_ARTIST)
            settings.remove(AUDIO_PLAYBACK_POSITION)
        } else {
            settings[AUDIO_PLAYBACK_ID] = value.mediaId
            settings[AUDIO_PLAYBACK_URI] = value.uri.toString()
            settings[AUDIO_PLAYBACK_TITLE] = value.title
            settings[AUDIO_PLAYBACK_ARTIST] = value.artist
            settings[AUDIO_PLAYBACK_POSITION] = value.positionMs.coerceAtLeast(0L)
        }

        if (value?.artworkUri == null) {
            settings.remove(AUDIO_PLAYBACK_ARTWORK_URI)
        } else {
            settings[AUDIO_PLAYBACK_ARTWORK_URI] = value.artworkUri.toString()
        }
    }
}

/*************************************************** IAP *****************************************************************************************************/
val TOKEN_PURCHASE_SUCCESS_KEY = stringPreferencesKey("TOKEN_PURCHASE_SUCCESS")
val SUBSCRIPTION_INFO_KEY = stringPreferencesKey("SUBSCRIPTION_INFO")
val CANCEL_SUBSCRIPTION_COUNT_KEY = intPreferencesKey("CANCEL_SUBSCRIPTION_COUNT")
val LAST_DATE_SHOW_PENDING_KEY = stringPreferencesKey("LAST_DATE_SHOW_PENDING")

val tokenPurchaseSuccess: Flow<String> = data.map { preferences ->
    preferences[TOKEN_PURCHASE_SUCCESS_KEY] ?: ""
}.distinctUntilChanged()

val subscriptionInfo: Flow<String> = data.map { preferences ->
    preferences[SUBSCRIPTION_INFO_KEY] ?: "[]"
}.distinctUntilChanged()

val cancelSubsCount: Flow<Int> = data.map { preferences ->
    preferences[CANCEL_SUBSCRIPTION_COUNT_KEY] ?: 0
}.distinctUntilChanged()

val lastDayShowPending: Flow<String> = data.map { preferences ->
    preferences[LAST_DATE_SHOW_PENDING_KEY] ?: ""
}.distinctUntilChanged()

fun updateLastDayShowPending(value: String) = dataStoreWrite {
    dataStore.edit { settings ->
        settings[LAST_DATE_SHOW_PENDING_KEY] = value
    }
}

fun updateCancelSubsCount(value: Int) = dataStoreWrite {
    dataStore.edit { settings ->
        settings[CANCEL_SUBSCRIPTION_COUNT_KEY] = value
    }
}

fun updateTokenPurchaseSuccess(value: String) = dataStoreWrite {
    dataStore.edit { settings ->
        settings[TOKEN_PURCHASE_SUCCESS_KEY] = value
    }
}

fun updateSubscriptionInfo(value: String) = dataStoreWrite {
    dataStore.edit { settings ->
        settings[SUBSCRIPTION_INFO_KEY] = value
    }
}

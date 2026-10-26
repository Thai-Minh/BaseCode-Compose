package com.looper.base.base.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.looper.ads.AdsDataPreference
import com.looper.base.MyApplication
import com.looper.base.equalizer.model.EqConstants
import com.looper.base.equalizer.model.EqSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.IOException

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

private val data: Flow<Preferences> = flow {
    emitAll(dataStore.data)
}.catch { exception ->
    if (exception is IOException) {
        emit(emptyPreferences())
    } else {
        throw exception
    }
}.flowOn(Dispatchers.IO)

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

val EQ_ENABLED_KEY = booleanPreferencesKey("EQ_ENABLED")
val EQ_PRESET_ID_KEY = stringPreferencesKey("EQ_PRESET_ID")
val EQ_BAND_LEVELS_KEY = stringPreferencesKey("EQ_BAND_LEVELS")
val EQ_BASS_BOOST_KEY = intPreferencesKey("EQ_BASS_BOOST")
val EQ_VIRTUALIZER_KEY = intPreferencesKey("EQ_VIRTUALIZER")
val EQ_LOUDNESS_KEY = intPreferencesKey("EQ_LOUDNESS")

const val EQ_PRESET_CUSTOM = "__custom__"

val eqSettings: Flow<EqSettings> = data.map { preferences ->
    val presetRaw = preferences[EQ_PRESET_ID_KEY]
    EqSettings(
        enabled = preferences[EQ_ENABLED_KEY] ?: false,
        presetId = when (presetRaw) {
            null -> EqConstants.PRESET_ID_FLAT
            EQ_PRESET_CUSTOM -> null
            else -> presetRaw
        },
        bandLevelsMb = preferences[EQ_BAND_LEVELS_KEY]
            ?.split(',')
            ?.mapNotNull {
                it.trim().toIntOrNull()
            }
            ?: emptyList(),
        bassBoost = preferences[EQ_BASS_BOOST_KEY] ?: 0,
        virtualizer = preferences[EQ_VIRTUALIZER_KEY] ?: 0,
        loudnessGainMb = preferences[EQ_LOUDNESS_KEY] ?: 0,
    ).normalized()
}.distinctUntilChanged()

fun updateEqSettings(value: EqSettings) = dataStoreWrite {
    val normalized = value.normalized()
    dataStore.edit { settings ->
        settings[EQ_ENABLED_KEY] = normalized.enabled
        settings[EQ_PRESET_ID_KEY] = normalized.presetId ?: EQ_PRESET_CUSTOM
        settings[EQ_BAND_LEVELS_KEY] = normalized.bandLevelsMb.joinToString(separator = ",")
        settings[EQ_BASS_BOOST_KEY] = normalized.bassBoost
        settings[EQ_VIRTUALIZER_KEY] = normalized.virtualizer
        settings[EQ_LOUDNESS_KEY] = normalized.loudnessGainMb
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
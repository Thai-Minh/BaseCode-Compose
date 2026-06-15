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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
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
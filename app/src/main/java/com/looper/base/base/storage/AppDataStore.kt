package com.looper.base.base.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.looper.ads.AdsDataPreference
import com.looper.base.MyApplication
import com.looper.base.backup.model.BackupFrequency
import com.looper.base.backup.model.BackupSettings
import com.looper.base.database.data.LyricsProvider
import com.looper.base.download.model.AudioDownloadFormat
import com.looper.base.download.model.DownloadSettings
import com.looper.base.equalizer.model.AudioEffectsSettings
import com.looper.base.equalizer.model.EqConstants
import com.looper.base.equalizer.model.EqEngine
import com.looper.base.equalizer.model.EqSettings
import com.looper.base.equalizer.model.ReverbPreset
import com.looper.base.equalizer.model.TenBandEqSettings
import com.looper.base.lastfm.model.LastFmSettings
import com.looper.base.listentogether.model.ListenTogetherConstants
import com.looper.base.listentogether.model.ListenTogetherSettings
import com.looper.base.lyrics.model.LyricsSettings
import com.looper.base.player.model.AudioQuality
import com.looper.base.player.model.PlaybackSnapshot
import com.looper.base.player.model.PlayerSettings
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

const val DATA_STORE_NAME = "my_app_name_data_store"

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = DATA_STORE_NAME)
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

val TEN_BAND_ENGINE_KEY = stringPreferencesKey(name = "TEN_BAND_ENGINE")
val TEN_BAND_GAINS_KEY = stringPreferencesKey(name = "TEN_BAND_GAINS")
val TEN_BAND_PREAMP_KEY = floatPreferencesKey(name = "TEN_BAND_PREAMP")
val TEN_BAND_PROFILE_KEY = stringPreferencesKey(name = "TEN_BAND_PROFILE")

val tenBandEqSettings: Flow<TenBandEqSettings> = data.map { preferences ->
    val defaults = TenBandEqSettings()
    TenBandEqSettings(
        engine = preferences[TEN_BAND_ENGINE_KEY]
            ?.let { name ->
                EqEngine.entries.firstOrNull {
                    it.name == name
                }
            }
            ?: defaults.engine,
        gainsDb = preferences[TEN_BAND_GAINS_KEY]
            ?.split(',')
            ?.mapNotNull {
                it.trim().toFloatOrNull()
            }
            ?: defaults.gainsDb,
        preampDb = preferences[TEN_BAND_PREAMP_KEY] ?: defaults.preampDb,
        profileName = preferences[TEN_BAND_PROFILE_KEY],
    ).normalized()
}.distinctUntilChanged()

fun updateTenBandEqSettings(value: TenBandEqSettings) = dataStoreWrite {
    val normalized = value.normalized()
    dataStore.edit { settings ->
        settings[TEN_BAND_ENGINE_KEY] = normalized.engine.name
        settings[TEN_BAND_GAINS_KEY] = normalized.gainsDb.joinToString(separator = ",")
        settings[TEN_BAND_PREAMP_KEY] = normalized.preampDb
        val profileName = normalized.profileName
        if (profileName == null) {
            settings.remove(key = TEN_BAND_PROFILE_KEY)
        } else {
            settings[TEN_BAND_PROFILE_KEY] = profileName
        }
    }
}

val PLAYER_SPEED_KEY = floatPreferencesKey(name = "PLAYER_SPEED")
val PLAYER_PITCH_KEY = floatPreferencesKey(name = "PLAYER_PITCH")
val PLAYER_SKIP_SILENCE_KEY = booleanPreferencesKey(name = "PLAYER_SKIP_SILENCE")
val PLAYER_CACHE_SIZE_MB_KEY = intPreferencesKey(name = "PLAYER_CACHE_SIZE_MB")
val PLAYER_NORMALIZE_VOLUME_KEY = booleanPreferencesKey(name = "PLAYER_NORMALIZE_VOLUME")
val PLAYER_VIDEO_MAX_HEIGHT_KEY = intPreferencesKey(name = "PLAYER_VIDEO_MAX_HEIGHT")
val PLAYER_AUDIO_LANGUAGE_KEY = stringPreferencesKey(name = "PLAYER_AUDIO_LANGUAGE")
val PLAYER_SUBTITLE_LANGUAGE_KEY = stringPreferencesKey(name = "PLAYER_SUBTITLE_LANGUAGE")
val PLAYER_STOP_ON_TASK_REMOVED_KEY = booleanPreferencesKey(name = "PLAYER_STOP_ON_TASK_REMOVED")
val PLAYER_AUDIO_QUALITY_KEY = stringPreferencesKey(name = "PLAYER_AUDIO_QUALITY")

val playerSettings: Flow<PlayerSettings> = data.map { preferences ->
    val defaults = PlayerSettings()
    PlayerSettings(
        playbackSpeed = preferences[PLAYER_SPEED_KEY] ?: defaults.playbackSpeed,
        pitchSemitones = preferences[PLAYER_PITCH_KEY] ?: defaults.pitchSemitones,
        skipSilence = preferences[PLAYER_SKIP_SILENCE_KEY] ?: defaults.skipSilence,
        cacheSizeMb = preferences[PLAYER_CACHE_SIZE_MB_KEY] ?: defaults.cacheSizeMb,
        normalizeVolume = preferences[PLAYER_NORMALIZE_VOLUME_KEY] ?: defaults.normalizeVolume,
        videoMaxHeight = preferences[PLAYER_VIDEO_MAX_HEIGHT_KEY] ?: defaults.videoMaxHeight,
        preferredAudioLanguage = preferences[PLAYER_AUDIO_LANGUAGE_KEY],
        subtitleLanguage = preferences[PLAYER_SUBTITLE_LANGUAGE_KEY],
        stopOnTaskRemoved = preferences[PLAYER_STOP_ON_TASK_REMOVED_KEY]
            ?: defaults.stopOnTaskRemoved,
        audioQuality = preferences[PLAYER_AUDIO_QUALITY_KEY]?.let { name ->
            AudioQuality.entries.firstOrNull {
                it.name == name
            }
        } ?: defaults.audioQuality,
    ).normalized()
}.distinctUntilChanged()

fun updatePlayerSettings(value: PlayerSettings) = dataStoreWrite {
    val normalized = value.normalized()
    dataStore.edit { settings ->
        settings[PLAYER_SPEED_KEY] = normalized.playbackSpeed
        settings[PLAYER_PITCH_KEY] = normalized.pitchSemitones
        settings[PLAYER_SKIP_SILENCE_KEY] = normalized.skipSilence
        settings[PLAYER_CACHE_SIZE_MB_KEY] = normalized.cacheSizeMb
        settings[PLAYER_NORMALIZE_VOLUME_KEY] = normalized.normalizeVolume
        settings[PLAYER_VIDEO_MAX_HEIGHT_KEY] = normalized.videoMaxHeight
        settings.setOrRemove(
            key = PLAYER_AUDIO_LANGUAGE_KEY,
            value = normalized.preferredAudioLanguage
        )
        settings.setOrRemove(
            key = PLAYER_SUBTITLE_LANGUAGE_KEY,
            value = normalized.subtitleLanguage
        )
        settings[PLAYER_STOP_ON_TASK_REMOVED_KEY] = normalized.stopOnTaskRemoved
        settings[PLAYER_AUDIO_QUALITY_KEY] = normalized.audioQuality.name
    }
}

val PLAYBACK_INDEX_KEY = intPreferencesKey(name = "PLAYBACK_INDEX")
val PLAYBACK_POSITION_KEY = longPreferencesKey(name = "PLAYBACK_POSITION")
val PLAYBACK_REPEAT_MODE_KEY = intPreferencesKey(name = "PLAYBACK_REPEAT_MODE")
val PLAYBACK_SHUFFLE_KEY = booleanPreferencesKey(name = "PLAYBACK_SHUFFLE")

val playbackSnapshot: Flow<PlaybackSnapshot> = data.map { preferences ->
    val defaults = PlaybackSnapshot()
    PlaybackSnapshot(
        currentIndex = preferences[PLAYBACK_INDEX_KEY] ?: defaults.currentIndex,
        positionMs = preferences[PLAYBACK_POSITION_KEY] ?: defaults.positionMs,
        repeatMode = preferences[PLAYBACK_REPEAT_MODE_KEY] ?: defaults.repeatMode,
        shuffleEnabled = preferences[PLAYBACK_SHUFFLE_KEY] ?: defaults.shuffleEnabled,
    )
}.distinctUntilChanged()

fun updatePlaybackSnapshot(value: PlaybackSnapshot) = dataStoreWrite {
    dataStore.edit { settings ->
        settings[PLAYBACK_INDEX_KEY] = value.currentIndex
        settings[PLAYBACK_POSITION_KEY] = value.positionMs
        settings[PLAYBACK_REPEAT_MODE_KEY] = value.repeatMode
        settings[PLAYBACK_SHUFFLE_KEY] = value.shuffleEnabled
    }
}

val SMART_DOWNLOADS_ENABLED_KEY = booleanPreferencesKey(name = "SMART_DOWNLOADS_ENABLED")
val SMART_DOWNLOADS_COUNT_KEY = intPreferencesKey(name = "SMART_DOWNLOADS_COUNT")
val DOWNLOAD_UNMETERED_ONLY_KEY = booleanPreferencesKey(name = "DOWNLOAD_UNMETERED_ONLY")
val DOWNLOAD_AUTO_FAVORITES_KEY = booleanPreferencesKey(name = "DOWNLOAD_AUTO_FAVORITES")
val DOWNLOAD_AUDIO_FORMAT_KEY = stringPreferencesKey(name = "DOWNLOAD_AUDIO_FORMAT")
val DOWNLOAD_VIDEO_MAX_HEIGHT_KEY = intPreferencesKey(name = "DOWNLOAD_VIDEO_MAX_HEIGHT")

val downloadSettings: Flow<DownloadSettings> = data.map { preferences ->
    val defaults = DownloadSettings()
    DownloadSettings(
        smartDownloadsEnabled = preferences[SMART_DOWNLOADS_ENABLED_KEY]
            ?: defaults.smartDownloadsEnabled,
        smartDownloadsCount = preferences[SMART_DOWNLOADS_COUNT_KEY]
            ?: defaults.smartDownloadsCount,
        unmeteredOnly = preferences[DOWNLOAD_UNMETERED_ONLY_KEY] ?: defaults.unmeteredOnly,
        autoDownloadFavorites = preferences[DOWNLOAD_AUTO_FAVORITES_KEY]
            ?: defaults.autoDownloadFavorites,
        audioFormat = preferences[DOWNLOAD_AUDIO_FORMAT_KEY]
            ?.let { name ->
                AudioDownloadFormat.entries.firstOrNull {
                    it.name == name
                }
            }
            ?: defaults.audioFormat,
        videoMaxHeight = preferences[DOWNLOAD_VIDEO_MAX_HEIGHT_KEY] ?: defaults.videoMaxHeight,
    ).normalized()
}.distinctUntilChanged()

fun updateDownloadSettings(value: DownloadSettings) = dataStoreWrite {
    val normalized = value.normalized()
    dataStore.edit { settings ->
        settings[SMART_DOWNLOADS_ENABLED_KEY] = normalized.smartDownloadsEnabled
        settings[SMART_DOWNLOADS_COUNT_KEY] = normalized.smartDownloadsCount
        settings[DOWNLOAD_UNMETERED_ONLY_KEY] = normalized.unmeteredOnly
        settings[DOWNLOAD_AUTO_FAVORITES_KEY] = normalized.autoDownloadFavorites
        settings[DOWNLOAD_AUDIO_FORMAT_KEY] = normalized.audioFormat.name
        settings[DOWNLOAD_VIDEO_MAX_HEIGHT_KEY] = normalized.videoMaxHeight
    }
}

val AUTO_BACKUP_ENABLED_KEY = booleanPreferencesKey(name = "AUTO_BACKUP_ENABLED")
val AUTO_BACKUP_FREQUENCY_KEY = stringPreferencesKey(name = "AUTO_BACKUP_FREQUENCY")
val AUTO_BACKUP_MAX_FILES_KEY = intPreferencesKey(name = "AUTO_BACKUP_MAX_FILES")

val backupSettings: Flow<BackupSettings> = data.map { preferences ->
    val defaults = BackupSettings()
    BackupSettings(
        autoBackupEnabled = preferences[AUTO_BACKUP_ENABLED_KEY] ?: defaults.autoBackupEnabled,
        frequency = preferences[AUTO_BACKUP_FREQUENCY_KEY]
            ?.let { name ->
                BackupFrequency.entries.firstOrNull {
                    it.name == name
                }
            }
            ?: defaults.frequency,
        maxFiles = preferences[AUTO_BACKUP_MAX_FILES_KEY] ?: defaults.maxFiles,
    ).normalized()
}.distinctUntilChanged()

fun updateBackupSettings(value: BackupSettings) = dataStoreWrite {
    val normalized = value.normalized()
    dataStore.edit { settings ->
        settings[AUTO_BACKUP_ENABLED_KEY] = normalized.autoBackupEnabled
        settings[AUTO_BACKUP_FREQUENCY_KEY] = normalized.frequency.name
        settings[AUTO_BACKUP_MAX_FILES_KEY] = normalized.maxFiles
    }
}

val LASTFM_SCROBBLE_ENABLED_KEY = booleanPreferencesKey(name = "LASTFM_SCROBBLE_ENABLED")
val LASTFM_SESSION_KEY = stringPreferencesKey(name = "LASTFM_SESSION")
val LASTFM_USERNAME_KEY = stringPreferencesKey(name = "LASTFM_USERNAME")

val lastFmSettings: Flow<LastFmSettings> = data.map { preferences ->
    val defaults = LastFmSettings()
    LastFmSettings(
        scrobbleEnabled = preferences[LASTFM_SCROBBLE_ENABLED_KEY] ?: defaults.scrobbleEnabled,
        sessionKey = preferences[LASTFM_SESSION_KEY],
        username = preferences[LASTFM_USERNAME_KEY],
    )
}.distinctUntilChanged()

fun updateLastFmSettings(value: LastFmSettings) = dataStoreWrite {
    dataStore.edit { settings ->
        settings[LASTFM_SCROBBLE_ENABLED_KEY] = value.scrobbleEnabled
        val sessionKey = value.sessionKey
        val username = value.username
        if (sessionKey == null) {
            settings.remove(key = LASTFM_SESSION_KEY)
        } else {
            settings[LASTFM_SESSION_KEY] = sessionKey
        }
        if (username == null) {
            settings.remove(key = LASTFM_USERNAME_KEY)
        } else {
            settings[LASTFM_USERNAME_KEY] = username
        }
    }
}

val ECHO_ENABLED_KEY = booleanPreferencesKey(name = "ECHO_ENABLED")
val ECHO_DELAY_MS_KEY = intPreferencesKey(name = "ECHO_DELAY_MS")
val ECHO_FEEDBACK_KEY = floatPreferencesKey(name = "ECHO_FEEDBACK")
val ECHO_MIX_KEY = floatPreferencesKey(name = "ECHO_MIX")
val REVERB_ENABLED_KEY = booleanPreferencesKey(name = "REVERB_ENABLED")
val REVERB_PRESET_KEY = stringPreferencesKey(name = "REVERB_PRESET")
val REVERB_MIX_KEY = floatPreferencesKey(name = "REVERB_MIX")

val audioEffectsSettings: Flow<AudioEffectsSettings> = data.map { preferences ->
    val defaults = AudioEffectsSettings()
    AudioEffectsSettings(
        echoEnabled = preferences[ECHO_ENABLED_KEY] ?: defaults.echoEnabled,
        echoDelayMs = preferences[ECHO_DELAY_MS_KEY] ?: defaults.echoDelayMs,
        echoFeedback = preferences[ECHO_FEEDBACK_KEY] ?: defaults.echoFeedback,
        echoMix = preferences[ECHO_MIX_KEY] ?: defaults.echoMix,
        reverbEnabled = preferences[REVERB_ENABLED_KEY] ?: defaults.reverbEnabled,
        reverbPreset = preferences[REVERB_PRESET_KEY]
            ?.let { name ->
                ReverbPreset.entries.firstOrNull {
                    it.name == name
                }
            }
            ?: defaults.reverbPreset,
        reverbMix = preferences[REVERB_MIX_KEY] ?: defaults.reverbMix,
    ).normalized()
}.distinctUntilChanged()

fun updateAudioEffectsSettings(value: AudioEffectsSettings) = dataStoreWrite {
    val normalized = value.normalized()
    dataStore.edit { settings ->
        settings[ECHO_ENABLED_KEY] = normalized.echoEnabled
        settings[ECHO_DELAY_MS_KEY] = normalized.echoDelayMs
        settings[ECHO_FEEDBACK_KEY] = normalized.echoFeedback
        settings[ECHO_MIX_KEY] = normalized.echoMix
        settings[REVERB_ENABLED_KEY] = normalized.reverbEnabled
        settings[REVERB_PRESET_KEY] = normalized.reverbPreset.name
        settings[REVERB_MIX_KEY] = normalized.reverbMix
    }
}

val LT_SERVER_URL_KEY = stringPreferencesKey(name = "LT_SERVER_URL")
val LT_DISPLAY_NAME_KEY = stringPreferencesKey(name = "LT_DISPLAY_NAME")
val LT_AUTO_APPROVE_JOINS_KEY = booleanPreferencesKey(name = "LT_AUTO_APPROVE_JOINS")
val LT_AUTO_APPROVE_SUGGESTIONS_KEY = booleanPreferencesKey(name = "LT_AUTO_APPROVE_SUGGESTIONS")
val LT_BLOCKED_NAMES_KEY = stringSetPreferencesKey(name = "LT_BLOCKED_NAMES")

val listenTogetherSettings: Flow<ListenTogetherSettings> = data.map { preferences ->
    val defaults = ListenTogetherSettings()
    ListenTogetherSettings(
        serverUrl = preferences[LT_SERVER_URL_KEY],
        displayName = preferences[LT_DISPLAY_NAME_KEY] ?: defaults.displayName,
        autoApproveJoins = preferences[LT_AUTO_APPROVE_JOINS_KEY] ?: defaults.autoApproveJoins,
        autoApproveSuggestions = preferences[LT_AUTO_APPROVE_SUGGESTIONS_KEY]
            ?: defaults.autoApproveSuggestions,
        blockedNames = preferences[LT_BLOCKED_NAMES_KEY] ?: defaults.blockedNames,
    )
}.distinctUntilChanged()

fun updateListenTogetherSettings(value: ListenTogetherSettings) = dataStoreWrite {
    dataStore.edit { settings ->
        settings.setOrRemove(
            key = LT_SERVER_URL_KEY,
            value = value.serverUrl?.trim()?.takeIf { it.isNotEmpty() })
        settings[LT_DISPLAY_NAME_KEY] =
            value.displayName.take(n = ListenTogetherConstants.MAX_USERNAME_LENGTH)
        settings[LT_AUTO_APPROVE_JOINS_KEY] = value.autoApproveJoins
        settings[LT_AUTO_APPROVE_SUGGESTIONS_KEY] = value.autoApproveSuggestions
        settings[LT_BLOCKED_NAMES_KEY] = value.blockedNames
    }
}

private fun <T> MutablePreferences.setOrRemove(key: Preferences.Key<T>, value: T?) {
    if (value == null) {
        remove(key = key)
    } else {
        this[key] = value
    }
}

val LYRICS_ENABLED_KEY = booleanPreferencesKey(name = "LYRICS_ENABLED")
val LYRICS_WORD_SYNC_KEY = booleanPreferencesKey(name = "LYRICS_WORD_SYNC")
val LYRICS_USE_EMBEDDED_KEY = booleanPreferencesKey(name = "LYRICS_USE_EMBEDDED")
val LYRICS_PROVIDERS_KEY = stringPreferencesKey(name = "LYRICS_PROVIDERS")
val LYRICS_OFFSET_MS_KEY = longPreferencesKey(name = "LYRICS_OFFSET_MS")
val LYRICS_TEXT_SIZE_KEY = intPreferencesKey(name = "LYRICS_TEXT_SIZE")
val LYRICS_TAP_TO_SEEK_KEY = booleanPreferencesKey(name = "LYRICS_TAP_TO_SEEK")
val LYRICS_TRANSLATION_ENABLED_KEY = booleanPreferencesKey(name = "LYRICS_TRANSLATION_ENABLED")
val LYRICS_TRANSLATION_LANGUAGE_KEY = stringPreferencesKey(name = "LYRICS_TRANSLATION_LANGUAGE")
val LYRICS_ROMANIZATION_ENABLED_KEY = booleanPreferencesKey(name = "LYRICS_ROMANIZATION_ENABLED")
val LYRICS_TRANSLATION_EXCLUDED_KEY = stringSetPreferencesKey(name = "LYRICS_TRANSLATION_EXCLUDED")
val LYRICS_ROMANIZATION_EXCLUDED_KEY =
    stringSetPreferencesKey(name = "LYRICS_ROMANIZATION_EXCLUDED")
val LYRICS_BLUR_INACTIVE_KEY = booleanPreferencesKey(name = "LYRICS_BLUR_INACTIVE")
val LYRICS_GLOW_LONG_WORDS_KEY = booleanPreferencesKey(name = "LYRICS_GLOW_LONG_WORDS")

private const val LYRICS_PROVIDER_DISABLED_PREFIX = "-"

val lyricsSettings: Flow<LyricsSettings> = data.map { preferences ->
    val defaults = LyricsSettings()
    val providerTokens = preferences[LYRICS_PROVIDERS_KEY]
        ?.split(',')
        ?.map {
            it.trim()
        }?.filter {
            it.isNotEmpty()
        }
    LyricsSettings(
        enabled = preferences[LYRICS_ENABLED_KEY] ?: defaults.enabled,
        wordSync = preferences[LYRICS_WORD_SYNC_KEY] ?: defaults.wordSync,
        useEmbedded = preferences[LYRICS_USE_EMBEDDED_KEY] ?: defaults.useEmbedded,
        providerOrder = providerTokens
            ?.mapNotNull {
                LyricsProvider.fromName(name = it.removePrefix(prefix = LYRICS_PROVIDER_DISABLED_PREFIX))
            }
            ?: defaults.providerOrder,
        disabledProviders = providerTokens
            ?.filter {
                it.startsWith(prefix = LYRICS_PROVIDER_DISABLED_PREFIX)
            }?.mapNotNull {
                LyricsProvider.fromName(name = it.removePrefix(prefix = LYRICS_PROVIDER_DISABLED_PREFIX))
            }
            ?.toSet()
            ?: defaults.disabledProviders,
        offsetMs = preferences[LYRICS_OFFSET_MS_KEY] ?: defaults.offsetMs,
        textSizeSp = preferences[LYRICS_TEXT_SIZE_KEY] ?: defaults.textSizeSp,
        tapToSeek = preferences[LYRICS_TAP_TO_SEEK_KEY] ?: defaults.tapToSeek,
        translationEnabled = preferences[LYRICS_TRANSLATION_ENABLED_KEY]
            ?: defaults.translationEnabled,
        translationLanguage = preferences[LYRICS_TRANSLATION_LANGUAGE_KEY]
            ?: defaults.translationLanguage,
        romanizationEnabled = preferences[LYRICS_ROMANIZATION_ENABLED_KEY]
            ?: defaults.romanizationEnabled,
        translationExcludedLanguages = preferences[LYRICS_TRANSLATION_EXCLUDED_KEY]
            ?: defaults.translationExcludedLanguages,
        romanizationExcludedLanguages = preferences[LYRICS_ROMANIZATION_EXCLUDED_KEY]
            ?: defaults.romanizationExcludedLanguages,
        blurInactiveLines = preferences[LYRICS_BLUR_INACTIVE_KEY] ?: defaults.blurInactiveLines,
        glowLongWords = preferences[LYRICS_GLOW_LONG_WORDS_KEY] ?: defaults.glowLongWords,
    ).normalized()
}.distinctUntilChanged()

fun updateLyricsSettings(value: LyricsSettings) = dataStoreWrite {
    val normalized = value.normalized()
    dataStore.edit { settings ->
        settings[LYRICS_ENABLED_KEY] = normalized.enabled
        settings[LYRICS_WORD_SYNC_KEY] = normalized.wordSync
        settings[LYRICS_USE_EMBEDDED_KEY] = normalized.useEmbedded
        settings[LYRICS_PROVIDERS_KEY] = normalized.providerOrder.joinToString(separator = ",") {
            if (it in normalized.disabledProviders) {
                LYRICS_PROVIDER_DISABLED_PREFIX + it.name
            } else {
                it.name
            }
        }
        settings[LYRICS_OFFSET_MS_KEY] = normalized.offsetMs
        settings[LYRICS_TEXT_SIZE_KEY] = normalized.textSizeSp
        settings[LYRICS_TAP_TO_SEEK_KEY] = normalized.tapToSeek
        settings[LYRICS_TRANSLATION_ENABLED_KEY] = normalized.translationEnabled
        val translationLanguage = normalized.translationLanguage
        if (translationLanguage == null) {
            settings.remove(key = LYRICS_TRANSLATION_LANGUAGE_KEY)
        } else {
            settings[LYRICS_TRANSLATION_LANGUAGE_KEY] = translationLanguage
        }
        settings[LYRICS_ROMANIZATION_ENABLED_KEY] = normalized.romanizationEnabled
        settings[LYRICS_TRANSLATION_EXCLUDED_KEY] = normalized.translationExcludedLanguages
        settings[LYRICS_ROMANIZATION_EXCLUDED_KEY] = normalized.romanizationExcludedLanguages
        settings[LYRICS_BLUR_INACTIVE_KEY] = normalized.blurInactiveLines
        settings[LYRICS_GLOW_LONG_WORDS_KEY] = normalized.glowLongWords
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
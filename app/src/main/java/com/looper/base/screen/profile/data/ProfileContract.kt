package com.looper.base.screen.profile.data

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.looper.base.R
import com.looper.base.base.mvi.UiEffect
import com.looper.base.base.mvi.UiIntent
import com.looper.base.base.mvi.UiState
import com.looper.base.player.model.AudioQuality
import com.looper.base.storage.StorageUsage

enum class ProfileSetting(
    @DrawableRes val iconRes: Int,
    @StringRes val labelRes: Int,
) {
    Equalizer(
        iconRes = R.drawable.ic_profile_equalizer,
        labelRes = R.string.profile_setting_equalizer,
    ),
    AudioQuality(
        iconRes = R.drawable.ic_profile_audio_quality,
        labelRes = R.string.profile_setting_audio_quality,
    ),
    ClearCache(
        iconRes = R.drawable.ic_profile_clear_cache,
        labelRes = R.string.profile_setting_clear_cache,
    ),
    About(
        iconRes = R.drawable.ic_profile_about,
        labelRes = R.string.profile_setting_about,
    ),
}

enum class ProfileSheet {
    Equalizer,
    AudioQuality,
    ClearCache,
}

enum class AudioQualityOption(
    val quality: AudioQuality,
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int,
) {
    Auto(
        quality = AudioQuality.AUTO,
        titleRes = R.string.profile_quality_auto,
        descriptionRes = R.string.profile_quality_auto_description,
    ),
    Low(
        quality = AudioQuality.LOW,
        titleRes = R.string.profile_quality_low,
        descriptionRes = R.string.profile_quality_low_description,
    ),
    Normal(
        quality = AudioQuality.NORMAL,
        titleRes = R.string.profile_quality_normal,
        descriptionRes = R.string.profile_quality_normal_description,
    ),
    High(
        quality = AudioQuality.HIGH,
        titleRes = R.string.profile_quality_high,
        descriptionRes = R.string.profile_quality_high_description,
    ),
    Lossless(
        quality = AudioQuality.LOSSLESS,
        titleRes = R.string.profile_quality_lossless,
        descriptionRes = R.string.profile_quality_lossless_description,
    ),
}

data class MiniPlayerUi(
    val title: String,
    val artist: String,
    val artwork: Any?,
    val isPlaying: Boolean,
    val hasNext: Boolean,
)

data class StorageUi(
    val otherAppsBytes: Long,
    val downloadsBytes: Long,
    val cacheBytes: Long,
    val freeBytes: Long,
    val totalBytes: Long,
) {
    val usedBytes: Long
        get() = (totalBytes - freeBytes).coerceAtLeast(minimumValue = 0L)

    fun fractionOf(bytes: Long): Float = if (totalBytes > 0L) {
        (bytes.toFloat() / totalBytes).coerceIn(minimumValue = 0f, maximumValue = 1f)
    } else {
        0f
    }

    companion object {
        fun from(usage: StorageUsage): StorageUi {
            val cache = usage.playerCacheBytes + usage.imageCacheBytes
            val used = (usage.totalBytes - usage.freeBytes).coerceAtLeast(minimumValue = 0L)
            return StorageUi(
                otherAppsBytes = (used - usage.downloadsBytes - cache).coerceAtLeast(minimumValue = 0L),
                downloadsBytes = usage.downloadsBytes,
                cacheBytes = cache,
                freeBytes = usage.freeBytes,
                totalBytes = usage.totalBytes,
            )
        }
    }
}

data class ProfileUiState(
    val downloadedCount: Int = 0,
    val listeningMs: Long = 0L,
    val settings: List<ProfileSetting> = ProfileSetting.entries,
    val sheet: ProfileSheet? = null,
    val audioQualityOptions: List<AudioQualityOption> = AudioQualityOption.entries,
    val audioQuality: AudioQuality = AudioQuality.AUTO,
    val storage: StorageUi? = null,
    val isClearCacheDialogVisible: Boolean = false,
    val isClearingCache: Boolean = false,
    val miniPlayer: MiniPlayerUi? = null,
) : UiState {
    val listeningHours: Long
        get() = listeningMs / HOUR_MS

    val listeningMinutes: Long
        get() = (listeningMs % HOUR_MS) / MINUTE_MS

    private companion object {
        const val MINUTE_MS = 60_000L
        const val HOUR_MS = 60L * MINUTE_MS
    }
}

sealed interface ProfileIntent : UiIntent {
    data class SettingClick(val setting: ProfileSetting) : ProfileIntent
    data object DismissSheet : ProfileIntent
    data class SelectAudioQuality(val quality: AudioQuality) : ProfileIntent
    data object ClearCacheClick : ProfileIntent
    data object DismissClearCacheDialog : ProfileIntent
    data object ConfirmClearCache : ProfileIntent
    data object MiniPlayerPlayPauseClick : ProfileIntent
    data object MiniPlayerNextClick : ProfileIntent
}

sealed interface ProfileEffect : UiEffect {
    data object NavigateToAbout : ProfileEffect
    data class ShowMessage(@StringRes val resId: Int) : ProfileEffect
}

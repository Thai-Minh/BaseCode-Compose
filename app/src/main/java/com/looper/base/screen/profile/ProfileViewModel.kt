package com.looper.base.screen.profile

import androidx.lifecycle.viewModelScope
import com.looper.base.R
import com.looper.base.base.mvi.MviViewModel
import com.looper.base.base.storage.playerSettings
import com.looper.base.base.storage.updatePlayerSettings
import com.looper.base.download.data.DownloadRepository
import com.looper.base.player.PlayerConnection
import com.looper.base.player.model.AudioQuality
import com.looper.base.screen.profile.data.MiniPlayerUi
import com.looper.base.screen.profile.data.ProfileEffect
import com.looper.base.screen.profile.data.ProfileIntent
import com.looper.base.screen.profile.data.ProfileSetting
import com.looper.base.screen.profile.data.ProfileSheet
import com.looper.base.screen.profile.data.ProfileUiState
import com.looper.base.screen.profile.data.StorageUi
import com.looper.base.stats.data.ListeningStatsRepository
import com.looper.base.storage.StorageRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ProfileViewModel(
    private val playerConnection: PlayerConnection,
    private val storageRepository: StorageRepository,
    downloadRepository: DownloadRepository,
    listeningStatsRepository: ListeningStatsRepository,
) : MviViewModel<ProfileUiState, ProfileIntent, ProfileEffect>(
    initialState = ProfileUiState(),
) {

    init {
        viewModelScope.launch {
            downloadRepository.files.collect { files ->
                setState {
                    copy(
                        downloadedCount = files.distinctBy {
                            it.mediaItemId
                        }.size
                    )
                }
            }
        }
        viewModelScope.launch {
            listeningStatsRepository.observeTotalListenedMs().collect { listened ->
                setState {
                    copy(listeningMs = listened)
                }
            }
        }
        viewModelScope.launch {
            playerSettings.collect { settings ->
                setState {
                    copy(audioQuality = settings.audioQuality)
                }
            }
        }
        viewModelScope.launch {
            playerConnection.state.collect { player ->
                setState {
                    copy(
                        miniPlayer = if (player.hasMedia) {
                            MiniPlayerUi(
                                title = player.title.orEmpty(),
                                artist = player.artist.orEmpty(),
                                artwork = player.artworkUri,
                                isPlaying = player.isPlaying,
                                hasNext = player.hasNext,
                            )
                        } else {
                            null
                        }
                    )
                }
            }
        }
    }

    override fun onIntent(intent: ProfileIntent) {
        when (intent) {
            is ProfileIntent.SettingClick -> openSetting(setting = intent.setting)

            ProfileIntent.DismissSheet -> setState {
                copy(
                    sheet = null,
                    isClearCacheDialogVisible = false
                )
            }

            is ProfileIntent.SelectAudioQuality -> selectAudioQuality(quality = intent.quality)

            ProfileIntent.ClearCacheClick -> setState {
                copy(isClearCacheDialogVisible = true)
            }

            ProfileIntent.DismissClearCacheDialog -> setState {
                copy(isClearCacheDialogVisible = false)
            }

            ProfileIntent.ConfirmClearCache -> clearCache()

            ProfileIntent.MiniPlayerPlayPauseClick -> playerConnection.togglePlayPause()

            ProfileIntent.MiniPlayerNextClick -> playerConnection.skipToNext()
        }
    }

    private fun openSetting(setting: ProfileSetting) {
        when (setting) {
            ProfileSetting.Equalizer -> setState {
                copy(sheet = ProfileSheet.Equalizer)
            }

            ProfileSetting.AudioQuality -> setState {
                copy(sheet = ProfileSheet.AudioQuality)
            }

            ProfileSetting.ClearCache -> {
                setState {
                    copy(sheet = ProfileSheet.ClearCache)
                }
                refreshStorage()
            }

            ProfileSetting.About -> sendEffect(effect = ProfileEffect.NavigateToAbout)
        }
    }

    private fun selectAudioQuality(quality: AudioQuality) {
        setState {
            copy(audioQuality = quality)
        }
        viewModelScope.launch {
            updatePlayerSettings(value = playerSettings.first().copy(audioQuality = quality))
        }
    }

    private fun refreshStorage() {
        viewModelScope.launch {
            val usage = runCatching {
                storageRepository.usage()
            }.getOrNull() ?: return@launch
            setState {
                copy(storage = StorageUi.from(usage = usage))
            }
        }
    }

    private fun clearCache() {
        if (currentState.isClearingCache) {
            return
        }
        setState {
            copy(
                isClearCacheDialogVisible = false,
                isClearingCache = true
            )
        }
        viewModelScope.launch {
            runCatching {
                storageRepository.clearPlayerCache()
                storageRepository.clearImageCache()
            }
            setState {
                copy(isClearingCache = false)
            }
            refreshStorage()
            sendEffect(effect = ProfileEffect.ShowMessage(resId = R.string.profile_cache_cleared))
        }
    }
}

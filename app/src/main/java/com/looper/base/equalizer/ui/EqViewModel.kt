package com.looper.base.equalizer.ui

import androidx.lifecycle.viewModelScope
import com.looper.base.R
import com.looper.base.base.mvi.MviViewModel
import com.looper.base.equalizer.audio.AudioEffectController
import com.looper.base.equalizer.data.EqPresetResult
import com.looper.base.equalizer.data.EqRepository
import com.looper.base.equalizer.model.BuiltInEqPresets
import com.looper.base.equalizer.model.EqConstants
import com.looper.base.equalizer.model.EqPreset
import com.looper.base.equalizer.model.EqSettings
import com.looper.base.equalizer.model.applyTo
import com.looper.base.equalizer.ui.data.EqEffect
import com.looper.base.equalizer.ui.data.EqIntent
import com.looper.base.equalizer.ui.data.EqState
import kotlinx.coroutines.launch

class EqViewModel(
    private val repository: EqRepository,
    effectController: AudioEffectController,
) : MviViewModel<EqState, EqIntent, EqEffect>(initialState = EqState()) {

    private companion object {
        const val LOCAL_EDIT_GRACE_MS = 1_000L
    }

    private var lastLocalEditAt = 0L

    init {
        viewModelScope.launch {
            repository.settings.collect { persisted ->
                val recentlyEdited =
                    System.currentTimeMillis() - lastLocalEditAt < LOCAL_EDIT_GRACE_MS
                setState {
                    if (isLoading || !recentlyEdited) {
                        copy(settings = persisted, isLoading = false)
                    } else {
                        this
                    }
                }
            }
        }
        viewModelScope.launch {
            repository.presets.collect { presets ->
                setState {
                    copy(presets = presets)
                }
            }
        }
        viewModelScope.launch {
            effectController.capabilities.collect { caps ->
                setState {
                    copy(
                        supported = caps.supported,
                        attached = caps.attached,
                        minLevelMb = if (caps.attached) {
                            caps.minLevelMb
                        } else {
                            EqConstants.MIN_LEVEL_MB
                        },
                        maxLevelMb = if (caps.attached) {
                            caps.maxLevelMb
                        } else {
                            EqConstants.MAX_LEVEL_MB
                        },
                    )
                }
            }
        }
        viewModelScope.launch {
            effectController.audioSessionId.collect { id ->
                setState {
                    copy(audioSessionId = id)
                }
            }
        }
    }

    override fun onIntent(intent: EqIntent) {
        when (intent) {
            is EqIntent.ToggleEnabled -> editSettings {
                copy(enabled = intent.enabled)
            }

            is EqIntent.ChangeBand -> editSettings {
                val levels = bandLevelsMb.toMutableList()
                if (intent.index !in levels.indices) {
                    return@editSettings this
                }
                levels[intent.index] = intent.levelMb
                copy(presetId = null, bandLevelsMb = levels)
            }

            is EqIntent.ChangeBassBoost -> editSettings {
                copy(
                    presetId = null,
                    bassBoost = intent.strength
                )
            }

            is EqIntent.ChangeVirtualizer -> editSettings {
                copy(
                    presetId = null,
                    virtualizer = intent.strength
                )
            }

            is EqIntent.ChangeLoudness -> editSettings {
                copy(loudnessGainMb = intent.gainMb)
            }

            is EqIntent.SelectPreset -> editSettings {
                intent.preset.applyTo(this).copy(enabled = true)
            }

            EqIntent.Reset -> editSettings {
                BuiltInEqPresets.flat.applyTo(this).copy(loudnessGainMb = 0)
            }

            EqIntent.OpenSaveDialog -> setState {
                copy(isSaveDialogVisible = true)
            }

            EqIntent.DismissSaveDialog -> setState {
                copy(isSaveDialogVisible = false)
            }

            is EqIntent.SavePreset -> savePreset(intent.name)

            is EqIntent.OpenManagePreset -> setState {
                copy(managedPresetId = intent.preset.takeUnless {
                    it.isBuiltIn
                }?.id)
            }

            EqIntent.DismissManagePreset -> setState {
                copy(managedPresetId = null)
            }

            is EqIntent.RenamePreset -> renamePreset(preset = intent.preset, name = intent.name)
            is EqIntent.DeletePreset -> deletePreset(preset = intent.preset)
        }
    }

    private fun savePreset(name: String) {
        setState {
            copy(isSaveDialogVisible = false)
        }
        viewModelScope.launch {
            when (val result =
                repository.savePreset(name = name, settings = currentState.settings)) {
                is EqPresetResult.Success -> {
                    editSettings {
                        copy(presetId = result.preset.id)
                    }
                    sendEffect(effect = EqEffect.ShowMessage(resId = R.string.eq_preset_saved))
                }

                EqPresetResult.EmptyName -> sendEffect(effect = EqEffect.ShowMessage(resId = R.string.eq_preset_name_empty))
                EqPresetResult.DuplicateName -> sendEffect(effect = EqEffect.ShowMessage(resId = R.string.eq_preset_name_exists))
            }
        }
    }

    private fun renamePreset(preset: EqPreset, name: String) {
        setState {
            copy(managedPresetId = null)
        }
        if (preset.isBuiltIn) {
            return
        }
        viewModelScope.launch {
            when (repository.renamePreset(preset.id, name)) {
                is EqPresetResult.Success -> Unit
                EqPresetResult.EmptyName -> sendEffect(effect = EqEffect.ShowMessage(resId = R.string.eq_preset_name_empty))
                EqPresetResult.DuplicateName -> sendEffect(effect = EqEffect.ShowMessage(resId = R.string.eq_preset_name_exists))
            }
        }
    }

    private fun deletePreset(preset: EqPreset) {
        setState {
            copy(managedPresetId = null)
        }
        if (preset.isBuiltIn) {
            return
        }
        viewModelScope.launch {
            repository.deletePreset(id = preset.id)
            if (currentState.settings.presetId == preset.id) {
                editSettings {
                    copy(presetId = null)
                }
            }
            sendEffect(effect = EqEffect.ShowMessage(resId = R.string.eq_preset_deleted))
        }
    }

    private fun editSettings(reducer: EqSettings.() -> EqSettings) {
        val updated = currentState.settings.reducer().normalized()
        if (updated == currentState.settings) {
            return
        }
        lastLocalEditAt = System.currentTimeMillis()
        setState {
            copy(settings = updated)
        }
        repository.apply(settings = updated)
    }
}

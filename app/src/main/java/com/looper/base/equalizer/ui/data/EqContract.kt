package com.looper.base.equalizer.ui.data

import androidx.annotation.StringRes
import com.looper.base.base.mvi.UiEffect
import com.looper.base.base.mvi.UiIntent
import com.looper.base.base.mvi.UiState
import com.looper.base.equalizer.model.BuiltInEqPresets
import com.looper.base.equalizer.model.EqConstants
import com.looper.base.equalizer.model.EqPreset
import com.looper.base.equalizer.model.EqSettings

data class EqState(
    val isLoading: Boolean = true,
    val supported: Boolean = true,
    val attached: Boolean = false,
    val audioSessionId: Int = EqConstants.AUDIO_SESSION_UNSET,
    val settings: EqSettings = EqSettings(),
    val presets: List<EqPreset> = BuiltInEqPresets.all,
    val bandFreqsHz: List<Int> = EqConstants.STANDARD_FREQS_HZ,
    val minLevelMb: Int = EqConstants.MIN_LEVEL_MB,
    val maxLevelMb: Int = EqConstants.MAX_LEVEL_MB,
    val isSaveDialogVisible: Boolean = false,
    val managedPresetId: String? = null,
) : UiState {
    val selectedPreset: EqPreset?
        get() = presets.firstOrNull {
            it.id == settings.presetId
        }
    val managedPreset: EqPreset?
        get() = presets.firstOrNull {
            it.id == managedPresetId && !it.isBuiltIn
        }
    val isCustom: Boolean get() = settings.isCustom
}

sealed interface EqIntent : UiIntent {
    data class ToggleEnabled(val enabled: Boolean) : EqIntent
    data class ChangeBand(val index: Int, val levelMb: Int) : EqIntent
    data class ChangeBassBoost(val strength: Int) : EqIntent
    data class ChangeVirtualizer(val strength: Int) : EqIntent
    data class ChangeLoudness(val gainMb: Int) : EqIntent
    data class SelectPreset(val preset: EqPreset) : EqIntent
    data object Reset : EqIntent

    data object OpenSaveDialog : EqIntent
    data object DismissSaveDialog : EqIntent
    data class SavePreset(val name: String) : EqIntent

    data class OpenManagePreset(val preset: EqPreset) : EqIntent
    data object DismissManagePreset : EqIntent
    data class RenamePreset(val preset: EqPreset, val name: String) : EqIntent
    data class DeletePreset(val preset: EqPreset) : EqIntent
}

sealed interface EqEffect : UiEffect {
    data class ShowMessage(@StringRes val resId: Int) : EqEffect
}

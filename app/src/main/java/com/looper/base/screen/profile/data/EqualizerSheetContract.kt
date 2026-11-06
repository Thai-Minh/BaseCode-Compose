package com.looper.base.screen.profile.data

import androidx.annotation.StringRes
import com.looper.base.R
import com.looper.base.base.mvi.UiEffect
import com.looper.base.base.mvi.UiIntent
import com.looper.base.base.mvi.UiState
import com.looper.base.equalizer.autoeq.AutoEqEntry
import com.looper.base.equalizer.model.EqEngine
import com.looper.base.equalizer.model.TenBandEqConstants
import com.looper.base.equalizer.model.TenBandEqSettings
import com.looper.base.equalizer.model.TenBandPreset
import com.looper.base.equalizer.model.TenBandPresets

enum class EqPresetText(
    val presetId: String,
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int,
) {
    Flat(
        presetId = "flat",
        titleRes = R.string.eq_preset_flat,
        descriptionRes = R.string.eq_preset_flat_description,
    ),
    Acoustic(
        presetId = "acoustic",
        titleRes = R.string.eq_preset_acoustic,
        descriptionRes = R.string.eq_preset_acoustic_description,
    ),
    BassBooster(
        presetId = "bass_booster",
        titleRes = R.string.eq_preset_bass_booster,
        descriptionRes = R.string.eq_preset_bass_booster_description,
    ),
    BassReducer(
        presetId = "bass_reducer",
        titleRes = R.string.eq_preset_bass_reducer,
        descriptionRes = R.string.eq_preset_bass_reducer_description,
    ),
    Classical(
        presetId = "classical",
        titleRes = R.string.eq_preset_classical,
        descriptionRes = R.string.eq_preset_classical_description,
    ),
    Dance(
        presetId = "dance",
        titleRes = R.string.eq_preset_dance,
        descriptionRes = R.string.eq_preset_dance_description,
    ),
    Deep(
        presetId = "deep",
        titleRes = R.string.eq_preset_deep,
        descriptionRes = R.string.eq_preset_deep_description,
    ),
    Electronic(
        presetId = "electronic",
        titleRes = R.string.eq_preset_electronic,
        descriptionRes = R.string.eq_preset_electronic_description,
    ),
    HipHop(
        presetId = "hip_hop",
        titleRes = R.string.eq_preset_hip_hop,
        descriptionRes = R.string.eq_preset_hip_hop_description,
    ),
    Jazz(
        presetId = "jazz",
        titleRes = R.string.eq_preset_jazz,
        descriptionRes = R.string.eq_preset_jazz_description,
    ),
    Latin(
        presetId = "latin",
        titleRes = R.string.eq_preset_latin,
        descriptionRes = R.string.eq_preset_latin_description,
    ),
    Loudness(
        presetId = "loudness",
        titleRes = R.string.eq_preset_loudness,
        descriptionRes = R.string.eq_preset_loudness_description,
    ),
    Lounge(
        presetId = "lounge",
        titleRes = R.string.eq_preset_lounge,
        descriptionRes = R.string.eq_preset_lounge_description,
    ),
    Piano(
        presetId = "piano",
        titleRes = R.string.eq_preset_piano,
        descriptionRes = R.string.eq_preset_piano_description,
    ),
    Pop(
        presetId = "pop",
        titleRes = R.string.eq_preset_pop,
        descriptionRes = R.string.eq_preset_pop_description,
    ),
    RnB(
        presetId = "rnb",
        titleRes = R.string.eq_preset_rnb,
        descriptionRes = R.string.eq_preset_rnb_description,
    ),
    Rock(
        presetId = "rock",
        titleRes = R.string.eq_preset_rock,
        descriptionRes = R.string.eq_preset_rock_description,
    ),
    SmallSpeakers(
        presetId = "small_speakers",
        titleRes = R.string.eq_preset_small_speakers,
        descriptionRes = R.string.eq_preset_small_speakers_description,
    ),
    SpokenWord(
        presetId = "spoken_word",
        titleRes = R.string.eq_preset_spoken_word,
        descriptionRes = R.string.eq_preset_spoken_word_description,
    ),
    TrebleBooster(
        presetId = "treble_booster",
        titleRes = R.string.eq_preset_treble_booster,
        descriptionRes = R.string.eq_preset_treble_booster_description,
    ),
    TrebleReducer(
        presetId = "treble_reducer",
        titleRes = R.string.eq_preset_treble_reducer,
        descriptionRes = R.string.eq_preset_treble_reducer_description,
    ),
    VocalBooster(
        presetId = "vocal_booster",
        titleRes = R.string.eq_preset_vocal_booster,
        descriptionRes = R.string.eq_preset_vocal_booster_description,
    );

    companion object {
        fun of(presetId: String): EqPresetText? = entries.firstOrNull {
            it.presetId == presetId
        }
    }
}

enum class EqualizerPage {
    Main,
    Headphones,
}

sealed interface EqSelection {
    data class Preset(val presetId: String) : EqSelection
    data object Custom : EqSelection
    data class Headphone(val name: String) : EqSelection
    data object None : EqSelection
}

data class EqualizerSheetState(
    val enabled: Boolean = false,
    val tenBand: TenBandEqSettings = TenBandEqSettings(),
    val presets: List<TenBandPreset> = TenBandPresets.all,
    val frequenciesHz: List<Int> = TenBandEqConstants.FREQUENCIES_HZ,
    val minGainDb: Float = TenBandEqConstants.MIN_GAIN_DB,
    val maxGainDb: Float = TenBandEqConstants.MAX_GAIN_DB,
    val page: EqualizerPage = EqualizerPage.Main,
    val query: String = "",
    val results: List<AutoEqEntry> = emptyList(),
    val isSearching: Boolean = false,
    val searchFailed: Boolean = false,
    val loadingProfile: AutoEqEntry? = null,
) : UiState {
    val selection: EqSelection
        get() {
            if (tenBand.engine != EqEngine.BUILT_IN) {
                return EqSelection.None
            }
            val profileName = tenBand.profileName ?: return EqSelection.Custom
            val preset = presets.firstOrNull {
                it.name == profileName
            }
            return if (preset != null) {
                EqSelection.Preset(presetId = preset.id)
            } else {
                EqSelection.Headphone(name = profileName)
            }
        }

    val firstPreset: TenBandPreset?
        get() = presets.firstOrNull()

    val otherPresets: List<TenBandPreset>
        get() = presets.drop(n = 1)

    val canSearch: Boolean
        get() = query.trim().length >= MIN_QUERY_LENGTH

    companion object {
        const val MIN_QUERY_LENGTH = 2
    }
}

sealed interface EqualizerSheetIntent : UiIntent {
    data class ToggleEnabled(val enabled: Boolean) : EqualizerSheetIntent
    data class SelectPreset(val preset: TenBandPreset) : EqualizerSheetIntent
    data object SelectCustom : EqualizerSheetIntent
    data class ChangeGain(val index: Int, val gainDb: Float) : EqualizerSheetIntent
    data object OpenHeadphones : EqualizerSheetIntent
    data object CloseHeadphones : EqualizerSheetIntent
    data class QueryChange(val query: String) : EqualizerSheetIntent
    data object RetrySearch : EqualizerSheetIntent
    data class SelectHeadphone(val entry: AutoEqEntry) : EqualizerSheetIntent
}

sealed interface EqualizerSheetEffect : UiEffect {
    data class ShowMessage(
        @StringRes val resId: Int,
        val arg: String? = null,
    ) : EqualizerSheetEffect
}

package com.looper.base.screen.profile

import androidx.lifecycle.viewModelScope
import com.looper.base.R
import com.looper.base.base.mvi.MviViewModel
import com.looper.base.equalizer.autoeq.AutoEqEntry
import com.looper.base.equalizer.autoeq.AutoEqRepository
import com.looper.base.equalizer.data.EqRepository
import com.looper.base.equalizer.model.EqEngine
import com.looper.base.equalizer.model.EqSettings
import com.looper.base.equalizer.model.TenBandEqSettings
import com.looper.base.equalizer.model.TenBandPreset
import com.looper.base.equalizer.model.applyTo
import com.looper.base.screen.profile.data.EqualizerPage
import com.looper.base.screen.profile.data.EqualizerSheetEffect
import com.looper.base.screen.profile.data.EqualizerSheetIntent
import com.looper.base.screen.profile.data.EqualizerSheetState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class EqualizerSheetViewModel(
    private val eqRepository: EqRepository,
    private val autoEqRepository: AutoEqRepository,
) : MviViewModel<EqualizerSheetState, EqualizerSheetIntent, EqualizerSheetEffect>(
    initialState = EqualizerSheetState(),
) {

    private companion object {
        const val LOCAL_EDIT_GRACE_MS = 1_000L
        const val SEARCH_DEBOUNCE_MS = 300L
        const val GAIN_STEP_DB = 0.5f
    }

    private var eqSettings = EqSettings()
    private var lastLocalEditAt = 0L
    private var searchJob: Job? = null
    private var profileJob: Job? = null

    init {
        viewModelScope.launch {
            eqRepository.settings.collect { settings ->
                eqSettings = settings
                setState {
                    copy(enabled = settings.enabled)
                }
            }
        }
        viewModelScope.launch {
            eqRepository.tenBandSettings.collect { persisted ->
                if (System.currentTimeMillis() - lastLocalEditAt >= LOCAL_EDIT_GRACE_MS) {
                    setState {
                        copy(tenBand = persisted)
                    }
                }
            }
        }
    }

    override fun onIntent(intent: EqualizerSheetIntent) {
        when (intent) {
            is EqualizerSheetIntent.ToggleEnabled -> setEnabled(enabled = intent.enabled)

            is EqualizerSheetIntent.SelectPreset -> selectPreset(preset = intent.preset)

            EqualizerSheetIntent.SelectCustom -> {
                editTenBand {
                    copy(
                        engine = EqEngine.BUILT_IN,
                        profileName = null
                    )
                }
                setEnabled(enabled = true)
            }

            is EqualizerSheetIntent.ChangeGain -> changeGain(index = intent.index, gainDb = intent.gainDb)

            EqualizerSheetIntent.OpenHeadphones -> setState {
                copy(page = EqualizerPage.Headphones)
            }

            EqualizerSheetIntent.CloseHeadphones -> {
                profileJob?.cancel()
                setState {
                    copy(
                        page = EqualizerPage.Main,
                        loadingProfile = null
                    )
                }
            }

            is EqualizerSheetIntent.QueryChange -> {
                setState {
                    copy(query = intent.query)
                }
                search(debounce = true)
            }

            EqualizerSheetIntent.RetrySearch -> search(debounce = false)

            is EqualizerSheetIntent.SelectHeadphone -> loadProfile(entry = intent.entry)
        }
    }

    private fun setEnabled(enabled: Boolean) {
        if (enabled && currentState.tenBand.engine != EqEngine.BUILT_IN) {
            editTenBand {
                copy(engine = EqEngine.BUILT_IN)
            }
        }
        if (eqSettings.enabled == enabled) {
            return
        }
        eqSettings = eqSettings.copy(enabled = enabled)
        setState {
            copy(enabled = enabled)
        }
        eqRepository.apply(settings = eqSettings)
    }

    private fun selectPreset(preset: TenBandPreset) {
        editTenBand {
            preset.applyTo(settings = this)
        }
        setEnabled(enabled = true)
    }

    private fun changeGain(index: Int, gainDb: Float) {
        val stepped = (gainDb / GAIN_STEP_DB).roundToInt() * GAIN_STEP_DB
        editTenBand {
            if (index !in gainsDb.indices || gainsDb[index] == stepped) {
                return@editTenBand this
            }
            copy(
                engine = EqEngine.BUILT_IN,
                gainsDb = gainsDb.toMutableList().apply {
                    set(index, stepped)
                },
                profileName = null,
            )
        }
    }

    private fun search(debounce: Boolean) {
        searchJob?.cancel()
        if (!currentState.canSearch) {
            setState {
                copy(
                    results = emptyList(),
                    isSearching = false,
                    searchFailed = false
                )
            }
            return
        }
        searchJob = viewModelScope.launch {
            if (debounce) {
                delay(timeMillis = SEARCH_DEBOUNCE_MS)
            }
            setState {
                copy(
                    isSearching = true,
                    searchFailed = false
                )
            }
            try {
                val results = autoEqRepository.search(query = currentState.query)
                setState {
                    copy(
                        results = results,
                        isSearching = false
                    )
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                setState {
                    copy(
                        results = emptyList(),
                        isSearching = false,
                        searchFailed = true
                    )
                }
            }
        }
    }

    private fun loadProfile(entry: AutoEqEntry) {
        if (currentState.loadingProfile != null) {
            return
        }
        setState {
            copy(loadingProfile = entry)
        }
        profileJob = viewModelScope.launch {
            try {
                val profile = autoEqRepository.profile(entry = entry)
                editTenBand {
                    profile
                }
                setEnabled(enabled = true)
                setState {
                    copy(
                        loadingProfile = null,
                        page = EqualizerPage.Main
                    )
                }
                sendEffect(
                    effect = EqualizerSheetEffect.ShowMessage(
                        resId = R.string.profile_eq_headphone_applied,
                        arg = entry.name,
                    )
                )
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                setState {
                    copy(loadingProfile = null)
                }
                sendEffect(effect = EqualizerSheetEffect.ShowMessage(resId = R.string.profile_eq_headphone_error))
            }
        }
    }

    private fun editTenBand(reducer: TenBandEqSettings.() -> TenBandEqSettings) {
        val updated = currentState.tenBand.reducer().normalized()
        if (updated == currentState.tenBand) {
            return
        }
        lastLocalEditAt = System.currentTimeMillis()
        setState {
            copy(tenBand = updated)
        }
        eqRepository.applyTenBand(settings = updated)
    }
}

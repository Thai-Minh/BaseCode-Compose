package com.looper.base.screen.language

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.looper.base.base.navigation.child.OpenLanguageFrom
import com.looper.base.base.storage.updateLanguageCode
import com.looper.base.screen.language.data.LanguageEffect
import com.looper.base.screen.language.data.LanguageInitialLoadingStage
import com.looper.base.screen.language.data.LanguageItem
import com.looper.base.screen.language.data.LanguageNativeAdState
import com.looper.base.screen.language.data.LanguageUiState
import com.looper.base.utils.firebase.FRRemoteConfig
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

private const val DEFAULT_COUNTDOWN_MS = 5_000L
private const val SUCCESS_HOLD_DURATION_MS = 1_000L
private const val COUNTDOWN_STEP_MS = 1_000L

class LanguageViewModel : ViewModel() {

    val uiState: StateFlow<LanguageUiState>
        field = MutableStateFlow(LanguageUiState())

    val effects: SharedFlow<LanguageEffect>
        field = MutableSharedFlow(extraBufferCapacity = 1)

    val languageItems: StateFlow<List<LanguageItem>> = uiState
        .map { state ->
            state.rows
        }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = uiState.value.rows,
        )

    val languageSelected: StateFlow<LanguageItem> = uiState
        .map { state ->
            state.rows.find { it.isSelected } ?: LanguageItem(
                type = state.selectedLanguage,
                isSelected = true
            )
        }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = LanguageItem(type = uiState.value.selectedLanguage, isSelected = true)
        )

    val countdownMs: StateFlow<Long> = uiState.map {
        it.countdownMs
    }.distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = uiState.value.countdownMs,
        )

    private var countdownJob: Job? = null
    private var loadingJob: Job? = null

    fun configure(startScreen: OpenLanguageFrom) {
        if (uiState.value.isConfigured) return

        updateState {
            it.copy(
                startScreen = startScreen,
                loadingStage = if (startScreen == OpenLanguageFrom.Home) {
                    LanguageInitialLoadingStage.Complete
                } else {
                    LanguageInitialLoadingStage.Idle
                },
                countdownMs = countdownDurationMs(),
            )
        }
    }

    fun onScreenStarted() {
        updateState {
            it.copy(isScreenStarted = true)
        }

        if (uiState.value.loadingStage == LanguageInitialLoadingStage.Complete) {
            startCountdown()
        }
    }

    fun onScreenStopped() {
        countdownJob?.cancel()
        countdownJob = null

        updateState {
            it.copy(isScreenStarted = false)
        }
    }

    fun startInitialLoading(): Boolean {
        val state = uiState.value

        if (!state.isConfigured || state.isSettingsScreen) return false
        if (state.loadingStage != LanguageInitialLoadingStage.Idle) return false

        updateState {
            it.copy(loadingStage = LanguageInitialLoadingStage.ProgressTo80Percent)
        }

        return true
    }

    fun onInitialProgressTo80Finished() {
        if (uiState.value.loadingStage != LanguageInitialLoadingStage.ProgressTo80Percent) return

        continueInitialLoading()
    }

    fun onLanguageLoadingFinished(adLoaded: Boolean) {
        val state = uiState.value

        val shouldStartLanguageAd = state.languageNativeAdState == LanguageNativeAdState.NotStarted
        val shouldStoreLoadingResult = state.initialAdLoaded == null

        if (!shouldStartLanguageAd && !shouldStoreLoadingResult) return

        updateState {
            it.copy(
                initialAdLoaded = if (shouldStoreLoadingResult) adLoaded else it.initialAdLoaded,
                languageNativeAdState = if (shouldStartLanguageAd) {
                    LanguageNativeAdState.LanguageAdLoading
                } else {
                    it.languageNativeAdState
                },
            )
        }

        if (shouldStoreLoadingResult &&
            uiState.value.loadingStage == LanguageInitialLoadingStage.WaitingForResult
        ) {
            continueInitialLoading()
        }
    }

    fun onLanguageAdPreloadFinished(adLoaded: Boolean) {
        if (uiState.value.languageAdPreloadLoaded != null) return

        updateState {
            it.copy(languageAdPreloadLoaded = adLoaded)
        }

        if (uiState.value.loadingStage == LanguageInitialLoadingStage.WaitingForResult) {
            continueInitialLoading()
        }
    }

    fun onInitialProgressAnimationFinished() {
        if (uiState.value.loadingStage != LanguageInitialLoadingStage.Finishing) return

        updateState {
            it.copy(loadingStage = LanguageInitialLoadingStage.Complete)
        }

        startCountdown()
    }

    fun onLanguageClicked(item: LanguageItem) {
        val state = uiState.value
        if (!state.canSelectLanguage || state.isNavigationRequested) return

        if (state.isSettingsScreen) {
            updateState {
                it.copy(selectedLanguage = item.type)
            }
            return
        }

        countdownJob?.cancel()
        countdownJob = null

        val shouldLoadSelectedAd =
            state.languageNativeAdState == LanguageNativeAdState.LanguageAdFinished

        updateState {
            it.copy(
                selectedLanguage = item.type,
                countdownMs = countdownDurationMs(),
                languageNativeAdState = if (shouldLoadSelectedAd) {
                    LanguageNativeAdState.LanguageSelectedAdLoading
                } else {
                    it.languageNativeAdState
                },
            )
        }

        startCountdown()
    }

    fun onLanguageAdFinished(): Boolean {
        if (uiState.value.languageNativeAdState != LanguageNativeAdState.LanguageAdLoading) {
            return false
        }

        updateState {
            it.copy(languageNativeAdState = LanguageNativeAdState.LanguageAdFinished)
        }
        startCountdown()
        return true
    }

    fun onLanguageSelectedAdFinished(): Boolean {
        if (uiState.value.languageNativeAdState !=
            LanguageNativeAdState.LanguageSelectedAdLoading
        ) {
            return false
        }

        updateState {
            it.copy(languageNativeAdState = LanguageNativeAdState.LanguageSelectedAdFinished)
        }
        if (uiState.value.countdownMs == 0L) {
            requestAutoNext()
        }
        return true
    }

    fun requestManualNavigation(): Boolean {
        val state = uiState.value

        if (state.isNavigationRequested) return false

        if (!state.canNavigate) return false

        countdownJob?.cancel()
        countdownJob = null
        updateState {
            it.copy(isNavigationRequested = true)
        }

        return true
    }

    fun saveLangCodeSelected(): String {
        return uiState.value.selectedLanguage.localize.also { languageCode ->
            updateLanguageCode(value = languageCode)
        }
    }

    private fun continueInitialLoading() {
        val state = uiState.value

        val languageLoadingAdLoaded = state.initialAdLoaded
        val languageAdPreloadLoaded = state.languageAdPreloadLoaded
        if (languageLoadingAdLoaded == null || languageAdPreloadLoaded == null) {
            updateState {
                it.copy(loadingStage = LanguageInitialLoadingStage.WaitingForResult)
            }
            return
        }

        loadingJob?.cancel()

        if (!languageLoadingAdLoaded) {
            updateState {
                it.copy(loadingStage = LanguageInitialLoadingStage.Finishing)
            }
            return
        }

        updateState {
            it.copy(loadingStage = LanguageInitialLoadingStage.SuccessDelay)
        }

        loadingJob = viewModelScope.launch {
            delay(SUCCESS_HOLD_DURATION_MS.milliseconds)
            updateState {
                it.copy(loadingStage = LanguageInitialLoadingStage.Finishing)
            }
        }
    }

    private fun startCountdown() {
        val state = uiState.value

        if (state.isSettingsScreen || !state.isScreenStarted || state.isNavigationRequested) return
        if (!state.canSelectLanguage) return
        if (countdownJob?.isActive == true) return

        countdownJob = viewModelScope.launch {
            while (uiState.value.countdownMs > 0L) {
                val stepMs = minOf(uiState.value.countdownMs, COUNTDOWN_STEP_MS)
                delay(stepMs.milliseconds)
                updateState {
                    it.copy(countdownMs = (it.countdownMs - stepMs).coerceAtLeast(0L))
                }
            }

            countdownJob = null
            requestAutoNext()
        }
    }

    private fun requestAutoNext() {
        val state = uiState.value
        if (!state.isScreenStarted || state.isSettingsScreen || state.isNavigationRequested) return
        if (!state.canNavigate) return

        updateState { it.copy(isNavigationRequested = true) }
        effects.tryEmit(LanguageEffect.AutoNext)
    }

    private fun updateState(transform: (LanguageUiState) -> LanguageUiState) {
        uiState.value = transform(uiState.value)
    }

    private fun countdownDurationMs(): Long {
        return FRRemoteConfig.autoSelectDefaultLanguageMs.takeIf { it > 0L }
            ?: DEFAULT_COUNTDOWN_MS
    }
}

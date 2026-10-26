package com.looper.base.screen.language.data

import com.looper.base.base.navigation.child.OpenLanguageFrom


private const val DEFAULT_COUNTDOWN_MS = 5_000L

data class LanguageUiState(
    val startScreen: OpenLanguageFrom? = null,
    val selectedLanguage: LanguageType = LanguageType.Default,
    val loadingStage: LanguageInitialLoadingStage = LanguageInitialLoadingStage.Idle,
    val initialAdLoaded: Boolean? = null,
    val languageAdPreloadLoaded: Boolean? = null,
    val countdownMs: Long = DEFAULT_COUNTDOWN_MS,
    val languageNativeAdState: LanguageNativeAdState = LanguageNativeAdState.NotStarted,
    val isScreenStarted: Boolean = false,
    val isNavigationRequested: Boolean = false,
) {
    val isConfigured: Boolean
        get() = startScreen != null

    val isSettingsScreen: Boolean
        get() = startScreen == OpenLanguageFrom.Home

    val areLanguageItemsEnabled: Boolean
        get() = isSettingsScreen || loadingStage == LanguageInitialLoadingStage.Complete

    val canSelectLanguage: Boolean
        get() = areLanguageItemsEnabled && (
            isSettingsScreen ||
                languageNativeAdState == LanguageNativeAdState.LanguageAdFinished ||
                languageNativeAdState == LanguageNativeAdState.LanguageSelectedAdLoading ||
                languageNativeAdState == LanguageNativeAdState.LanguageSelectedAdFinished
            )

    val canNavigate: Boolean
        get() = areLanguageItemsEnabled && (
            isSettingsScreen ||
                languageNativeAdState == LanguageNativeAdState.LanguageAdFinished ||
                languageNativeAdState == LanguageNativeAdState.LanguageSelectedAdFinished
            )

    val rows: List<LanguageItem>
        get() = languageDefaultItems().map { item ->
            item.copy(
                isEnable = areLanguageItemsEnabled,
                isSelected = item.type == selectedLanguage,
            )
        }
}

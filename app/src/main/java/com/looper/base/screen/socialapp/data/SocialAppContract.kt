package com.looper.base.screen.socialapp.data

import com.looper.base.base.mvi.UiEffect
import com.looper.base.base.mvi.UiIntent
import com.looper.base.base.mvi.UiState
import com.looper.base.screen.home.data.SocialApp

data class SocialAppUiState(
    val apps: List<SocialApp> = SocialApp.entries,
) : UiState

sealed interface SocialAppIntent : UiIntent {
    data object BackClick : SocialAppIntent
    data class AppClick(val app: SocialApp) : SocialAppIntent
}

sealed interface SocialAppEffect : UiEffect {
    data object NavigateBack : SocialAppEffect
}

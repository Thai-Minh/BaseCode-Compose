package com.looper.base.screen.socialapp

import com.looper.base.base.mvi.MviViewModel
import com.looper.base.screen.home.data.SocialAppLauncher
import com.looper.base.screen.socialapp.data.SocialAppEffect
import com.looper.base.screen.socialapp.data.SocialAppIntent
import com.looper.base.screen.socialapp.data.SocialAppUiState

class SocialAppViewModel(
    private val socialAppLauncher: SocialAppLauncher,
) : MviViewModel<SocialAppUiState, SocialAppIntent, SocialAppEffect>(
    initialState = SocialAppUiState(),
) {

    override fun onIntent(intent: SocialAppIntent) {
        when (intent) {
            SocialAppIntent.BackClick -> sendEffect(effect = SocialAppEffect.NavigateBack)

            is SocialAppIntent.AppClick -> socialAppLauncher.open(app = intent.app)
        }
    }
}

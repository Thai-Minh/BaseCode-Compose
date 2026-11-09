package com.looper.base.screen.main

import com.looper.base.base.mvi.MviViewModel
import com.looper.base.screen.main.data.MainEffect
import com.looper.base.screen.main.data.MainIntent
import com.looper.base.screen.main.data.MainUiState

class MainViewModel : MviViewModel<MainUiState, MainIntent, MainEffect>(
    initialState = MainUiState(),
) {

    override fun onIntent(intent: MainIntent) {
        when (intent) {
            is MainIntent.SelectTab -> setState {
                copy(selectedTab = intent.tab)
            }
        }
    }
}

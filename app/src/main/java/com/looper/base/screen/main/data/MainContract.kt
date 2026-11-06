package com.looper.base.screen.main.data

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.looper.base.R
import com.looper.base.base.mvi.UiEffect
import com.looper.base.base.mvi.UiIntent
import com.looper.base.base.mvi.UiState

enum class MainTab(
    @DrawableRes val iconRes: Int,
    @StringRes val labelRes: Int,
) {
    Home(iconRes = R.drawable.ic_main_tab_home, labelRes = R.string.main_tab_home),
    Library(iconRes = R.drawable.ic_main_tab_library, labelRes = R.string.main_tab_library),
    History(iconRes = R.drawable.ic_main_tab_history, labelRes = R.string.main_tab_history),
    Search(iconRes = R.drawable.ic_main_tab_search, labelRes = R.string.main_tab_search),
    Profile(iconRes = R.drawable.ic_main_tab_profile, labelRes = R.string.main_tab_profile),
}

data class MainUiState(
    val tabs: List<MainTab> = MainTab.entries,
    val selectedTab: MainTab = MainTab.Home,
) : UiState

sealed interface MainIntent : UiIntent {
    data class SelectTab(val tab: MainTab) : MainIntent
}

sealed interface MainEffect : UiEffect

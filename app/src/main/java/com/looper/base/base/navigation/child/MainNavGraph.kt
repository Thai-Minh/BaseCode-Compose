package com.looper.base.base.navigation.child

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.looper.base.base.navigation.MainInput
import com.looper.base.screen.main.MainScreen

fun NavGraphBuilder.mainNavGraph() {
    composable<MainInput> {
        MainScreen()
    }
}

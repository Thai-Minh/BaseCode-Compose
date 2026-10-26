package com.looper.base.base.navigation.child

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.looper.base.base.navigation.SleepTimerInput
import com.looper.base.player.sleeptimer.ui.SleepTimerScreen

fun NavGraphBuilder.sleepTimerNavGraph(navController: NavHostController) {
    composable<SleepTimerInput> {
        SleepTimerScreen(
            onBack = { navController.popBackStack() }
        )
    }
}

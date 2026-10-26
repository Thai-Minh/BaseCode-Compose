package com.looper.base.base.navigation.child

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.looper.base.base.navigation.EqualizerInput
import com.looper.base.equalizer.ui.EqualizerScreen

fun NavGraphBuilder.equalizerNavGraph(navController: NavHostController) {
    composable<EqualizerInput> {
        EqualizerScreen(
            onBack = { navController.popBackStack() }
        )
    }
}

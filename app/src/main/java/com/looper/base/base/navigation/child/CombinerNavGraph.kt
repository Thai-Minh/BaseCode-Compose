package com.looper.base.base.navigation.child

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.looper.base.base.navigation.CombinerInput
import com.looper.base.screen.combiner.CombinerScreen

fun NavGraphBuilder.combinerNavGraph(navController: NavHostController) {
    composable<CombinerInput> {
        CombinerScreen(
            onBack = { navController.popBackStack() }
        )
    }
}

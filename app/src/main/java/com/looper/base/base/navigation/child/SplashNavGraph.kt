package com.looper.base.base.navigation.child

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.looper.base.base.navigation.CombinerInput
import com.looper.base.base.navigation.DualCameraInput
import com.looper.base.base.navigation.ReactionInput
import com.looper.base.base.navigation.SplashInput
import com.looper.base.base.navigation.navigateSingle
import com.looper.base.screen.splash.SplashScreen

fun NavGraphBuilder.splashNavGraph(navController: NavHostController) {
    composable<SplashInput> {
        SplashScreen(
            onOpenDualCamera = {
                navController.navigateSingle<DualCameraInput>(route = DualCameraInput)
            },
            onOpenReaction = {
                navController.navigateSingle<ReactionInput>(route = ReactionInput)
            },
            onOpenCombiner = {
                navController.navigateSingle<CombinerInput>(route = CombinerInput)
            },
        )
    }
}

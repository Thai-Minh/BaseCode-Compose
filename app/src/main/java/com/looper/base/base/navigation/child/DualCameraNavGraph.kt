package com.looper.base.base.navigation.child

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.looper.base.base.navigation.DualCameraInput
import com.looper.base.screen.dualcamera.DualCameraScreen

fun NavGraphBuilder.dualCameraNavGraph(navController: NavHostController) {
    composable<DualCameraInput> {
        DualCameraScreen(
            onClose = { navController.popBackStack() }
        )
    }
}

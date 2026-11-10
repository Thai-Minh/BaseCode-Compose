package com.looper.base.base.navigation.child

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.looper.base.base.navigation.AudioPreviewInput
import com.looper.base.base.navigation.popBackStackSingle
import com.looper.base.screen.audiopreview.AudioPreviewScreen

fun NavGraphBuilder.audioPreviewNavGraph(navController: NavHostController) {
    composable<AudioPreviewInput> { entry ->
        val input = entry.toRoute<AudioPreviewInput>()
        AudioPreviewScreen(
            sourceUrl = input.sourceUrl,
            onBack = {
                navController.popBackStackSingle<AudioPreviewInput>(currentRoute = input)
            },
        )
    }
}

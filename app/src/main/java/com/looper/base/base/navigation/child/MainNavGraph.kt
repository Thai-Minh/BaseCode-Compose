package com.looper.base.base.navigation.child

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.looper.base.base.navigation.AboutInput
import com.looper.base.base.navigation.AudioPreviewInput
import com.looper.base.base.navigation.MainInput
import com.looper.base.base.navigation.SocialAppInput
import com.looper.base.base.navigation.navigateSingle
import com.looper.base.screen.main.MainScreen

fun NavGraphBuilder.mainNavGraph(navController: NavHostController) {
    composable<MainInput> {
        MainScreen(
            onNavigateSocialApps = {
                navController.navigateSingle<SocialAppInput>(route = SocialAppInput)
            },
            onNavigateAbout = {
                navController.navigateSingle<AboutInput>(route = AboutInput)
            },
            onNavigateAudioPreview = { sourceUrl ->
                navController.navigateSingle<AudioPreviewInput>(
                    route = AudioPreviewInput(sourceUrl = sourceUrl),
                )
            },
        )
    }
}

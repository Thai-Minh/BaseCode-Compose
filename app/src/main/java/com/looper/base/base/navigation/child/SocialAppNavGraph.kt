package com.looper.base.base.navigation.child

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.looper.base.base.navigation.SocialAppInput
import com.looper.base.base.navigation.popBackStackSingle
import com.looper.base.screen.socialapp.SocialAppScreen

fun NavGraphBuilder.socialAppNavGraph(navController: NavHostController) {
    composable<SocialAppInput> {
        SocialAppScreen(
            onBack = {
                navController.popBackStackSingle<SocialAppInput>(currentRoute = SocialAppInput)
            },
        )
    }
}

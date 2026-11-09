package com.looper.base.base.navigation.child

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.looper.base.base.navigation.AboutInput
import com.looper.base.base.navigation.LanguageInput
import com.looper.base.base.navigation.navigateSingle
import com.looper.base.base.navigation.popBackStackSingle
import com.looper.base.screen.about.AboutScreen

fun NavGraphBuilder.aboutNavGraph(navController: NavHostController) {
    composable<AboutInput> {
        AboutScreen(
            onBack = {
                navController.popBackStackSingle<AboutInput>(currentRoute = AboutInput)
            },
            onNavigateLanguage = {
                navController.navigateSingle<LanguageInput>(
                    route = LanguageInput(openFrom = OpenLanguageFrom.Home.name)
                )
            },
        )
    }
}

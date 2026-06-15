package com.looper.base.base.navigation.child

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.looper.base.base.navigation.LanguageInput
import com.looper.base.screen.language.LanguageScreen

enum class OpenLanguageFrom {
    Splash,
    Home
}

fun NavGraphBuilder.languageNavGraph(navController: NavHostController) {
    composable<LanguageInput> { backStackEntry ->

        val input: LanguageInput = backStackEntry.toRoute()

        val openFrom = OpenLanguageFrom.valueOf(input.openFrom)

        LanguageScreen(
            from = openFrom
        )
    }
}
package com.looper.base.base.navigation.child

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.looper.base.base.navigation.MainInput
import com.looper.base.base.navigation.SplashInput
import com.looper.base.base.navigation.navigateSingle
import com.looper.base.screen.splash.SplashScreen

fun NavGraphBuilder.splashNavGraph(navController: NavHostController) {
    composable<SplashInput> {
        SplashScreen {
            navController.navigateSingle<MainInput>(route = MainInput) {
                popUpTo<SplashInput> {
                    inclusive = true
                }
                launchSingleTop = true
            }
        }
    }
}
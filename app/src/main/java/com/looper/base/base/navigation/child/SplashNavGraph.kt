package com.looper.base.base.navigation.child

import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.looper.base.base.navigation.LanguageInput
import com.looper.base.base.navigation.MainInput
import com.looper.base.base.navigation.SplashInput
import com.looper.base.base.navigation.navigateSingle
import com.looper.base.base.navigation.safeNavigate
import com.looper.base.screen.splash.SplashScreen
import com.looper.base.utils.getActivity

fun NavGraphBuilder.splashNavGraph(navController: NavHostController) {
    composable<SplashInput> {
        val activity = LocalContext.current.getActivity()

        SplashScreen {
            navController.safeNavigate<MainInput>(
                route = MainInput,
                activity = activity
            )
        }
    }
}
package com.looper.base.base.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import com.looper.base.base.navigation.child.languageNavGraph
import com.looper.base.base.navigation.child.splashNavGraph

@Composable
fun MyAppNavGraph(
    navController: NavHostController = rememberNavController(),
) {
    AppNavHost(
        navController = navController,
        startDestination = SplashInput,
        builder = {
            splashNavGraph(navController = navController)

            languageNavGraph(navController = navController)
        }
    )
}

@Composable
fun AppNavHost(
    navController: NavHostController, startDestination: Any, builder: NavGraphBuilder.() -> Unit,
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = {
            slideIntoContainer(
                AnimatedContentTransitionScope.SlideDirection.Left, animationSpec = tween(350)
            )
        },
        exitTransition = {
            slideOutOfContainer(
                AnimatedContentTransitionScope.SlideDirection.Left, animationSpec = tween(350)
            )
        },
        popEnterTransition = {
            slideIntoContainer(
                AnimatedContentTransitionScope.SlideDirection.Right, animationSpec = tween(350)
            )
        },
        popExitTransition = {
            slideOutOfContainer(
                AnimatedContentTransitionScope.SlideDirection.Right, animationSpec = tween(350)
            )
        },
        builder = builder
    )
}
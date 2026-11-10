package com.looper.base.base.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import com.looper.base.base.navigation.child.aboutNavGraph
import com.looper.base.base.navigation.child.audioPreviewNavGraph
import com.looper.base.base.navigation.child.equalizerNavGraph
import com.looper.base.base.navigation.child.languageNavGraph
import com.looper.base.base.navigation.child.mainNavGraph
import com.looper.base.base.navigation.child.sleepTimerNavGraph
import com.looper.base.base.navigation.child.socialAppNavGraph
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

            mainNavGraph(navController = navController)

            equalizerNavGraph(navController = navController)

            sleepTimerNavGraph(navController = navController)

            socialAppNavGraph(navController = navController)

            aboutNavGraph(navController = navController)

            audioPreviewNavGraph(navController = navController)
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
                towards = AnimatedContentTransitionScope.SlideDirection.Left, animationSpec = tween(
                    durationMillis = 350
                )
            )
        },
        exitTransition = {
            slideOutOfContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Left, animationSpec = tween(
                    durationMillis = 350
                )
            )
        },
        popEnterTransition = {
            slideIntoContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Right,
                animationSpec = tween(
                    durationMillis = 350
                )
            )
        },
        popExitTransition = {
            slideOutOfContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Right,
                animationSpec = tween(
                    durationMillis = 350
                )
            )
        },
        builder = builder
    )
}
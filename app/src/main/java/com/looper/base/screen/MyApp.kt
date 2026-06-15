package com.looper.base.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.looper.ads.AdsManager
import com.looper.base.base.navigation.LanguageInput
import com.looper.base.base.navigation.MyAppNavGraph
import com.looper.base.base.navigation.OnBoardingInput
import com.looper.base.base.navigation.SplashInput
import com.looper.base.base.ui.LocaleWrapper
import com.looper.base.screen.dialog.DialogScreen
import com.looper.base.screen.dialog.DialogState
import com.looper.base.screen.dialog.LocalDialogState
import com.looper.base.utils.NetworkManager
import org.koin.compose.koinInject

@Composable
fun MyApp(
    navController: NavHostController
) {
    AppStateProvider {
        DialogScreen()

        MyAppNavGraph(navController = navController)
        NetworkHandler(navController = navController)
    }
}

@Composable
fun AppStateProvider(
    content: @Composable () -> Unit,
) {
    val isPreview = LocalInspectionMode.current

    val dialogState = remember {
        DialogState()
    }

    CompositionLocalProvider(
        LocalDialogState provides dialogState,
    ) {
        LocaleWrapper {
            if (isPreview) {
                content()
            } else {
                content()
            }
        }
    }
}

@Composable
private fun NetworkHandler(
    navController: NavHostController
) {
    val dialogState = LocalDialogState.current
    val networkManager: NetworkManager = koinInject()
    val isConnected by networkManager.isConnected.collectAsStateWithLifecycle(true)

    val currentBackStackEntry by navController.currentBackStackEntryAsState()

    val isSplash = currentBackStackEntry?.destination?.hasRoute<SplashInput>() ?: false
    val isNotSplash = !isSplash

    LaunchedEffect(key1 = isConnected, key2 = currentBackStackEntry) {
        val destination = currentBackStackEntry?.destination ?: return@LaunchedEffect

        handleShowWelcomeBack(destination = destination)

        if (!isConnected && isNotSplash) {
            dialogState.showNoInternetDialog()
        } else if (isConnected) {
            dialogState.dismissNoInternetDialog(false)
        }
    }
}

private fun handleShowWelcomeBack(destination: NavDestination) {
    val isIgnoreRoute = destination.hasRoute<SplashInput>() ||
            destination.hasRoute<OnBoardingInput>() ||
            destination.hasRoute<LanguageInput>()

    AdsManager.handleTemporarilyAppOpenResumed(isEnable = !isIgnoreRoute)
}
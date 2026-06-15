package com.looper.base.base.navigation

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.livedata.observeAsState
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavOptionsBuilder
import androidx.navigation.navOptions
import com.looper.ads.helper.interstitial.InterstitialType
import com.looper.ads.helper.interstitial.showInterstitialAd

inline fun <reified T : Any> NavController.safeNavigate(
    activity: ComponentActivity,
    route: T,
    showLoadingDialog: Boolean = true,
    ignoreShowAds: Boolean = false
) {
    if (!isCurrentBackStack<T>()) {
        try {
            activity.withInterstitialAds(
                ignoreShowAds = ignoreShowAds,
                showLoadingDialog = showLoadingDialog
            ) {
                navigate(route)
            }
        } catch (_: Exception) {}
    }
}

// T: là current Route mình truyền vào
inline fun <reified T : Any> NavController.popBackStackSingle(
    activity: ComponentActivity,
    showLoadingDialog: Boolean = true,
    ignoreShowAds: Boolean = false
) {
    if (isCurrentBackStack<T>()) {
        try {
            activity.withInterstitialAds(
                ignoreShowAds = ignoreShowAds,
                showLoadingDialog = showLoadingDialog
            ) {
                popBackStack()
            }
        } catch (_: Exception) {}
    }
}

inline fun <reified T : Any> NavController.safeNavigate(
    activity: ComponentActivity,
    route: T,
    showLoadingDialog: Boolean = true,
    type: InterstitialType = InterstitialType.InApp,
    ignoreShowAds: Boolean = false,
    noinline builder: NavOptionsBuilder.() -> Unit
) {
    if (!isCurrentBackStack<T>()) {
        try {
            activity.withInterstitialAds(
                ignoreShowAds = ignoreShowAds,
                type = type,
                showLoadingDialog = showLoadingDialog
            ) {
                navigate(route, navOptions(builder))
            }
        } catch (_: Exception) {}
    }
}

inline fun <reified T : Any> NavController.navigateFromSplash(
    activity: ComponentActivity,
    route: T,
    ignoreShowAds: Boolean = false,
    noinline builder: NavOptionsBuilder.() -> Unit
) {
    safeNavigate(
        activity = activity,
        route = route,
        type = InterstitialType.Splash,
        showLoadingDialog = false,
        ignoreShowAds = ignoreShowAds,
        builder = builder
    )
}

fun NavController.navigateActivity(
    activity: ComponentActivity,
    navigate: () -> Unit = {}
) {
    try {
        activity.withInterstitialAds(
            ignoreShowAds = false,
            type = InterstitialType.Splash,
            showLoadingDialog = false
        ) {
            navigate()
        }
    } catch (_: Exception) {

    }
}

inline fun <reified T : Any> NavController.navigateSingle(route: Any) {
    if (!isCurrentBackStack<T>())
        try {
            navigate(route)
        } catch (_: Exception) {

        }
}

inline fun <reified T : Any> NavController.navigateSingle(
    route: Any,
    noinline builder: NavOptionsBuilder.() -> Unit
) {
    if (!isCurrentBackStack<T>()) {
        navigate(route, navOptions(builder))
    }
}

inline fun <reified T : Any> NavController.isCurrentBackStack(): Boolean {
    return currentBackStackEntry?.destination?.hasRoute<T>() == true
}

inline fun <reified T : Any> NavController.popBackStackSingle(currentRoute: Any) {
    if (isCurrentBackStack<T>())
        try {
            popBackStack()
        } catch (e: Exception) {
            e.printStackTrace()
        }
}

@Composable
fun <T> NavController.ObserveResult(
    resultKey: String = "result_key",
    onResult: (T) -> Unit
) {
    val result = currentBackStackEntry
        ?.savedStateHandle
        ?.getLiveData<T>(resultKey)
        ?.observeAsState()

    var value = result?.value

    LaunchedEffect(value) {
        if (value != null) {
            onResult(value)
            currentBackStackEntry?.savedStateHandle?.remove<T>(resultKey)
        }
    }
}

fun <T : Any> NavController.sendBackResult(result: T, resultKey: String = "result_key") {
    this.previousBackStackEntry
        ?.savedStateHandle
        ?.set(resultKey, result)

    this.popBackStack()
}

fun ComponentActivity.withInterstitialAds(
    type: InterstitialType = InterstitialType.InApp,
    showLoadingDialog: Boolean = true,
    ignoreShowAds: Boolean,
    callback: (isShown: Boolean) -> Unit
) {
    if (ignoreShowAds) {
        callback(false)
        return
    }

    showInterstitialAd(
        activity = this,
        type = type,
        showLoadingDialog = showLoadingDialog,
        onLoadAdCallback = {
            if (it) return@showInterstitialAd

            callback(false)
        },
        onShowAdCallback = {
            callback(it)
        }
    )
}
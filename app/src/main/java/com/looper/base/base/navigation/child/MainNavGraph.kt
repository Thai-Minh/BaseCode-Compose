package com.looper.base.base.navigation.child

import androidx.activity.compose.LocalActivity
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.looper.base.base.navigation.DownloadInput
import com.looper.base.base.navigation.MainInput
import com.looper.base.base.navigation.safeNavigate
import com.looper.base.screen.main.MainScreen
import com.looper.base.utils.getActivity

fun NavGraphBuilder.mainNavGraph(navController: NavHostController) {
    composable<MainInput> {
        val activity = LocalContext.current.getActivity()

        MainScreen(
            navigateToDownload = {
                navController.safeNavigate<DownloadInput>(
                    activity = activity,
                    route = DownloadInput
                )
            }
        )
    }
}

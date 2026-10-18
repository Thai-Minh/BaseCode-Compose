package com.looper.base.base.navigation.child

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.looper.base.base.navigation.DownloadInput
import com.looper.base.screen.download_test.DownloadTestScreen

fun NavGraphBuilder.downloadNavGraph(navController: NavHostController) {
    composable<DownloadInput> {

        DownloadTestScreen()
    }
}

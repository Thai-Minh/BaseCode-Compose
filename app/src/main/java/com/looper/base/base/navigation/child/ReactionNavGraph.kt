package com.looper.base.base.navigation.child

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.looper.base.base.navigation.ReactionInput
import com.looper.base.screen.reaction.ReactionScreen

fun NavGraphBuilder.reactionNavGraph(navController: NavHostController) {
    composable<ReactionInput> {
        ReactionScreen(
            onBack = { navController.popBackStack() }
        )
    }
}

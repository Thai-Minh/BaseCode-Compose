package com.looper.base.screen.splash

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(navigate: () -> Unit) {

    LaunchedEffect(key1 = Unit) {
        delay(2000)
        navigate()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Yellow)
    ) {

    }
}
package com.looper.base.screen.splash

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.looper.base.R

@Composable
fun SplashScreen(
    onOpenDualCamera: () -> Unit,
    onOpenReaction: () -> Unit,
    onOpenCombiner: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Yellow)
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SplashButton(text = stringResource(R.string.splash_open_dual_camera), onClick = onOpenDualCamera)
        SplashButton(text = stringResource(R.string.splash_open_reaction), onClick = onOpenReaction)
        SplashButton(text = stringResource(R.string.splash_open_combiner), onClick = onOpenCombiner)
    }
}

@Composable
private fun SplashButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Black,
            contentColor = Color.White,
        ),
    ) {
        Text(text = text)
    }
}

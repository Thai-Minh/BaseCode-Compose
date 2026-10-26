package com.looper.base.player.ui

import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_SURFACE_VIEW
import com.looper.base.player.PlayerConnection
import org.koin.compose.koinInject

@OptIn(UnstableApi::class)
@Composable
fun PlayerVideoSurface(
    modifier: Modifier = Modifier,
    connection: PlayerConnection = koinInject(),
) {
    val controller by connection.controller.collectAsStateWithLifecycle()
    PlayerSurface(
        player = controller,
        modifier = modifier,
        surfaceType = SURFACE_TYPE_SURFACE_VIEW,
    )
}

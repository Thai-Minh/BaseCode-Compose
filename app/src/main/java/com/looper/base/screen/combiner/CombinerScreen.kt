package com.looper.base.screen.combiner

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import com.looper.base.R
import com.looper.base.screen.combiner.data.CombinerEffect
import com.looper.base.screen.combiner.data.CombinerLayout
import com.looper.base.screen.combiner.data.CombinerUiState
import com.looper.base.screen.common.data.DualAudioMode
import com.looper.base.screen.common.data.DualLayout
import org.koin.compose.viewmodel.koinViewModel

/**
 * Same layout and controls as the reaction screen, but the corner window shows a second video
 * picked from the device instead of the camera.
 */
@Composable
fun CombinerScreen(
    onBack: () -> Unit,
    viewModel: CombinerViewModel = koinViewModel(),
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val mainPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> viewModel.onMainPicked(uri) }
    val overlayPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> viewModel.onOverlayPicked(uri) }
    val videoOnly = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
    val pickMain = { mainPicker.launch(videoOnly) }
    val pickOverlay = { overlayPicker.launch(videoOnly) }

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            val message = when (effect) {
                is CombinerEffect.VideoSaved -> R.string.combiner_saved
                is CombinerEffect.Error -> effect.messageRes
            }
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.onStop() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ScreenBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
        ) {
            TopBar(
                state = uiState,
                onBack = onBack,
                onChangeMain = pickMain,
                onChangeOverlay = pickOverlay,
                onSwap = viewModel::onSwapClick,
            )
            CombinerStage(
                state = uiState,
                mainPlayer = viewModel.mainPlayer,
                overlayPlayer = viewModel.overlayPlayer,
                onPickMain = pickMain,
                onPickOverlay = pickOverlay,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            )
            if (uiState.isReady) {
                CombinerControls(
                    state = uiState,
                    onArrangementSelected = viewModel::onArrangementSelected,
                    onAudioModeSelected = viewModel::onAudioModeSelected,
                    onPreviewClick = viewModel::onPreviewClick,
                    onCombineClick = viewModel::onCombineClick,
                )
            }
        }

        if (uiState.isExporting) {
            ExportOverlay(progress = uiState.exportProgress)
        }
    }
}

@Composable
private fun TopBar(
    state: CombinerUiState,
    onBack: () -> Unit,
    onChangeMain: () -> Unit,
    onChangeOverlay: () -> Unit,
    onSwap: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CircleIconButton(
            iconRes = R.drawable.ic_reaction_back,
            label = stringResource(R.string.combiner_back),
            onClick = onBack,
        )
        Spacer(modifier = Modifier.weight(1f))
        if (state.mainVideo != null) {
            val enabled = state.canEdit
            TextButton(onClick = onChangeMain, enabled = enabled) {
                Text(
                    text = stringResource(R.string.combiner_change_main),
                    color = if (enabled) Color.White else Color.White.copy(alpha = 0.4f),
                )
            }
        }
        if (state.overlayVideo != null) {
            CircleIconButton(
                iconRes = R.drawable.ic_reaction_video,
                label = stringResource(R.string.combiner_change_overlay),
                onClick = onChangeOverlay,
                enabled = state.canEdit,
            )
        }
        if (state.isReady) {
            CircleIconButton(
                iconRes = R.drawable.ic_dual_camera_swap,
                label = stringResource(R.string.combiner_swap_videos),
                onClick = onSwap,
                enabled = state.canSwap,
            )
        }
    }
}

/**
 * The canvas: in PiP layouts the first video's aspect ratio, that video filling it and the other
 * in a corner window 1/3 of its width at its own aspect ratio; in split layouts a fixed frame
 * with one video per half. Uses [CombinerLayout] so the exported video has the same geometry.
 *
 * Both slots stay at fixed positions in the composition and only their modifiers change on swap
 * or layout change, so the players' TextureViews are never recreated.
 */
@Composable
private fun CombinerStage(
    state: CombinerUiState,
    mainPlayer: Player,
    overlayPlayer: Player,
    onPickMain: () -> Unit,
    onPickOverlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val mainVideo = state.mainVideo
    val overlayVideo = state.overlayVideo
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        val stageModifier = state.canvasAspectRatio
            ?.let { Modifier.aspectRatio(it) }
            ?: Modifier.fillMaxSize()
        // Empty bands next to the narrower / shorter video are black, as in the exported video.
        val stageBackground = if (!state.arrangement.isPip) {
            Modifier.background(Color.Black)
        } else {
            Modifier
        }
        BoxWithConstraints(modifier = stageModifier.then(stageBackground)) {
            val (firstRect, secondRect) = CombinerLayout.slots(
                canvasWidth = constraints.maxWidth.toFloat(),
                canvasHeight = constraints.maxHeight.toFloat(),
                arrangement = state.arrangement,
                firstAspectRatio = state.fullAspectRatio,
                secondAspectRatio = state.windowAspectRatio,
            )
            val density = LocalDensity.current
            val fullSlot = Modifier.placedAt(firstRect, density).zIndex(0f)
            val windowSlot = Modifier.placedAt(secondRect, density).zIndex(1f)

            // Main video slot.
            Box(
                modifier = (if (state.overlayFullScreen) windowSlot else fullSlot).clipToBounds(),
                contentAlignment = Alignment.Center,
            ) {
                if (mainVideo != null) {
                    PlayerTexture(
                        player = mainPlayer,
                        videoAspectRatio = mainVideo.aspectRatio,
                        onClickLabel = stringResource(R.string.combiner_change_main),
                        enabled = state.canEdit,
                        onClick = onPickMain,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    MainPlaceholder(onClick = onPickMain)
                }
            }

            // Overlay video slot.
            Box(
                modifier = (if (state.overlayFullScreen) fullSlot else windowSlot)
                    .clip(if (overlayVideo != null) RectangleShape else RoundedCornerShape(12.dp))
                    .background(PlaceholderColor),
                contentAlignment = Alignment.Center,
            ) {
                if (overlayVideo != null) {
                    PlayerTexture(
                        player = overlayPlayer,
                        videoAspectRatio = overlayVideo.aspectRatio,
                        onClickLabel = stringResource(R.string.combiner_change_overlay),
                        enabled = state.canEdit,
                        onClick = onPickOverlay,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    OverlayPlaceholder(enabled = state.canEdit, onClick = onPickOverlay)
                }
            }
        }
    }
}

@Composable
private fun CombinerControls(
    state: CombinerUiState,
    onArrangementSelected: (DualLayout) -> Unit,
    onAudioModeSelected: (DualAudioMode) -> Unit,
    onPreviewClick: () -> Unit,
    onCombineClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ArrangementSelector(
            selected = state.arrangement,
            enabled = state.canEdit,
            onSelect = onArrangementSelected,
        )
        AudioModeSelector(
            selected = state.audioMode,
            enabled = state.canEdit,
            onSelect = onAudioModeSelected,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PreviewButton(
                isPlaying = state.isPreviewPlaying,
                enabled = state.canEdit,
                onClick = onPreviewClick,
            )
            CombineButton(enabled = state.canEdit, onClick = onCombineClick)
        }
    }
}

@Composable
private fun PreviewButton(isPlaying: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val label = stringResource(
        if (isPlaying) R.string.combiner_pause_preview else R.string.combiner_play_preview
    )
    val color = Color.White.copy(alpha = if (enabled) 1f else 0.4f)
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(CircleShape)
            .border(3.dp, color, CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(
                if (isPlaying) R.drawable.ic_combiner_pause else R.drawable.ic_combiner_play
            ),
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(28.dp),
        )
    }
}

@Composable
private fun CombineButton(enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .width(180.dp)
            .height(56.dp)
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = if (enabled) 1f else 0.4f))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.combiner_combine),
            color = Color.Black,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

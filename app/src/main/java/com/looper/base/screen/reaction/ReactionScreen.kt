package com.looper.base.screen.reaction

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.compose.CameraXViewfinder
import androidx.camera.core.SurfaceRequest
import androidx.camera.viewfinder.core.ImplementationMode
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import com.looper.base.R
import com.looper.base.screen.common.data.DualAudioMode
import com.looper.base.screen.common.data.DualLayout
import com.looper.base.screen.reaction.data.ReactionEffect
import com.looper.base.screen.reaction.data.ReactionLayout
import com.looper.base.screen.reaction.data.ReactionUiState
import org.koin.compose.viewmodel.koinViewModel
import java.util.Locale

private val RecordRed = Color(0xFFE53935)

@Composable
fun ReactionScreen(
    onBack: () -> Unit,
    viewModel: ReactionViewModel = koinViewModel(),
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val surfaceRequest by viewModel.surfaceRequest.collectAsStateWithLifecycle()

    val videoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> viewModel.onBackgroundPicked(uri) }
    val pickBackground = {
        videoPicker.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        if (context.isGranted(Manifest.permission.CAMERA)) {
            viewModel.onCameraEnabled()
        } else {
            Toast.makeText(
                context,
                R.string.reaction_camera_permission_denied,
                Toast.LENGTH_SHORT,
            ).show()
        }
    }
    val openReactionCamera = {
        val needed = listOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
            .filterNot(context::isGranted)
        if (needed.isEmpty()) {
            viewModel.onCameraEnabled()
        } else {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            val message = when (effect) {
                is ReactionEffect.VideoSaved -> R.string.reaction_saved
                is ReactionEffect.Error -> effect.messageRes
            }
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    // Recording can't continue once the camera lifecycle stops.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.onStop() }

    if (uiState.isCameraOn) {
        val lifecycleOwner = LocalLifecycleOwner.current
        LaunchedEffect(lifecycleOwner, uiState.useFrontCamera) {
            viewModel.bindCamera(lifecycleOwner, uiState.useFrontCamera)
        }
    }

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
                onChangeBackground = pickBackground,
                onSwitchCamera = viewModel::onSwitchCamera,
                onSwap = viewModel::onSwapClick,
            )
            ReactionStage(
                state = uiState,
                player = viewModel.player,
                surfaceRequest = surfaceRequest,
                onPickBackground = pickBackground,
                onPickReaction = openReactionCamera,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .onSizeChanged { viewModel.onStageSizeChanged(it.width, it.height) },
            )
            if (uiState.isReadyToRecord) {
                ReactionControls(
                    state = uiState,
                    onArrangementSelected = viewModel::onArrangementSelected,
                    onAudioModeSelected = viewModel::onAudioModeSelected,
                    onRecordClick = viewModel::onRecordClick,
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
    state: ReactionUiState,
    onBack: () -> Unit,
    onChangeBackground: () -> Unit,
    onSwitchCamera: () -> Unit,
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
            label = stringResource(R.string.reaction_back),
            onClick = onBack,
        )
        Spacer(modifier = Modifier.weight(1f))
        if (state.background != null) {
            val enabled = state.canEdit
            TextButton(onClick = onChangeBackground, enabled = enabled) {
                Text(
                    text = stringResource(R.string.reaction_change_background),
                    color = if (enabled) Color.White else Color.White.copy(alpha = 0.4f),
                )
            }
        }
        if (state.isCameraOn) {
            CircleIconButton(
                iconRes = R.drawable.ic_reaction_switch_camera,
                label = stringResource(R.string.reaction_switch_camera),
                onClick = onSwitchCamera,
                enabled = state.canSwitchCamera,
            )
        }
        if (state.isReadyToRecord) {
            CircleIconButton(
                iconRes = R.drawable.ic_dual_camera_swap,
                label = stringResource(R.string.reaction_swap_videos),
                onClick = onSwap,
                enabled = state.canSwap,
            )
        }
    }
}

/**
 * The canvas: in PiP layouts the whole stage, the full-frame camera filling it or the full-frame
 * video fitted at its original aspect ratio (black bands), and the other one in a corner window
 * inside it (camera: 9:16 at 1/3 of its width, or 3:4 at 1/2 of its height when it is
 * landscape; video: 1/3 of its width); in split layouts a fixed frame
 * with one video per half. Uses
 * [ReactionLayout] so the exported video has the same geometry.
 *
 * Both slots stay at fixed positions in the composition and only their modifiers change on swap
 * or layout change, so the player's TextureView and the camera viewfinder are never recreated.
 */
@Composable
private fun ReactionStage(
    state: ReactionUiState,
    player: Player,
    surfaceRequest: SurfaceRequest?,
    onPickBackground: () -> Unit,
    onPickReaction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val background = state.background
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        val stageModifier = state.canvasAspectRatio
            ?.let { Modifier.aspectRatio(it) }
            ?: Modifier.fillMaxSize()
        // Bands around a fitted video are black, as in the exported video.
        val stageBackground = if (background != null) Modifier.background(Color.Black) else Modifier
        BoxWithConstraints(modifier = stageModifier.then(stageBackground)) {
            val (firstRect, secondRect) = ReactionLayout.slots(
                canvasWidth = constraints.maxWidth.toFloat(),
                canvasHeight = constraints.maxHeight.toFloat(),
                arrangement = state.arrangement,
                fullAspectRatio = state.fullAspectRatio,
                windowAspectRatio = state.windowAspectRatio,
            )
            val density = LocalDensity.current
            val fullSlot = Modifier.placedAt(firstRect, density).zIndex(0f)
            val windowSlot = Modifier.placedAt(secondRect, density).zIndex(1f)

            // Background video slot.
            Box(
                modifier = (if (state.reactionWindowFullScreen) windowSlot else fullSlot).clipToBounds(),
                contentAlignment = Alignment.Center,
            ) {
                if (background != null) {
                    PlayerTexture(
                        player = player,
                        videoAspectRatio = background.aspectRatio,
                        onClickLabel = stringResource(R.string.reaction_change_background),
                        enabled = state.canEdit,
                        onClick = onPickBackground,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    BackgroundPlaceholder(onClick = onPickBackground)
                }
            }

            // Reaction (camera) slot.
            ReactionContent(
                isCameraOn = state.isCameraOn,
                surfaceRequest = surfaceRequest,
                enabled = state.canEdit,
                onPickReaction = onPickReaction,
                modifier = if (state.reactionWindowFullScreen) fullSlot else windowSlot,
            )
        }
    }
}

@Composable
private fun ReactionContent(
    isCameraOn: Boolean,
    surfaceRequest: SurfaceRequest?,
    enabled: Boolean,
    onPickReaction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            // The live reaction is square-cornered like in the exported video.
            .clip(if (isCameraOn) RectangleShape else RoundedCornerShape(12.dp))
            .background(PlaceholderColor),
        contentAlignment = Alignment.Center,
    ) {
        if (isCameraOn) {
            surfaceRequest?.let { request ->
                // EMBEDDED (TextureView): drawn in order on top of the background video.
                CameraXViewfinder(
                    surfaceRequest = request,
                    implementationMode = ImplementationMode.EMBEDDED,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        } else {
            WindowPlaceholder(
                iconRes = R.drawable.ic_reaction_person,
                text = stringResource(R.string.reaction_select_reaction),
                enabled = enabled,
                onClick = onPickReaction,
            )
        }
    }
}

@Composable
private fun ReactionControls(
    state: ReactionUiState,
    onArrangementSelected: (DualLayout) -> Unit,
    onAudioModeSelected: (DualAudioMode) -> Unit,
    onRecordClick: () -> Unit,
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
            labelRes = { it.labelRes() },
            onSelect = onAudioModeSelected,
        )
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (state.isRecording) {
                Text(
                    text = formatDuration(state.recordedDurationMs),
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.align(Alignment.CenterStart),
                )
            }
            RecordButton(
                isRecording = state.isRecording,
                enabled = !state.isExporting,
                onClick = onRecordClick,
            )
        }
    }
}

@Composable
private fun RecordButton(isRecording: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val label = stringResource(if (isRecording) R.string.reaction_stop else R.string.reaction_start)
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .border(4.dp, Color.White.copy(alpha = if (enabled) 1f else 0.4f), CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = if (isRecording) {
                Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(RecordRed)
            } else {
                Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(RecordRed)
            }
        )
    }
}

private fun DualAudioMode.labelRes(): Int = when (this) {
    DualAudioMode.Main -> R.string.reaction_audio_background
    DualAudioMode.Secondary -> R.string.reaction_audio_reaction
    DualAudioMode.Both -> R.string.reaction_audio_both
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    return String.format(Locale.US, "%02d:%02d", totalSeconds / 60, totalSeconds % 60)
}

private fun Context.isGranted(permission: String) =
    checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

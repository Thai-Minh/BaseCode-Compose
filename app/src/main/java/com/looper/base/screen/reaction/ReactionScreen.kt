package com.looper.base.screen.reaction

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.view.TextureView
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.compose.CameraXViewfinder
import androidx.camera.core.SurfaceRequest
import androidx.camera.viewfinder.core.ImplementationMode
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.zIndex
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import com.looper.base.R
import com.looper.base.screen.reaction.data.ReactionAudioMode
import com.looper.base.screen.reaction.data.ReactionCorner
import com.looper.base.screen.reaction.data.ReactionEffect
import com.looper.base.screen.reaction.data.ReactionLayout
import com.looper.base.screen.reaction.data.ReactionUiState
import org.koin.compose.viewmodel.koinViewModel
import java.util.Locale

private val ScreenBackground = Color(0xFF1C1B1B)
private val PlaceholderColor = Color(0xFF4A4A4A)
private val PillColor = Color(0xFFC4C4C4)
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
                    .fillMaxWidth(),
            )
            if (uiState.isReadyToRecord) {
                ReactionControls(
                    state = uiState,
                    onCornerSelected = viewModel::onCornerSelected,
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

@Composable
private fun CircleIconButton(
    @DrawableRes iconRes: Int,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.15f))
            .clickable(enabled = enabled, onClickLabel = label, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = if (enabled) Color.White else Color.White.copy(alpha = 0.3f),
            modifier = Modifier.size(22.dp),
        )
    }
}

/**
 * The canvas (the background video's aspect ratio): one video fills it, the other sits in a
 * corner window. Uses [ReactionLayout] so the exported video has the same geometry.
 *
 * Both slots stay at fixed positions in the composition and only their modifiers change on swap,
 * so the player's TextureView and the camera viewfinder are never recreated.
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
        val stageModifier = if (background != null) {
            Modifier.aspectRatio(background.aspectRatio)
        } else {
            Modifier.fillMaxSize()
        }
        BoxWithConstraints(modifier = stageModifier) {
            val widthPx = constraints.maxWidth.toFloat()
            val heightPx = constraints.maxHeight.toFloat()
            val (windowWidth, windowHeight) = ReactionLayout.windowSize(widthPx, heightPx)
            val margin = ReactionLayout.margin(widthPx)
            val fullSlot = Modifier
                .fillMaxSize()
                .zIndex(0f)
            val windowSlot = with(LocalDensity.current) {
                Modifier
                    .align(state.corner.alignment)
                    .padding(margin.toDp())
                    .size(windowWidth.toDp(), windowHeight.toDp())
                    .zIndex(1f)
            }

            // Background video slot.
            Box(
                modifier = (if (state.isSwapped) windowSlot else fullSlot).clipToBounds(),
                contentAlignment = Alignment.Center,
            ) {
                if (background != null) {
                    PlayerTexture(
                        player = player,
                        videoAspectRatio = background.aspectRatio,
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
                modifier = if (state.isSwapped) fullSlot else windowSlot,
            )
        }
    }
}

/**
 * Plays [player] into a TextureView (composes correctly with the camera viewfinder), scaled to
 * cover the slot like the export's center crop.
 */
@Composable
private fun PlayerTexture(player: Player, videoAspectRatio: Float, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier = modifier.clipToBounds(), contentAlignment = Alignment.Center) {
        val slotWidth = constraints.maxWidth.toFloat()
        val slotHeight = constraints.maxHeight.toFloat()
        val (width, height) = if (slotWidth / slotHeight > videoAspectRatio) {
            slotWidth to slotWidth / videoAspectRatio
        } else {
            slotHeight * videoAspectRatio to slotHeight
        }
        with(LocalDensity.current) {
            AndroidView(
                factory = { context -> TextureView(context).also(player::setVideoTextureView) },
                onRelease = player::clearVideoTextureView,
                modifier = Modifier.requiredSize(width.toDp(), height.toDp()),
            )
        }
    }
}

@Composable
private fun BackgroundPlaceholder(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_reaction_video),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(64.dp),
        )
        Spacer(modifier = Modifier.height(16.dp))
        PillButton(
            text = stringResource(R.string.reaction_select_background),
            onClick = onClick,
            fontSizeSp = 15,
        )
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
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    painter = painterResource(R.drawable.ic_reaction_person),
                    contentDescription = null,
                    tint = Color(0xFF9E9E9E),
                    modifier = Modifier.size(56.dp),
                )
                Spacer(modifier = Modifier.height(8.dp))
                PillButton(
                    text = stringResource(R.string.reaction_select_reaction),
                    onClick = onPickReaction,
                    enabled = enabled,
                    fontSizeSp = 10,
                    maxLines = 2,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun PillButton(
    text: String,
    onClick: () -> Unit,
    fontSizeSp: Int,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    maxLines: Int = 1,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(PillColor)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = Color.Black,
            fontSize = fontSizeSp.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = maxLines,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReactionControls(
    state: ReactionUiState,
    onCornerSelected: (ReactionCorner) -> Unit,
    onAudioModeSelected: (ReactionAudioMode) -> Unit,
    onRecordClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ReactionCorner.entries.forEach { corner ->
                CornerOption(
                    corner = corner,
                    selected = corner == state.corner,
                    enabled = state.canEdit,
                    onClick = { onCornerSelected(corner) },
                )
            }
        }

        val segmentColors = SegmentedButtonDefaults.colors(
            activeContainerColor = Color.White,
            activeContentColor = Color.Black,
            activeBorderColor = Color.White,
            inactiveContainerColor = Color.Transparent,
            inactiveContentColor = Color.White,
            inactiveBorderColor = Color.White.copy(alpha = 0.5f),
            disabledActiveContainerColor = Color.White.copy(alpha = 0.4f),
            disabledActiveContentColor = Color.Black,
            disabledActiveBorderColor = Color.White.copy(alpha = 0.4f),
            disabledInactiveContainerColor = Color.Transparent,
            disabledInactiveContentColor = Color.White.copy(alpha = 0.4f),
            disabledInactiveBorderColor = Color.White.copy(alpha = 0.2f),
        )
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            val modes = ReactionAudioMode.entries
            modes.forEachIndexed { index, mode ->
                SegmentedButton(
                    selected = mode == state.audioMode,
                    onClick = { onAudioModeSelected(mode) },
                    shape = SegmentedButtonDefaults.itemShape(index, modes.size),
                    enabled = state.canEdit,
                    colors = segmentColors,
                    label = { Text(text = stringResource(mode.labelRes()), maxLines = 1) },
                )
            }
        }

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
private fun CornerOption(
    corner: ReactionCorner,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val label = stringResource(corner.labelRes())
    val color = when {
        !enabled -> Color.White.copy(alpha = 0.3f)
        selected -> Color.White
        else -> Color.White.copy(alpha = 0.6f)
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) Color.White.copy(alpha = 0.2f) else Color.Transparent)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = label }
            .padding(8.dp),
    ) {
        Canvas(modifier = Modifier.width(28.dp).height(44.dp)) {
            val stroke = 2.dp.toPx()
            drawRoundRect(
                color = color,
                topLeft = Offset(stroke / 2, stroke / 2),
                size = Size(size.width - stroke, size.height - stroke),
                cornerRadius = CornerRadius(4.dp.toPx()),
                style = Stroke(width = stroke),
            )
            val inset = 4.dp.toPx()
            val windowWidth = size.width * ReactionLayout.WIDTH_FRACTION * 1.2f
            val windowHeight = windowWidth / ReactionLayout.ASPECT_RATIO
            val left = if (corner.isStart) inset else size.width - inset - windowWidth
            val top = if (corner.isTop) inset else size.height - inset - windowHeight
            drawRect(color = color, topLeft = Offset(left, top), size = Size(windowWidth, windowHeight))
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

@Composable
private fun ExportOverlay(progress: Int) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f))
            // Swallow touches while saving.
            .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(
                progress = { progress / 100f },
                color = Color.White,
                trackColor = Color.White.copy(alpha = 0.2f),
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.reaction_saving, progress),
                color = Color.White,
            )
        }
    }
}

/** Absolute (not RTL-mirrored) alignment, matching the exported video's geometry. */
private val ReactionCorner.alignment: Alignment
    get() = when (this) {
        ReactionCorner.TopStart -> AbsoluteAlignment.TopLeft
        ReactionCorner.TopEnd -> AbsoluteAlignment.TopRight
        ReactionCorner.BottomStart -> AbsoluteAlignment.BottomLeft
        ReactionCorner.BottomEnd -> AbsoluteAlignment.BottomRight
    }

private fun ReactionCorner.labelRes(): Int = when (this) {
    ReactionCorner.TopStart -> R.string.reaction_corner_top_start
    ReactionCorner.TopEnd -> R.string.reaction_corner_top_end
    ReactionCorner.BottomStart -> R.string.reaction_corner_bottom_start
    ReactionCorner.BottomEnd -> R.string.reaction_corner_bottom_end
}

private fun ReactionAudioMode.labelRes(): Int = when (this) {
    ReactionAudioMode.Background -> R.string.reaction_audio_background
    ReactionAudioMode.Reaction -> R.string.reaction_audio_reaction
    ReactionAudioMode.Both -> R.string.reaction_audio_both
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    return String.format(Locale.US, "%02d:%02d", totalSeconds / 60, totalSeconds % 60)
}

private fun Context.isGranted(permission: String) =
    checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

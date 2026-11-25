package com.looper.base.screen.dualcamera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.currentStateAsState
import com.looper.base.R
import com.looper.base.screen.dualcamera.data.Camera2CameraIds
import com.looper.base.screen.dualcamera.data.DualCameraEffect
import com.looper.base.screen.dualcamera.data.DualCameraSupport
import com.looper.base.screen.dualcamera.data.DualCameraUiState
import com.looper.base.screen.dualcamera.data.DualCaptureMode
import com.looper.base.screen.dualcamera.data.DualLayout
import com.looper.base.screen.dualcamera.data.LayoutRect
import com.looper.base.screen.dualcamera.data.PreviewLayout
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.compose.viewmodel.koinViewModel
import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds

/** Max wait for the front camera before opening the back one anyway. */
private const val FRONT_OPEN_TIMEOUT_MS = 3_000L

@Composable
fun DualCameraScreen(
    onClose: () -> Unit,
    viewModel: DualCameraViewModel = koinViewModel(),
) {
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(context.isGranted(Manifest.permission.CAMERA))
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        hasCameraPermission = context.isGranted(Manifest.permission.CAMERA)
    }
    val requestPermissions = {
        permissionLauncher.launch(
            arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
        )
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) requestPermissions()
    }

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            val message = when (effect) {
                is DualCameraEffect.PhotoSaved -> if (effect.isSingleCamera) {
                    R.string.dual_camera_photo_saved_single
                } else {
                    R.string.dual_camera_photo_saved
                }

                is DualCameraEffect.VideoSaved -> R.string.dual_camera_video_saved
                is DualCameraEffect.Error -> effect.messageRes
            }
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    // Recording can't continue once the camera lifecycle stops.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.stopRecording() }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val primaryRequest by viewModel.primarySurfaceRequest.collectAsStateWithLifecycle()
    val secondaryRequest by viewModel.secondarySurfaceRequest.collectAsStateWithLifecycle()

    if (hasCameraPermission) {
        val lifecycleOwner = LocalLifecycleOwner.current
        LaunchedEffect(lifecycleOwner, uiState.mode) {
            viewModel.bindToCamera(lifecycleOwner, uiState.mode)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        when {
            !hasCameraPermission -> MessageContent(
                title = stringResource(R.string.dual_camera_permission_title),
                message = stringResource(R.string.dual_camera_permission_message),
                actionLabel = stringResource(R.string.dual_camera_permission_grant),
                onAction = requestPermissions,
            )

            uiState.support == DualCameraSupport.Unsupported -> MessageContent(
                title = stringResource(R.string.dual_camera_unsupported_title),
                message = stringResource(R.string.dual_camera_unsupported_message),
            )

            else -> {
                // Top of the bottom controls (root px): bottom PiP layouts stay above them.
                var controlsTop by remember { mutableStateOf<Float?>(null) }
                val feedConfig = FeedConfig(
                    layout = uiState.layout,
                    isSwapped = uiState.isSwapped,
                    controlsTopInRoot = controlsTop,
                    onPreviewLayout = viewModel::onPreviewLayout,
                )

                if (uiState.support == DualCameraSupport.Camera2Fallback) {
                    uiState.camera2Ids?.let { ids ->
                        Camera2DualPreview(
                            cameraIds = ids,
                            feedConfig = feedConfig,
                            backHandle = viewModel.camera2Back,
                            frontHandle = viewModel.camera2Front,
                        )
                    }
                } else {
                    CameraXPreview(
                        uiState = uiState,
                        feedConfig = feedConfig,
                        primaryRequest = primaryRequest,
                        secondaryRequest = secondaryRequest,
                    )
                }

                CameraOverlay(
                    uiState = uiState,
                    onLayoutSelected = viewModel::onLayoutSelected,
                    onSwapClick = viewModel::onSwapClick,
                    onPhotoClick = viewModel::onPhotoClick,
                    onVideoClick = viewModel::onVideoClick,
                    onControlsTop = { controlsTop = it },
                )
            }
        }

        CloseButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopStart)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(8.dp)
        )
    }
}

/** Everything [DualFeedLayout] needs besides the two feeds. */
private class FeedConfig(
    val layout: DualLayout,
    val isSwapped: Boolean,
    val controlsTopInRoot: Float?,
    val onPreviewLayout: (PreviewLayout) -> Unit,
)

@Composable
private fun CameraXPreview(
    uiState: DualCameraUiState,
    feedConfig: FeedConfig,
    primaryRequest: SurfaceRequest?,
    secondaryRequest: SurfaceRequest?,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (uiState.mode == DualCaptureMode.Photo) {
            // Photo: primary = back camera, secondary = front camera.
            DualFeedLayout(
                config = feedConfig,
                back = { modifier -> primaryRequest?.let { CameraXFeed(it, modifier) } },
                front = { modifier -> secondaryRequest?.let { CameraXFeed(it, modifier) } },
            )
        } else {
            // Video: both cameras already composited by CameraX into one stream.
            primaryRequest?.let { request ->
                CameraXViewfinder(
                    surfaceRequest = request,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        if (!uiState.isCameraBound) {
            CircularProgressIndicator(
                color = Color.White,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}

@Composable
private fun CameraXFeed(surfaceRequest: SurfaceRequest, modifier: Modifier) {
    // EMBEDDED (TextureView) in both slots: it can be clipped, layered and resized when
    // swapping without re-requesting a surface.
    CameraXViewfinder(
        surfaceRequest = surfaceRequest,
        implementationMode = ImplementationMode.EMBEDDED,
        modifier = modifier,
    )
}

/**
 * Lays out the two camera feeds for [FeedConfig.layout]: two equal halves, or one full screen
 * plus a rounded PiP in a corner. [DualCameraMedia.composeLikePreview] and [DualVideoRecorder]
 * render photos / videos from the same measured geomety.
 *
 * Each feed always stays in the same composition slot and only receives a different modifier,
 * so swapping or changing the layout just re-lays out the views (no new surface / camera
 * restart). [zIndex] keeps the second feed above the main one.
 *
 * The measured geometry is reported through [FeedConfig.onPreviewLayout].
 */
@Composable
private fun DualFeedLayout(
    config: FeedConfig,
    back: @Composable (Modifier) -> Unit,
    front: @Composable (Modifier) -> Unit,
) {
    val layout = config.layout
    val density = LocalDensity.current
    val currentOnPreviewLayout by rememberUpdatedState(config.onPreviewLayout)
    var viewportBounds by remember { mutableStateOf<Rect?>(null) }
    var mainBounds by remember { mutableStateOf<Rect?>(null) }
    var secondBounds by remember { mutableStateOf<Rect?>(null) }

    LaunchedEffect(viewportBounds, mainBounds, secondBounds, layout) {
        val viewport = viewportBounds ?: return@LaunchedEffect
        val main = mainBounds ?: return@LaunchedEffect
        val second = secondBounds ?: return@LaunchedEffect
        fun Rect.relative() = LayoutRect(left - viewport.left, top - viewport.top, width, height)
        currentOnPreviewLayout(
            PreviewLayout(
                viewportWidth = viewport.width,
                viewportHeight = viewport.height,
                main = main.relative(),
                second = second.relative(),
                secondCornerRadius = if (layout.isPip) {
                    second.width * PreviewLayout.PIP_CORNER_FRACTION
                } else {
                    0f
                },
            ).croppedToContent() // Saved media has no empty bands (side by side layout).
        )
    }

    // Space taken by the bottom controls, so bottom PiPs aren't hidden behind them.
    val bottomReserved = with(density) {
        val viewport = viewportBounds
        val controlsTop = config.controlsTopInRoot
        if (viewport == null || controlsTop == null) 0.dp
        else (viewport.bottom - controlsTop).coerceAtLeast(0f).toDp()
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { viewportBounds = it.boundsInRoot() }
    ) {
        val pipWidth = maxWidth * PreviewLayout.PIP_WIDTH_FRACTION
        val secondShape = if (layout.isPip) {
            RoundedCornerShape(pipWidth * PreviewLayout.PIP_CORNER_FRACTION)
        } else {
            RectangleShape
        }

        val (mainPlacement, secondPlacement) = when (layout) {
            DualLayout.SplitVertical ->
                Modifier.align(Alignment.TopCenter).fillMaxWidth().fillMaxHeight(0.5f) to
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(0.5f)

            // Full width, height from the camera's portrait aspect, vertically centered.
            DualLayout.SplitHorizontal ->
                Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxWidth(0.5f)
                    .aspectRatio(PreviewLayout.SPLIT_HORIZONTAL_ASPECT_RATIO) to
                    Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxWidth(0.5f)
                        .aspectRatio(PreviewLayout.SPLIT_HORIZONTAL_ASPECT_RATIO)

            else -> Modifier.fillMaxSize() to pipPlacement(layout, bottomReserved)
        }

        val mainModifier = Modifier
            .zIndex(0f)
            .then(mainPlacement)
            .onGloballyPositioned { mainBounds = it.boundsInRoot() }
            .clipToBounds()
            .background(Color.DarkGray)
        val secondModifier = Modifier
            .zIndex(1f)
            .then(secondPlacement)
            .onGloballyPositioned { secondBounds = it.boundsInRoot() }
            .clip(secondShape)
            .background(Color.DarkGray)

        back(if (config.isSwapped) secondModifier else mainModifier)
        front(if (config.isSwapped) mainModifier else secondModifier)
    }
}

/** Size and corner position of the PiP window for a `Pip*` [layout]. */
@Composable
private fun BoxWithConstraintsScope.pipPlacement(layout: DualLayout, bottomReserved: Dp): Modifier {
    val pipWidth = maxWidth * PreviewLayout.PIP_WIDTH_FRACTION
    val margin = maxWidth * PreviewLayout.PIP_MARGIN_FRACTION
    val isTop = layout == DualLayout.PipTopStart || layout == DualLayout.PipTopEnd
    val isStart = layout == DualLayout.PipTopStart || layout == DualLayout.PipBottomStart
    val alignment = when {
        isTop && isStart -> Alignment.TopStart
        isTop -> Alignment.TopEnd
        isStart -> Alignment.BottomStart
        else -> Alignment.BottomEnd
    }
    val horizontalSide = if (isStart) WindowInsetsSides.Start else WindowInsetsSides.End
    // Top: below the status bar. Bottom: above the controls (which already include the nav bar).
    val insetSides = if (isTop) WindowInsetsSides.Top + horizontalSide else horizontalSide

    return Modifier
        .align(alignment)
        .windowInsetsPadding(WindowInsets.safeDrawing.only(insetSides))
        .padding(bottom = if (isTop) 0.dp else bottomReserved)
        .padding(margin)
        .width(pipWidth)
        .aspectRatio(PreviewLayout.PIP_ASPECT_RATIO)
}

/**
 * Dual camera for devices without declared concurrent support: both cameras are opened
 * directly with Camera2. Cameras are released whenever the screen is not STARTED.
 *
 * The back camera has priority: it is opened only after the front one has settled. When the
 * device can't keep both open, CameraService evicts the older client, so the back camera is
 * the one that keeps streaming (and gets captured).
 */
@Composable
private fun Camera2DualPreview(
    cameraIds: Camera2CameraIds,
    feedConfig: FeedConfig,
    backHandle: Camera2CameraHandle,
    frontHandle: Camera2CameraHandle,
) {
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    if (!lifecycleState.isAtLeast(Lifecycle.State.STARTED)) return

    // Fresh gate each time the cameras are (re)opened.
    val frontSettled = remember(cameraIds) { MutableStateFlow(false) }

    DualFeedLayout(
        config = feedConfig,
        back = { modifier ->
            Camera2Viewfinder(
                cameraId = cameraIds.backId,
                implementationMode = ImplementationMode.EMBEDDED,
                handle = backHandle,
                awaitBeforeOpen = {
                    withTimeoutOrNull(FRONT_OPEN_TIMEOUT_MS.milliseconds) { frontSettled.first { it } }
                },
                modifier = modifier,
            )
        },
        front = { modifier ->
            Camera2Viewfinder(
                cameraId = cameraIds.frontId,
                implementationMode = ImplementationMode.EMBEDDED,
                handle = frontHandle,
                onSettled = { frontSettled.value = true },
                modifier = modifier,
            )
        },
    )
}

@Composable
private fun CameraOverlay(
    uiState: DualCameraUiState,
    onLayoutSelected: (DualLayout) -> Unit,
    onSwapClick: () -> Unit,
    onPhotoClick: () -> Unit,
    onVideoClick: () -> Unit,
    onControlsTop: (Float) -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (uiState.isRecording) {
            RecordingIndicator(
                durationMs = uiState.recordedDurationMs,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(top = 16.dp)
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .onGloballyPositioned { onControlsTop(it.boundsInRoot().top) }
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(bottom = 24.dp)
        ) {
            LayoutPicker(
                selected = uiState.layout,
                enabled = uiState.canEditLayout,
                onSelect = onLayoutSelected,
            )
            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                SwapButton(enabled = uiState.canEditLayout, onClick = onSwapClick)
                PhotoButton(
                    enabled = uiState.isPhotoEnabled,
                    isCapturing = uiState.isCapturing,
                    onClick = onPhotoClick,
                )
                VideoButton(
                    enabled = uiState.isVideoEnabled,
                    isRecording = uiState.isRecording,
                    onClick = onVideoClick,
                )
            }
        }
    }
}

/** Row of small diagrams, one per [DualLayout]. */
@Composable
private fun LayoutPicker(
    selected: DualLayout,
    enabled: Boolean,
    onSelect: (DualLayout) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .alpha(if (enabled) 1f else 0.4f)
            .clip(RoundedCornerShape(50))
            .background(Color.Black.copy(alpha = 0.4f))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        DualLayout.entries.forEach { layout ->
            val description = stringResource(layout.labelRes())
            val isSelected = layout == selected
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSelected) Color.White.copy(alpha = 0.25f) else Color.Transparent)
                    .selectable(
                        selected = isSelected,
                        enabled = enabled,
                        role = Role.RadioButton,
                        onClick = { onSelect(layout) },
                    )
                    .semantics { contentDescription = description }
            ) {
                LayoutDiagram(
                    layout = layout,
                    color = if (isSelected) Color.White else Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.size(width = 20.dp, height = 30.dp),
                )
            }
        }
    }
}

/** Phone-shaped outline with the main feed (faded) and the second feed (solid). */
@Composable
private fun LayoutDiagram(layout: DualLayout, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val stroke = 1.5.dp.toPx()
        drawRoundRect(
            color = color,
            cornerRadius = CornerRadius(3.dp.toPx()),
            style = Stroke(width = stroke),
        )
        val inset = stroke + 1.dp.toPx()
        val gap = 1.dp.toPx()
        val left = inset
        val top = inset
        val width = size.width - inset * 2
        val height = size.height - inset * 2
        val faded = color.copy(alpha = color.alpha * 0.4f)

        when (layout) {
            DualLayout.SplitVertical -> {
                val half = (height - gap) / 2
                drawRect(faded, Offset(left, top), Size(width, half))
                drawRect(color, Offset(left, top + half + gap), Size(width, half))
            }

            DualLayout.SplitHorizontal -> {
                val half = (width - gap) / 2
                val halfHeight = (half / PreviewLayout.SPLIT_HORIZONTAL_ASPECT_RATIO).coerceAtMost(height)
                val y = top + (height - halfHeight) / 2
                drawRect(faded, Offset(left, y), Size(half, halfHeight))
                drawRect(color, Offset(left + half + gap, y), Size(half, halfHeight))
            }

            else -> {
                drawRect(faded, Offset(left, top), Size(width, height))
                val pip = Size(width * 0.45f, width * 0.45f / PreviewLayout.PIP_ASPECT_RATIO)
                val isTop = layout == DualLayout.PipTopStart || layout == DualLayout.PipTopEnd
                val isStart = layout == DualLayout.PipTopStart || layout == DualLayout.PipBottomStart
                val margin = 1.5.dp.toPx()
                // Mirror Start/End for RTL like the real layout does.
                val onLeft = isStart == (layoutDirection == LayoutDirection.Ltr)
                val x = if (onLeft) left + margin else left + width - margin - pip.width
                val y = if (isTop) top + margin else top + height - margin - pip.height
                drawRect(color, Offset(x, y), pip)
            }
        }
    }
}

private fun DualLayout.labelRes(): Int = when (this) {
    DualLayout.SplitVertical -> R.string.dual_camera_layout_split_vertical
    DualLayout.SplitHorizontal -> R.string.dual_camera_layout_split_horizontal
    DualLayout.PipTopStart -> R.string.dual_camera_layout_pip_top_start
    DualLayout.PipTopEnd -> R.string.dual_camera_layout_pip_top_end
    DualLayout.PipBottomStart -> R.string.dual_camera_layout_pip_bottom_start
    DualLayout.PipBottomEnd -> R.string.dual_camera_layout_pip_bottom_end
}

@Composable
private fun SwapButton(enabled: Boolean, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(56.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.4f))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_dual_camera_swap),
            contentDescription = stringResource(R.string.dual_camera_swap),
            tint = Color.White,
            modifier = Modifier.size(28.dp),
        )
    }
}

@Composable
private fun PhotoButton(enabled: Boolean, isCapturing: Boolean, onClick: () -> Unit) {
    val description = stringResource(R.string.dual_camera_take_photo)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(76.dp)
            .border(4.dp, Color.White, CircleShape)
            .padding(8.dp)
            .clip(CircleShape)
            .background(if (enabled) Color.White else Color.Gray)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description }
    ) {
        if (isCapturing) {
            CircularProgressIndicator(
                color = Color.Black,
                strokeWidth = 3.dp,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

@Composable
private fun VideoButton(enabled: Boolean, isRecording: Boolean, onClick: () -> Unit) {
    val description = stringResource(
        if (isRecording) R.string.dual_camera_stop_recording else R.string.dual_camera_start_recording
    )
    val innerColor = if (enabled) Color.Red else Color.Gray
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(56.dp)
            .border(3.dp, Color.White, CircleShape)
            .clip(CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description }
    ) {
        if (isRecording) {
            Box(
                Modifier
                    .size(20.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(innerColor)
            )
        } else {
            Box(
                Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(innerColor)
            )
        }
    }
}

@Composable
private fun RecordingIndicator(durationMs: Long, modifier: Modifier = Modifier) {
    val totalSeconds = durationMs / 1000
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(Color.Black.copy(alpha = 0.5f))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(Color.Red)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = String.format(Locale.US, "%02d:%02d", totalSeconds / 60, totalSeconds % 60),
            color = Color.White,
            fontSize = 14.sp,
        )
    }
}

@Composable
private fun CloseButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    TextButton(onClick = onClick, modifier = modifier) {
        Text(
            text = stringResource(R.string.dual_camera_close),
            color = Color.White,
        )
    }
}

@Composable
private fun MessageContent(
    title: String,
    message: String,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = message,
            color = Color.White.copy(alpha = 0.8f),
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null) {
            Spacer(Modifier.height(24.dp))
            Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}

private fun Context.isGranted(permission: String) =
    checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

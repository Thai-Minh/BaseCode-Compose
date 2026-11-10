package com.looper.base.screen.home

import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.looper.base.R
import com.looper.base.screen.home.data.HomeTourStep
import com.looper.base.ui.theme.Color000000
import com.looper.base.ui.theme.Color010300
import com.looper.base.ui.theme.Color2A2A2A
import com.looper.base.ui.theme.Color444444
import com.looper.base.ui.theme.Color999999
import com.looper.base.ui.theme.ColorFFEE32
import com.looper.base.ui.theme.ColorFFFFFF
import com.looper.base.ui.theme.sfProTextStyle
import com.looper.base.utils.clickable
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeSourceSelection
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import kotlin.math.min

private val TooltipWidth = 296.dp
private val TooltipReserve = 230.dp
private val TooltipEstimatedHeight = 200.dp
private val TooltipGap = 12.dp
private val RingPadding = 8.dp
private val ArrowOverlap = 16.dp
private val ArrowInset = 1.6.dp
private val TooltipShape = RoundedCornerShape(size = 32.dp)

private enum class TourPlacement { BELOW, ABOVE }

private enum class TourArrow { UP, DOWN }

private data class TourSpec(
    @StringRes val titleRes: Int,
    @StringRes val messageRes: Int,
    val index: Int,
    @StringRes val primaryRes: Int,
    @StringRes val secondaryRes: Int?,
    val placement: TourPlacement?,
    val cornerRadius: Dp,
    val hasRing: Boolean,
    val arrowAlpha: Float,
)

private fun HomeTourStep.spec(): TourSpec = when (this) {
    HomeTourStep.INTRO -> TourSpec(
        titleRes = R.string.home_tour_intro_title,
        messageRes = R.string.home_tour_intro_message,
        index = 1,
        primaryRes = R.string.home_tour_start,
        secondaryRes = R.string.home_tour_nevermind,
        placement = null,
        cornerRadius = 0.dp,
        hasRing = false,
        arrowAlpha = 1f,
    )

    HomeTourStep.PASTE_LINK -> TourSpec(
        titleRes = R.string.home_tour_paste_title,
        messageRes = R.string.home_tour_paste_message,
        index = 2,
        primaryRes = R.string.home_tour_next,
        secondaryRes = R.string.home_tour_skip,
        placement = TourPlacement.BELOW,
        cornerRadius = 32.dp,
        hasRing = true,
        arrowAlpha = 1f,
    )

    HomeTourStep.NOW_PLAYING -> TourSpec(
        titleRes = R.string.home_tour_player_title,
        messageRes = R.string.home_tour_player_message,
        index = 3,
        primaryRes = R.string.home_tour_next,
        secondaryRes = R.string.home_tour_skip,
        placement = TourPlacement.ABOVE,
        cornerRadius = 50.dp,
        hasRing = false,
        arrowAlpha = 0.5f,
    )

    HomeTourStep.SOCIAL_APPS -> TourSpec(
        titleRes = R.string.home_tour_source_title,
        messageRes = R.string.home_tour_source_message,
        index = 4,
        primaryRes = R.string.home_tour_got_it,
        secondaryRes = null,
        placement = TourPlacement.BELOW,
        cornerRadius = 50.dp,
        hasRing = true,
        arrowAlpha = 0.5f,
    )
}

private val TooltipBlurStyle = HazeBlurStyle {
    blurRadius(radius = 23.dp)
    noiseFactor(factor = 0f)
    colorEffects(
        effects = listOf(
            HazeColorEffect.tint(color = Color000000.copy(alpha = 0.8f)),
            HazeColorEffect.tint(color = Color999999, blendMode = BlendMode.Darken),
            HazeColorEffect.tint(color = Color444444.copy(alpha = 0.6f), blendMode = BlendMode.Plus),
        ),
    )
    fallbackColorEffect(effect = HazeColorEffect.tint(color = Color2A2A2A))
}

internal class HomeTourTargets {
    val hazeState = HazeState()
    val bounds = mutableStateMapOf<HomeTourStep, Rect>()
    val requesters = HomeTourStep.entries.associateWith {
        BringIntoViewRequester()
    }
}

@Composable
internal fun rememberHomeTourTargets(): HomeTourTargets = remember {
    HomeTourTargets()
}

internal fun Modifier.homeTourTarget(
    step: HomeTourStep,
    targets: HomeTourTargets,
): Modifier = this
    .bringIntoViewRequester(bringIntoViewRequester = targets.requesters.getValue(key = step))
    .onGloballyPositioned { coordinates ->
        targets.bounds[step] = Rect(
            offset = coordinates.positionOnScreen(),
            size = coordinates.size.toSize(),
        )
    }

@Composable
internal fun HomeTourOverlay(
    step: HomeTourStep,
    total: Int,
    targets: HomeTourTargets,
    onNext: () -> Unit,
    onSkip: () -> Unit,
) {
    val spec = step.spec()
    val density = LocalDensity.current
    val navigationBarBottom = WindowInsets.navigationBars.getBottom(density = density).toFloat()

    LaunchedEffect(key1 = step) {
        val placement = spec.placement ?: return@LaunchedEffect
        val size = targets.bounds[step]?.size ?: return@LaunchedEffect
        val reserve = with(density) {
            TooltipReserve.toPx()
        }
        targets.requesters.getValue(key = step).bringIntoView(
            rect = when (placement) {
                TourPlacement.BELOW -> Rect(
                    left = 0f,
                    top = 0f,
                    right = size.width,
                    bottom = size.height + reserve + navigationBarBottom,
                )

                TourPlacement.ABOVE -> Rect(
                    left = 0f,
                    top = -reserve,
                    right = size.width,
                    bottom = size.height + navigationBarBottom,
                )
            },
        )
    }

    Dialog(
        onDismissRequest = onSkip,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnClickOutside = false,
        ),
    ) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect {
            window?.setDimAmount(0f)
            window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }

        var origin by remember {
            mutableStateOf(value = Offset.Zero)
        }
        val target = targets.bounds[step]
            ?.takeIf {
                spec.placement != null
            }
            ?.translate(offset = -origin)

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { coordinates ->
                    origin = coordinates.positionOnScreen()
                }
                .pointerInput(key1 = Unit) {
                    detectTapGestures()
                },
        ) {
            val ringPadding = with(density) {
                if (spec.hasRing) RingPadding.toPx() else 0f
            }
            val gap = with(density) {
                TooltipGap.toPx()
            }
            val ring = target?.inflate(delta = ringPadding)
            val containerHeight = with(density) {
                maxHeight.toPx()
            } - navigationBarBottom
            val arrow = when {
                ring == null -> null
                spec.placement == TourPlacement.BELOW &&
                    ring.bottom + gap + with(density) { TooltipEstimatedHeight.toPx() } <= containerHeight -> TourArrow.UP

                spec.placement == TourPlacement.ABOVE &&
                    ring.top - gap - with(density) { TooltipEstimatedHeight.toPx() } < 0f -> TourArrow.UP

                else -> TourArrow.DOWN
            }

            TourScrim(
                target = target,
                ring = ring,
                cornerRadius = spec.cornerRadius,
                hasRing = spec.hasRing,
            )

            Layout(
                content = {
                    TourTooltip(
                        spec = spec,
                        total = total,
                        arrow = arrow,
                        hazeState = targets.hazeState,
                        onPrimary = onNext,
                        onSecondary = onSkip,
                    )
                },
                modifier = Modifier.fillMaxSize(),
            ) { measurables, constraints ->
                val placeable = measurables.first().measure(
                    constraints = constraints.copy(minWidth = 0, minHeight = 0),
                )
                layout(width = constraints.maxWidth, height = constraints.maxHeight) {
                    val x = (constraints.maxWidth - placeable.width) / 2
                    val y = when {
                        ring == null -> (constraints.maxHeight - placeable.height) / 2
                        arrow == TourArrow.UP -> (ring.bottom + gap).toInt()
                        else -> (ring.top - gap - placeable.height).toInt()
                    }
                    placeable.place(position = IntOffset(x = x, y = y))
                }
            }
        }
    }
}

@Composable
private fun TourScrim(
    target: Rect?,
    ring: Rect?,
    cornerRadius: Dp,
    hasRing: Boolean,
) {
    Canvas(
        modifier = Modifier.fillMaxSize(),
    ) {
        val dim = Color000000.copy(alpha = 0.8f)
        if (target == null || ring == null) {
            drawRect(color = dim)
            return@Canvas
        }
        val ringPadding = target.left - ring.left
        val targetRadius = min(
            cornerRadius.toPx(),
            min(target.width, target.height) / 2f,
        )
        val ringRadius = targetRadius + ringPadding
        val hole = RoundRect(
            rect = ring,
            cornerRadius = CornerRadius(x = ringRadius, y = ringRadius),
        )
        drawPath(
            path = Path().apply {
                fillType = PathFillType.EvenOdd
                addRect(rect = Rect(offset = Offset.Zero, size = size))
                addRoundRect(roundRect = hole)
            },
            color = dim,
        )
        if (hasRing) {
            drawPath(
                path = Path().apply {
                    fillType = PathFillType.EvenOdd
                    addRoundRect(roundRect = hole)
                    addRoundRect(
                        roundRect = RoundRect(
                            rect = target,
                            cornerRadius = CornerRadius(x = targetRadius, y = targetRadius),
                        ),
                    )
                },
                color = Color010300,
            )
            val stroke = 1.dp.toPx()
            val strokeRadius = ringRadius - stroke / 2f
            drawPath(
                path = Path().apply {
                    addRoundRect(
                        roundRect = RoundRect(
                            rect = ring.deflate(delta = stroke / 2f),
                            cornerRadius = CornerRadius(x = strokeRadius, y = strokeRadius),
                        ),
                    )
                },
                color = ColorFFEE32,
                style = Stroke(width = stroke),
            )
        }
    }
}

@Composable
private fun TourTooltip(
    spec: TourSpec,
    total: Int,
    arrow: TourArrow?,
    hazeState: HazeState,
    onPrimary: () -> Unit,
    onSecondary: () -> Unit,
) {
    Box(
        modifier = Modifier.width(width = TooltipWidth),
    ) {
        if (arrow != null) {
            Image(
                painter = painterResource(id = R.drawable.ic_home_tour_arrow),
                contentDescription = null,
                modifier = Modifier
                    .align(
                        alignment = if (arrow == TourArrow.UP) {
                            Alignment.TopCenter
                        } else {
                            Alignment.BottomCenter
                        }
                    )
                    .graphicsLayer {
                        scaleY = if (arrow == TourArrow.UP) 1f else -1f
                    }
                    .padding(top = ArrowInset)
                    .alpha(alpha = spec.arrowAlpha),
            )
        }

        Column(
            modifier = Modifier
                .padding(
                    top = if (arrow == TourArrow.UP) ArrowOverlap else 0.dp,
                    bottom = if (arrow == TourArrow.DOWN) ArrowOverlap else 0.dp,
                )
                .fillMaxWidth()
                .clip(shape = TooltipShape)
                .hazeBlur(
                    input = HazeInput.Sources(
                        state = hazeState,
                        selection = HazeSourceSelection.All,
                    ),
                    style = TooltipBlurStyle,
                )
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            ColorFFFFFF.copy(alpha = 0.22f),
                            ColorFFFFFF.copy(alpha = 0.08f),
                        ),
                    ),
                    shape = TooltipShape,
                )
                .padding(all = 24.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(space = 12.dp),
            ) {
                Text(
                    text = stringResource(id = spec.titleRes),
                    style = sfProTextStyle(
                        fontSize = 18.sp,
                        lineHeight = 26.sp,
                        fontWeight = FontWeight.W700,
                    ),
                    modifier = Modifier.weight(weight = 1f),
                )
                Text(
                    text = stringResource(id = R.string.home_tour_step, spec.index, total),
                    style = sfProTextStyle(
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        color = ColorFFFFFF.copy(alpha = 0.5f),
                    ),
                    modifier = Modifier.padding(top = 3.dp),
                )
            }

            Spacer(modifier = Modifier.height(height = 10.dp))

            Text(
                text = stringResource(id = spec.messageRes),
                style = sfProTextStyle(
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = ColorFFFFFF.copy(alpha = 0.5f),
                ),
            )

            Spacer(modifier = Modifier.height(height = 24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = if (spec.secondaryRes != null) {
                    Arrangement.SpaceBetween
                } else {
                    Arrangement.Start
                },
            ) {
                spec.secondaryRes?.let { secondaryRes ->
                    TourButton(
                        text = stringResource(id = secondaryRes),
                        primary = false,
                        onClick = onSecondary,
                    )
                }
                TourButton(
                    text = stringResource(id = spec.primaryRes),
                    primary = true,
                    onClick = onPrimary,
                )
            }
        }
    }
}

@Composable
private fun TourButton(
    text: String,
    primary: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(shape = RoundedCornerShape(size = 32.dp))
            .background(
                color = if (primary) {
                    ColorFFEE32
                } else {
                    ColorFFEE32.copy(alpha = 0.15f)
                }
            )
            .clickable(
                rippleColor = if (primary) {
                    Color000000
                } else {
                    ColorFFEE32
                },
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = sfProTextStyle(
                fontSize = 14.sp,
                lineHeight = 20.sp,
                fontWeight = if (primary) {
                    FontWeight.W500
                } else {
                    FontWeight.W400
                },
                color = if (primary) {
                    Color000000
                } else {
                    ColorFFEE32
                },
            ),
            maxLines = 1,
        )
    }
}

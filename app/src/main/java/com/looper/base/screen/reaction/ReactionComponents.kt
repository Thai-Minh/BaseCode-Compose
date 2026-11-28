package com.looper.base.screen.reaction

import android.R.attr.enabled
import android.R.attr.onClick
import android.view.TextureView
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import com.looper.base.R
import com.looper.base.screen.common.data.DualLayout
import com.looper.base.screen.reaction.data.ReactionLayout
import com.looper.base.screen.reaction.data.ReactionRect
import kotlin.math.roundToInt

/* UI pieces of the reaction screen. */

internal val PlaceholderColor = Color(0xFF4A4A4A)
private val PillColor = Color(0xFFC4C4C4)

@Composable
internal fun CircleIconButton(
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
 * Plays [player] into a TextureView (composes correctly with other video views), scaled to
 * cover the slot like the export's center crop. Tapping it calls [onClick] (e.g. to pick another
 * video) while [enabled].
 */
@Composable
internal fun PlayerTexture(
    player: Player,
    videoAspectRatio: Float,
    onClickLabel: String,
    modifier: Modifier = Modifier,
) {
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

/** Full-frame placeholder: a video icon and a "Select Background Video" pill. */
@Composable
internal fun BackgroundPlaceholder(modifier: Modifier = Modifier) {
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
            fontSizeSp = 15,
        )
    }
}

/** Window placeholder content: a small icon and a two-line pill. */
@Composable
internal fun WindowPlaceholder(
    @DrawableRes iconRes: Int,
    text: String,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = Color(0xFF9E9E9E),
            modifier = Modifier.size(56.dp),
        )
        Spacer(modifier = Modifier.height(8.dp))
        PillButton(
            text = text,
            fontSizeSp = 10,
            maxLines = 2,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

@Composable
internal fun PillButton(
    text: String,
    fontSizeSp: Int,
    modifier: Modifier = Modifier,
    maxLines: Int = 1,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(PillColor)
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

/** The layout choices: split vertical, split horizontal and the movable PiP window. */
@Composable
internal fun ArrangementSelector(
    selected: DualLayout,
    enabled: Boolean,
    onSelect: (DualLayout) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        DualLayout.entries.forEach { arrangement ->
            ArrangementOption(
                arrangement = arrangement,
                selected = arrangement == selected,
                enabled = enabled,
                onClick = { onSelect(arrangement) },
            )
        }
    }
}

@Composable
private fun ArrangementOption(
    arrangement: DualLayout,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val label = stringResource(arrangement.labelRes())
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
            .padding(6.dp),
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
            val innerWidth = size.width - 2 * inset
            val innerHeight = size.height - 2 * inset
            when (arrangement) {
                // Second video = the solid half.
                DualLayout.SplitVertical -> {
                    val half = (innerHeight - stroke) / 2
                    drawRect(
                        color = color,
                        topLeft = Offset(inset, inset + half + stroke),
                        size = Size(innerWidth, half),
                    )
                }

                DualLayout.SplitHorizontal -> {
                    val half = (innerWidth - stroke) / 2
                    drawRect(
                        color = color,
                        topLeft = Offset(inset + half + stroke, inset),
                        size = Size(half, innerHeight),
                    )
                }

                // Movable window: drawn in the middle.
                DualLayout.PipCustom -> {
                    val windowWidth = size.width * ReactionLayout.WIDTH_FRACTION * 1.2f
                    val windowHeight = windowWidth / ReactionLayout.ASPECT_RATIO
                    drawRect(
                        color = color,
                        topLeft = Offset(
                            (size.width - windowWidth) / 2,
                            (size.height - windowHeight) / 2,
                        ),
                        size = Size(windowWidth, windowHeight),
                    )
                }
            }
        }
    }
}

@Composable
internal fun ExportOverlay(progress: Int) {
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

/**
 * Places the element at [rect] (pixels from the canvas' top-left, not RTL-mirrored), matching the
 * exported video's geometry, moved by [shift] (pixels). [shift] is read in the layout phase: a
 * dragged window moves without recomposing anything.
 */
internal fun Modifier.placedAt(
    rect: ReactionRect,
    density: Density,
    shift: () -> Offset = { Offset.Zero },
): Modifier =
    offset {
        val delta = shift()
        IntOffset((rect.left + delta.x).roundToInt(), (rect.top + delta.y).roundToInt())
    }.size(with(density) { rect.width.toDp() }, with(density) { rect.height.toDp() })

/** Whether [point] (pixels from the canvas' top-left) is inside this rect. */
internal operator fun ReactionRect.contains(point: Offset): Boolean =
    point.x in left..(left + width) && point.y in top..(top + height)

@StringRes
private fun DualLayout.labelRes(): Int = when (this) {
    DualLayout.SplitVertical -> R.string.reaction_layout_split_vertical
    DualLayout.SplitHorizontal -> R.string.reaction_layout_split_horizontal
    DualLayout.PipCustom -> R.string.reaction_layout_pip_custom
}

package com.looper.base.screen.profile

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.innerShadow
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.looper.base.R
import com.looper.base.ui.theme.Color000000
import com.looper.base.ui.theme.Color0E100D
import com.looper.base.ui.theme.Color111111
import com.looper.base.ui.theme.Color1E1E1E
import com.looper.base.ui.theme.ColorFFEE32
import com.looper.base.ui.theme.ColorFFFFFF
import com.looper.base.ui.theme.sfProTextStyle
import com.looper.base.utils.clickable
import com.looper.base.utils.clickableBorderCircle

@Composable
internal fun SettingIconBadge(
    @DrawableRes iconRes: Int,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(size = 48.dp)
            .clip(shape = CircleShape)
            .background(color = Color111111)
            .innerShadow(
                shape = CircleShape,
                shadow = Shadow(
                    radius = 6.dp,
                    color = ColorFFEE32.copy(alpha = 0.1f),
                    offset = DpOffset(x = 0.dp, y = (-6).dp),
                ),
            )
            .innerShadow(
                shape = CircleShape,
                shadow = Shadow(
                    radius = 6.dp,
                    color = ColorFFEE32.copy(alpha = 0.1f),
                    offset = DpOffset(x = 0.dp, y = 6.dp),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(id = iconRes),
            contentDescription = null,
        )
    }
}

@Composable
internal fun SettingRow(
    @DrawableRes iconRes: Int,
    label: String,
    height: Dp,
    horizontalPadding: Dp,
    textStartPadding: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(size = 36.dp)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(height = height)
            .clip(shape = shape)
            .background(color = ColorFFFFFF.copy(alpha = 0.05f))
            .border(
                width = 0.5.dp,
                color = ColorFFFFFF.copy(alpha = 0.1f),
                shape = shape,
            )
            .clickable(
                rippleColor = ColorFFFFFF,
                onClick = onClick,
            )
            .padding(horizontal = horizontalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingIconBadge(iconRes = iconRes)

        Text(
            text = label,
            style = sfProTextStyle(
                fontSize = 16.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.W500,
            ),
            maxLines = 1,
            modifier = Modifier
                .weight(weight = 1f)
                .padding(start = textStartPadding, end = 12.dp),
        )

        Image(
            painter = painterResource(id = R.drawable.ic_home_chevron_right),
            contentDescription = null,
        )
    }
}

@Composable
internal fun SheetHeader(
    title: String,
    onBackClick: () -> Unit,
    onDoneClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, top = 23.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(size = 40.dp)
                .clip(shape = CircleShape)
                .background(color = ColorFFFFFF.copy(alpha = 0.1f))
                .clickableBorderCircle(
                    onClick = onBackClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_social_app_back),
                contentDescription = stringResource(id = R.string.social_app_back),
            )
        }

        Text(
            text = title,
            style = sfProTextStyle(
                fontSize = 18.sp,
                lineHeight = 26.sp,
                fontWeight = FontWeight.W600,
            ),
            maxLines = 1,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .weight(weight = 1f)
                .padding(horizontal = 12.dp),
        )

        Image(
            painter = painterResource(id = R.drawable.ic_profile_done),
            contentDescription = stringResource(id = R.string.profile_done),
            modifier = Modifier.clickableBorderCircle(
                rippleColor = Color000000,
                onClick = onDoneClick,
            ),
        )
    }
}

@Composable
internal fun CheckOption(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                rippleColor = ColorFFFFFF,
                onClick = onClick,
            )
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(space = 10.dp),
    ) {
        Text(
            text = title,
            style = sfProTextStyle(
                fontSize = 16.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.W500,
            ),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(space = 12.dp),
        ) {
            Text(
                text = description,
                style = sfProTextStyle(
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = ColorFFFFFF.copy(alpha = 0.5f),
                ),
                modifier = Modifier.weight(weight = 1f),
            )

            Image(
                painter = painterResource(id = R.drawable.ic_profile_check),
                contentDescription = null,
                modifier = Modifier.alpha(
                    alpha = if (selected) {
                        1f
                    } else {
                        0f
                    }
                ),
            )
        }
    }
}

@Composable
internal fun AppToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val trackColor by animateColorAsState(
        targetValue = if (checked) {
            ColorFFEE32
        } else {
            ColorFFFFFF.copy(alpha = 0.16f)
        },
        label = "toggleTrack",
    )
    val knobOffset by animateDpAsState(
        targetValue = if (checked) {
            24.dp
        } else {
            2.dp
        },
        label = "toggleKnob",
    )

    Box(
        modifier = modifier
            .size(width = 54.dp, height = 32.dp)
            .clip(shape = CircleShape)
            .background(color = trackColor)
            .clickable(
                rippleColor = ColorFFFFFF,
                onClick = {
                    onCheckedChange(!checked)
                },
            ),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .offset(x = knobOffset)
                .size(size = 28.dp)
                .shadow(
                    elevation = 3.dp,
                    shape = CircleShape,
                )
                .background(
                    color = Color0E100D,
                    shape = CircleShape,
                ),
        )
    }
}

@Composable
internal fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
    fontWeight: FontWeight = FontWeight.W500,
    containerColor: Color = ColorFFEE32,
    contentColor: Color = Color000000,
) {
    Box(
        modifier = modifier
            .alpha(
                alpha = if (enabled) {
                    1f
                } else {
                    0.5f
                }
            )
            .clip(shape = RoundedCornerShape(size = 32.dp))
            .background(color = containerColor)
            .clickable(
                rippleColor = contentColor,
                enabled = enabled,
                onClick = onClick,
            )
            .padding(paddingValues = contentPadding),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = sfProTextStyle(
                fontSize = 16.sp,
                lineHeight = 24.sp,
                fontWeight = fontWeight,
                color = contentColor,
            ),
            maxLines = 1,
        )
    }
}

@Composable
internal fun AppDialog(
    onDismissRequest: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect {
            window?.setDimAmount(0.8f)
        }

        val shape = RoundedCornerShape(size = 32.dp)

        Column(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .fillMaxWidth()
                .clip(shape = shape)
                .background(color = Color1E1E1E)
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            ColorFFFFFF.copy(alpha = 0.22f),
                            ColorFFFFFF.copy(alpha = 0.08f),
                        ),
                    ),
                    shape = shape,
                )
                .padding(horizontal = 16.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content,
        )
    }
}

@Composable
internal fun DialogTexts(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(space = 10.dp),
    ) {
        Text(
            text = title,
            style = sfProTextStyle(
                fontSize = 18.sp,
                lineHeight = 26.sp,
                fontWeight = FontWeight.W600,
            ),
            textAlign = TextAlign.Center,
        )
        Text(
            text = message,
            style = sfProTextStyle(
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = ColorFFFFFF.copy(alpha = 0.5f),
            ),
            textAlign = TextAlign.Center,
        )
    }
}

internal fun Modifier.topRoundedBorder(
    width: Dp,
    color: Color,
    cornerRadius: Dp,
    drawSides: Boolean,
): Modifier = drawWithContent {
    drawContent()
    val stroke = width.toPx()
    val inset = stroke / 2f
    val radius = cornerRadius.toPx()
    val right = size.width - inset
    val path = Path().apply {
        if (drawSides) {
            moveTo(x = inset, y = size.height)
            lineTo(x = inset, y = radius)
        } else {
            moveTo(x = inset, y = radius)
        }
        arcTo(
            rect = Rect(
                offset = Offset(x = inset, y = inset),
                size = Size(width = radius * 2f, height = radius * 2f),
            ),
            startAngleDegrees = 180f,
            sweepAngleDegrees = 90f,
            forceMoveTo = false,
        )
        lineTo(x = right - radius, y = inset)
        arcTo(
            rect = Rect(
                offset = Offset(x = right - radius * 2f, y = inset),
                size = Size(width = radius * 2f, height = radius * 2f),
            ),
            startAngleDegrees = 270f,
            sweepAngleDegrees = 90f,
            forceMoveTo = false,
        )
        if (drawSides) {
            lineTo(x = right, y = size.height)
        }
    }
    drawPath(
        path = path,
        color = color,
        style = Stroke(width = stroke),
    )
}

@Composable
internal fun VerticalGap(height: Dp) {
    Spacer(modifier = Modifier.height(height = height))
}

@Composable
internal fun HorizontalGap(width: Dp) {
    Spacer(modifier = Modifier.width(width = width))
}

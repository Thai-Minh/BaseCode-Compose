package com.looper.base.utils

import android.annotation.SuppressLint
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.debugInspectorInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.looper.base.ui.theme.ColorFFFFFF

@Composable
fun ImageForm(
    @DrawableRes resId: Int,
    modifier: Modifier = Modifier,
    contentScale: ContentScale? = null,
    colorFilter: ColorFilter? = null
) {
    Image(
        painter = painterResource(id = resId),
        contentDescription = null,
        modifier = modifier.then(Modifier.size(size = 48.dp)),
        contentScale = contentScale ?: ContentScale.None,
        colorFilter = colorFilter
    )
}

@Composable
fun ComposableLifecycle(
    key: Any? = null,
    lifeCycleOwner: LifecycleOwner = LocalLifecycleOwner.current,
    onEvent: (LifecycleOwner, Lifecycle.Event) -> Unit,
    onDispose: () -> Unit = {}
) {
    DisposableEffect(key1 = key ?: lifeCycleOwner.hashCode()) {
        val observer = LifecycleEventObserver { source, event ->
            onEvent(source, event)
        }
        lifeCycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifeCycleOwner.lifecycle.removeObserver(observer)
            onDispose()
        }
    }
}

@Composable
fun MeasureUnconstrainedViewWidth(
    viewToMeasure: @Composable () -> Unit,
    content: @Composable (measuredWidth: Dp) -> Unit,
) {
    SubcomposeLayout { constraints ->
        val measuredWidth = subcompose("viewToMeasure", viewToMeasure)[0]
            .measure(Constraints()).width.toDp()

        val contentPlaceable = subcompose("content") {
            content(measuredWidth)
        }[0].measure(constraints)

        layout(contentPlaceable.width, contentPlaceable.height) {
            contentPlaceable.place(0, 0)
        }
    }
}

@SuppressLint("UnnecessaryComposedModifier")
fun Modifier.clickableBorder(
    enable: Boolean = true,
    onClick: () -> Unit = {}
): Modifier = composed {
    clip(shape = RoundedCornerShape(size = 8.dp))
        .clickable(rippleColor = ColorFFFFFF, enabled = enable, onClick = onClick)
}

@SuppressLint("UnnecessaryComposedModifier")
fun Modifier.clickableBorderCircle(
    enable: Boolean = true,
    onClick: () -> Unit = {}
): Modifier = composed {
    clip(shape = CircleShape).clickable(
        enabled = enable,
        rippleColor = ColorFFFFFF,
        onClick = onClick
    )
}

@SuppressLint("UnnecessaryComposedModifier")
fun Modifier.clickableBorderCircle(
    enable: Boolean = true,
    rippleColor: Color = ColorFFFFFF.copy(alpha = 0.3f),
    onClick: () -> Unit = {},
) = composed(
    inspectorInfo = debugInspectorInfo {
        name = "clickable"
        properties["enable"] = enable
        properties["onClick"] = onClick
    },
    factory = {
        clip(shape = CircleShape)
            .clickable(rippleColor = rippleColor, enabled = enable, onClick = onClick)
    }
)

fun Modifier.conditional(
    condition: Boolean,
    ifTrue: Modifier.() -> Modifier,
    ifFalse: (Modifier.() -> Modifier)? = null
): Modifier {
    return if (condition) {
        then(ifTrue(Modifier))
    } else if (ifFalse != null) {
        then(ifFalse(Modifier))
    } else {
        this
    }
}

fun Modifier.noRippleClickable(enable: Boolean = true, onClick: () -> Unit = {}): Modifier =
    composed {
        clickable(
            enabled = enable,
            indication = null,
            interactionSource = remember { MutableInteractionSource() },
            onClick = onClick
        )
    }

fun Modifier.clickableWhiteColor(
    enabled: Boolean = true,
    onClick: () -> Unit = {}
): Modifier =
    composed {
        clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = ripple(color = Color.White),
            enabled = enabled,
            onClick = onClick
        )
    }

fun Modifier.clickable(
    rippleColor: Color? = null,
    enabled: Boolean = true,
    onClick: () -> Unit = {}
) = composed(
    inspectorInfo = debugInspectorInfo {
        name = "clickable"
        properties["rippleColor"] = rippleColor
        properties["onClick"] = onClick
        properties["enabled"] = onClick
    },
    factory = {
        clickable(
            onClick = onClick,
            enabled = enabled,
            indication = ripple(color = rippleColor ?: Color.Unspecified),
            interactionSource = remember { MutableInteractionSource() }
        )
    })

fun Modifier.combinedClickable(
    rippleColor: Color? = null,
    enabled: Boolean = true,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
    onDoubleClick: (() -> Unit)? = null,
) = composed(
    factory = {
        combinedClickable(
            onClick = onClick,
            onLongClick = onLongClick,
            onDoubleClick = onDoubleClick,
            enabled = enabled,
            indication = ripple(color = rippleColor ?: Color.Unspecified),
            interactionSource = remember { MutableInteractionSource() }
        )
    })

fun Modifier.clickableNoRipple(onClick: () -> Unit = {}): Modifier =
    composed {
        clickable(
            indication = null,
            interactionSource = remember { MutableInteractionSource() }) {
            onClick()
        }
    }

fun Modifier.clip(
    horizontal: Dp = 0.dp,
    vertical: Dp = 0.dp,
): Modifier = this.layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    fun Dp.toPxInt(): Int = this.toPx().toInt()

    layout(
        placeable.width - (horizontal * 2).toPxInt(),
        placeable.height - (vertical * 2).toPxInt()
    ) {
        placeable.placeRelative(-horizontal.toPx().toInt(), -vertical.toPx().toInt())
    }
}

fun Modifier.advancedShadow(
    color: Color = Color.Black,
    alpha: Float = 1f,
    cornersRadius: Dp = 0.dp,
    shadowBlurRadius: Dp = 0.dp,
    offsetY: Dp = 0.dp,
    offsetX: Dp = 0.dp
) = drawBehind {

    val shadowColor = color.copy(alpha = alpha).toArgb()
    val transparentColor = color.copy(alpha = 0f).toArgb()

    drawIntoCanvas {
        val paint = Paint()
        val frameworkPaint = paint.asFrameworkPaint()
        frameworkPaint.color = transparentColor
        frameworkPaint.setShadowLayer(
            shadowBlurRadius.toPx(),
            offsetX.toPx(),
            offsetY.toPx(),
            shadowColor
        )
        it.drawRoundRect(
            0f,
            0f,
            this.size.width,
            this.size.height,
            cornersRadius.toPx(),
            cornersRadius.toPx(),
            paint
        )
    }
}
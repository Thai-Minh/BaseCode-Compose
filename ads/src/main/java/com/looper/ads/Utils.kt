package com.looper.ads

import android.content.Context
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleDestroyedException
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.withResumed
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.roundToInt

fun Context.createAdaptiveBannerShimmerLayout(): View {
    return View.inflate(this, R.layout.inline_adaptive_banner_shimmer, null)
}

fun Context.createBannerShimmerLayout(): View {
    return View.inflate(this, R.layout.adaptive_banner_shimmer, null)
}

fun ViewGroup.synchronizedAdView(view: View, params: ViewGroup.LayoutParams? = null) {
    if (view.parent is ViewGroup)
        (view.parent as ViewGroup).removeAllViews()

    removeAllViews()

    if (params != null)
        addView(view, params)
    else
        addView(view)
}

fun Context.getWidthScreen(): Int {
    val metrics = this.resources.displayMetrics
    return (metrics.widthPixels / metrics.density).toInt()
}

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

fun Context.dp2Px(dp: Int): Int {
    return TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        dp.toFloat(),
        resources.displayMetrics
    ).roundToInt()
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

fun <T> CancellableContinuation<T>.safeResume(value: T) {
    if (this.isActive)
        this.resume(value)
}

fun AppCompatActivity.launchWhenResumed(block: CoroutineScope.() -> Unit) {
    lifecycleScope.launch {
        lifecycle.withResumed {
            block()
        }
    }
}

fun Modifier.clickable(
    color: Color = Color.White,
    enabled: Boolean = true,
    onClick: () -> Unit = {}
): Modifier =
    composed {
        clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = ripple(color = color),
            enabled = enabled,
            onClick = onClick
        )
    }

fun ComponentActivity.launchWhenResumed(block: CoroutineScope.() -> Unit) {
    lifecycleScope.launch {
        lifecycle.withResumed {
            block()
        }
    }
}

suspend fun Lifecycle.awaitResumed() = suspendCancellableCoroutine { continuation ->
    if (currentState == Lifecycle.State.DESTROYED) {
        continuation.resumeWithException(LifecycleDestroyedException())
        return@suspendCancellableCoroutine
    }

    val observer = object : LifecycleEventObserver {
        override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
            if (event == Lifecycle.Event.ON_RESUME) {
                removeObserver(this)
                continuation.safeResume(Unit)
            } else if (event == Lifecycle.Event.ON_DESTROY) {
                removeObserver(this)
                continuation.resumeWithException(LifecycleDestroyedException())
            }
        }
    }

    addObserver(observer)

    continuation.invokeOnCancellation {
        removeObserver(observer)
    }
}

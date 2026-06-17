package com.looper.ads.helper.native_ad.ui

import android.app.Application
import android.graphics.drawable.DrawableContainer
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import androidx.annotation.LayoutRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.looper.ads.ComposableLifecycle
import com.looper.ads.R
import com.looper.ads.conditional
import com.looper.ads.helper.native_ad.NativeAdState
import com.looper.ads.helper.native_ad.NativeAdType
import com.looper.ads.helper.native_ad.NativeAdViewModel
import com.looper.ads.helper.native_ad.NativeAdViewModelFactory
import com.looper.ads.isSupportAdState
import com.looper.ads.isSupportAds
import com.looper.ads.theme.FF222222
import com.looper.ads.theme.FF2E2E2E
import com.looper.ads.theme.FF38B000
import com.looper.ads.theme.FFF5F2FF
import com.looper.ads.theme.FFFFFFFF
import com.zenith.adapter.format.UnifiedNativeAdRender
import com.zenith.adapter.ui.UnifiedNativeAdViewBinder
import com.zenith.adsdk.MediationAd
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

data class NativeSpace internal constructor(
    val start: Dp = 0.dp,
    val top: Dp = 0.dp,
    val end: Dp = 0.dp,
    val bottom: Dp = 0.dp,
    val color: Color = Color.Transparent
) {
    companion object {
        fun space(
            start: Dp = 0.dp,
            top: Dp = 0.dp,
            end: Dp = 0.dp,
            bottom: Dp = 0.dp,
            color: Color = Color.Transparent
        ) = NativeSpace(start, top, end, bottom, color)
    }
}

data class NativeAdProperty(
    val title: Color = FFFFFFFF,
    val description: Color = FFFFFFFF,
    val backgroundAd: Color = FFF5F2FF,
    val titleButton: Color = FFFFFFFF,
    val backgroundButton: Color = FF38B000
)

val NativeSpace.isValid: Boolean
    get() = start > 0.dp || top > 0.dp || end > 0.dp || bottom > 0.dp

@Composable
fun NativeAd(
    modifier: Modifier = Modifier,
    adType: NativeAdType,
    key: String? = "",
    space: NativeSpace = NativeSpace(),
    @LayoutRes shimmerLayoutId: Int,
    @LayoutRes contentLayoutId: Int,
    adProperty: NativeAdProperty = NativeAdProperty(),
    isShowHeadlineAdvertiserLogo: Boolean = true,
    isSupportIconAnim: Boolean = false,
    onAdLoading: () -> Unit = {},
    onAdLoaded: () -> Unit = {},
    onAdFailed: () -> Unit = {},
    viewAbove: @Composable ColumnScope.() -> Unit = { HorizontalBaseLine() },
    viewBelow: @Composable ColumnScope.() -> Unit = { HorizontalBaseLine() },
) {
    val app = LocalContext.current.applicationContext as Application

    val supportAds by isSupportAdState.collectAsStateWithLifecycle()

    val mediationAd = MediationAd.getShared()

    val canShowAny = supportAds && mediationAd != null && mediationAd.getAdUnit(adType.adUnitId) != null

    LaunchedEffect(key1 = canShowAny) {
        if (!canShowAny) {
            delay(50.milliseconds)
            onAdFailed()
        }
    }

    if (!canShowAny) {
        return
    }

    val viewModel: NativeAdViewModel = viewModel(
        key = adType.adUnitId,
        factory = NativeAdViewModelFactory(app = app, type = adType, refreshAd = !key.isNullOrEmpty())
    )

    val nativeAd by viewModel.nativeAd.collectAsStateWithLifecycle(initialValue = NativeAdState.Loading)

    LaunchedEffect(key) {
        viewModel.forceRefreshAd(key)
    }

    NativeAdLifecycle(viewModel = viewModel)

    LaunchedEffect(nativeAd) {
        when (nativeAd) {
            is NativeAdState.Success -> onAdLoaded()
            is NativeAdState.Error -> onAdFailed()
            else -> onAdLoading()
        }
    }

    // Only show if native loading or success
    if (nativeAd !is NativeAdState.Error) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            viewAbove()

            val customModifier = if (modifier.any { Modifier.fillMaxHeight() == it })
                modifier
                    .weight(1f)
                    .conditional(
                        condition = adType.useCardViewWrapper,
                        ifTrue = {
                            padding(horizontal = 6.dp, vertical = 8.dp)
                                .clip(shape = RoundedCornerShape(size = 10.dp))
                                .border(
                                    1.dp,
                                    color = FF222222,
                                    shape = RoundedCornerShape(size = 10.dp)
                                )
                        },
                    )
            else
                modifier.conditional(
                    condition = adType.useCardViewWrapper,
                    ifTrue = {
                        padding(horizontal = 6.dp, vertical = 8.dp)
                            .clip(shape = RoundedCornerShape(size = 10.dp))
                            .border(
                                1.dp,
                                color = FF222222,
                                shape = RoundedCornerShape(size = 10.dp)
                            )
                    },
                )

            BoxSpace(space = space) {
                if (nativeAd is NativeAdState.Success) {
                    val ad = (nativeAd as NativeAdState.Success).nativeAd

                    val enableCollapsible =
                        MediationAd.getShared()?.isAdUnitCollapsible(adType.adUnitId) == true

                    NativeAdView(
                        modifier = customModifier,
                        nativeAd = ad,
                        contentLayoutId = contentLayoutId,
                        adProperty = adProperty,
                        enableCollapsible = enableCollapsible,
                        goneAllAfterCollapsible = adType.goneAllAfterCollapsible,
                        isShowHeadlineAdvertiserLogo = isShowHeadlineAdvertiserLogo,
                        isSupportIconAnim = isSupportIconAnim
                    )
                } else {
                    ShimmerView(
                        modifier = customModifier,
                        shimmerLayoutId = shimmerLayoutId
                    )
                }
            }

            viewBelow()
        }
    }
}

@Composable
private fun ShimmerView(
    modifier: Modifier = Modifier,
    @LayoutRes shimmerLayoutId: Int,
) {
    AndroidView(
        factory = { context ->
            LayoutInflater.from(context).inflate(
                shimmerLayoutId, FrameLayout(context), false
            )
        },
        modifier = modifier.padding(),
    )
}


@Composable
fun NativeAdView(
    modifier: Modifier = Modifier,
    nativeAd: UnifiedNativeAdRender,
    @LayoutRes contentLayoutId: Int,
    adProperty: NativeAdProperty = NativeAdProperty(),
    enableCollapsible: Boolean,
    goneAllAfterCollapsible: Boolean,
    isShowHeadlineAdvertiserLogo: Boolean = true,
    isSupportIconAnim: Boolean = false
) {
    AndroidView(
        modifier = modifier,
        factory = {
            FrameLayout(it)
        },
        update = { parent ->

            parent.removeAllViewAndAnimation()

            val buttonId = if (enableCollapsible)
                R.id.native_call_to_action_expand
            else
                R.id.native_call_to_action

            val binder = UnifiedNativeAdViewBinder.Builder(parent.context, contentLayoutId)
                .setShowHeadlineAdvertiserLogo(isShowHeadlineAdvertiserLogo)
                .buildDefault(
                    enableCollapsible = enableCollapsible,
                    goneAllAfterCollapsible = goneAllAfterCollapsible,
                    buttonExpanded = buttonId,
                    buttonCollapsed = R.id.native_call_to_action_collapsible
                )

            val nativeAdView = nativeAd.render(binder).apply {
                findViewById<TextView>(R.id.native_headline)?.setTextColor(adProperty.title.toArgb())

                findViewById<TextView>(R.id.native_body)?.setTextColor(adProperty.description.toArgb())

                setBackgroundColor(adProperty.backgroundAd.toArgb())

                findViewById<View>(R.id.native_call_to_action)?.updateRippleColor(adProperty.backgroundButton)

                findViewById<View>(R.id.native_call_to_action_expand)?.updateRippleColor(adProperty.backgroundButton)

                findViewById<View>(R.id.native_call_to_action_collapsible)?.updateRippleColor(
                    adProperty.backgroundButton
                )

                findViewById<Button>(R.id.native_call_to_action)?.setTextColor(adProperty.titleButton.toArgb())
            }

            parent.addView(
                nativeAdView,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )

//        if (isSupportIconAnim) startIconAnimation(view = nativeAdView)
        }
    )
}

@Composable
fun NativeAdLifecycle(
    viewModel: NativeAdViewModel,
) {
    val lifecycle = LocalLifecycleOwner.current

    ComposableLifecycle(
        key = viewModel,
        lifeCycleOwner = lifecycle,
        onEvent = { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                viewModel.onPaused()
            } else if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.onResumed()
            }
        },
        onDispose = {
            viewModel.onDisposed()
        }
    )
}

@Composable
inline fun BoxSpace(
    space: NativeSpace, content: @Composable () -> Unit
) {
    if (space.isValid) {
        Row {
            Spacer(
                modifier = Modifier
                    .background(space.color)
                    .width(space.start)
            )
            Column {
                Spacer(
                    modifier = Modifier
                        .background(space.color)
                        .fillMaxWidth()
                        .height(space.top)
                )
                content()
                Spacer(
                    modifier = Modifier
                        .background(space.color)
                        .fillMaxWidth()
                        .height(space.bottom)
                )
            }
            Spacer(
                modifier = Modifier
                    .background(space.color)
                    .width(space.end)
            )
        }
    } else content()
}

fun UnifiedNativeAdViewBinder.Builder.buildDefault(
    enableCollapsible: Boolean = false,
    goneAllAfterCollapsible: Boolean = true,
    buttonExpanded: Int = R.id.native_call_to_action_expand, // nếu là native thường thì truyền id button vào đây
    buttonCollapsed: Int = R.id.native_call_to_action_collapsible
): UnifiedNativeAdViewBinder {

    return setHeadlineResId(R.id.native_headline)
        .setBodyResId(R.id.native_body)
        .setIconResId(R.id.native_icon)
        .setMediaContentResId(R.id.native_media_container)
        .setCallToActionButton1ResId(buttonExpanded)
        .setCallToActionButton2ResId(buttonCollapsed)
        .setAdvertiserViewResId(R.id.native_sponsored_layout)

        .setMediaGroupResId(R.id.native_media_group) // for collapsible
        .setNativeIconGroupResId(R.id.native_icon_group) // for collapsible
        .setCollapsibleBtnResId(R.id.collapsible_btn) // for collapsible

        .setEnableCollapsible(enableCollapsible)
        .setGoneAllAfterCollapsible(goneAllAfterCollapsible)
        .setQuickCollapsible(true)
        .setRandomClickCollapsibleTime(3000)
        .setShowHeadlineAdvertiserLogo(enableCollapsible)
        .build()
}

private fun ViewGroup.removeAllViewAndAnimation() {
    clearAnimation()
    removeAllViews()
}

//private fun startIconAnimation(view: View) {
//    val appIcon = view.findViewById(R.id.native_icon_bound) as? View ?: return
//
//    val animator = ValueAnimator.ofFloat(-10f, 10f).apply {
//        interpolator = LinearInterpolator()
//        repeatMode = ValueAnimator.REVERSE
//        repeatCount = ValueAnimator.INFINITE
//        duration = 250
//    }
//
//    animator.addUpdateListener { animation ->
//        val animatedValue = animation.animatedValue as Float
//        appIcon.rotation = animatedValue
//    }
//
//    animator.start()
//}

private fun View.updateRippleColor(color: Color) {
    val gradientDrawable = background as? StateListDrawable
    val drawableContainerState =
        gradientDrawable?.constantState as? DrawableContainer.DrawableContainerState ?: return
    val children = drawableContainerState.children
    val selectedItem = children[0] as? GradientDrawable
    val unselectedItem = children[1] as? GradientDrawable
    unselectedItem?.setColor(color.toArgb())
    selectedItem?.setColor(color.copy(alpha = 0.7f).toArgb())
}


@Composable
fun HorizontalBaseLine(
    height: Dp = 1.dp,
    color: Color = FF2E2E2E,
    paddingContent: PaddingValues = PaddingValues(),
    paddingLine: PaddingValues = PaddingValues(),
) {
    if (!isSupportAds) return

    Column(modifier = Modifier.padding(paddingValues = paddingContent)) {
        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .padding(paddingValues = paddingLine)
                .height(height = height)
                .background(color = color)
        )
    }
}
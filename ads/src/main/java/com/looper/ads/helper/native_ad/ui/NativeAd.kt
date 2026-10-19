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
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.looper.ads.ComposableLifecycle
import com.looper.ads.R
import com.looper.ads.conditional
import com.looper.ads.helper.native_ad.NativeAdState
import com.looper.ads.helper.native_ad.NativeAdType
import com.looper.ads.helper.native_ad.NativeAdViewModel
import com.looper.ads.helper.native_ad.NativeAdViewModelFactory
import com.looper.ads.isContextActivityDestroyed
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
import com.zenith.adsdk.format.NativeAdLoader
import com.zenith.template_native.NativeAdTemplatesBinder
import com.zenith.template_native.OrigoNativeAdStyle
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
    refreshAd: Boolean = !key.isNullOrEmpty(),
    space: NativeSpace = NativeSpace(),
    @LayoutRes shimmerLayoutId: Int,
    @LayoutRes contentLayoutId: Int,
    adProperty: NativeAdProperty = NativeAdProperty(),
    isShowHeadlineAdvertiserLogo: Boolean = true,
    isSupportIconAnim: Boolean = false,
    onAdLoading: () -> Unit = {},
    onAdLoaded: () -> Unit = {},
    onAdFailed: () -> Unit = {},
    onCollapsibleListener: () -> Unit = {},
    viewAbove: @Composable ColumnScope.() -> Unit = { HorizontalBaseLine() },
    viewBelow: @Composable ColumnScope.() -> Unit = { HorizontalBaseLine() },
) {
    val app = LocalContext.current.applicationContext as Application

    val supportAds by isSupportAdState.collectAsStateWithLifecycle()

    val mediationAd = MediationAd.getShared()

    val canShowAny =
        supportAds && mediationAd != null && mediationAd.getAdUnit(adType.adUnitId) != null

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
        key = adType.name,
        factory = NativeAdViewModelFactory(
            app = app,
            type = adType,
            refreshAd = refreshAd,
        )
    )

    val nativeAd by viewModel.nativeAd.collectAsStateWithLifecycle(NativeAdState.Loading)

    LaunchedEffect(viewModel, key) {
        viewModel.forceRefreshAd()
    }

    NativeAdLifecycle(viewModel = viewModel)

    LaunchedEffect(nativeAd) {
        when (nativeAd) {
            is NativeAdState.Success -> {
                delay(1000.milliseconds)
                onAdLoaded()
            }
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

                    val adUnitId = adType.adUnitId

                    val enableCollapsible = MediationAd.getShared()?.isAdUnitCollapsible(adUnitId) == true

                    NativeAdView(
                        modifier = customModifier,
                        nativeAd = ad,
                        adUnitId = adUnitId,
                        contentLayoutId = contentLayoutId,
                        adProperty = adProperty,
                        enableCollapsible = enableCollapsible,
                        goneAllAfterCollapsible = adType.goneAllAfterCollapsible,
                        isShowHeadlineAdvertiserLogo = isShowHeadlineAdvertiserLogo,
                        isSupportIconAnim = isSupportIconAnim,
                        onCollapsibleListener = onCollapsibleListener,
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
            NativeAdTemplatesBinder.buildShimmer(
                context,
                shimmerLayoutId,
                createOrigoNativeAdStyle()
            )
        },
        modifier = modifier.padding(),
    )
}


@Composable
fun NativeAdView(
    modifier: Modifier = Modifier,
    nativeAd: UnifiedNativeAdRender,
    adUnitId: String,
    @LayoutRes contentLayoutId: Int,
    adProperty: NativeAdProperty = NativeAdProperty(),
    enableCollapsible: Boolean,
    goneAllAfterCollapsible: Boolean,
    isShowHeadlineAdvertiserLogo: Boolean = true,
    isSupportIconAnim: Boolean = false,
    onCollapsibleListener: () -> Unit = {},
) {
    AndroidView(
        modifier = modifier,
        factory = {
            FrameLayout(it)
        },
        update = { parent ->

            parent.removeAllViewAndAnimation()

            val binder = UnifiedNativeAdViewBinder.Builder(parent.context, contentLayoutId)
                .setShowHeadlineAdvertiserLogo(isShowHeadlineAdvertiserLogo)
                .buildDefault(
                    enableCollapsible = enableCollapsible,
                    goneAllAfterCollapsible = goneAllAfterCollapsible,
                    buttonCallToAction = R.id.native_call_to_action,
                    expandedLayout = R.id.native_call_to_action_expand_layout,
                    collapsibleLayout = R.id.native_call_to_action_collapsible_layout
                ).also { binder ->
                    createOrigoNativeAdStyle().applyTo(binder)
                }

            parent.addViewAds(
                adUnitId = adUnitId,
                nativeAd = nativeAd,
                baseAdView = binder
            )
        }
    )
}

private fun createOrigoNativeAdStyle() = OrigoNativeAdStyle()
    .setAdvertiserAttributionDrawableRes(R.drawable.ic_ad_top_left) // for update logo Ads
    .setRootBackgroundColorRes(R.color.color_020202)
    .setHeadlineTextColorRes(R.color.color_FAFAFA)
    .setBodyTextColorRes(R.color.color_EEEEEE)
//    .setIconGroupBackgroundColorRes(R.color.color_F74F23)
    .setCallToActionBackgroundDrawableRes(
        R.drawable.ripple_round_button_gradient
    )

private fun ViewGroup.addViewAds(
    adUnitId: String,
    nativeAd: UnifiedNativeAdRender,
    baseAdView: UnifiedNativeAdViewBinder
): View? {
    if (isContextActivityDestroyed(context)) return null

    val nativeAdView = nativeAd.render(baseAdView)

    val adLifecycleObserver = object : androidx.lifecycle.DefaultLifecycleObserver {
        override fun onStart(owner: androidx.lifecycle.LifecycleOwner) {
            NativeAdLoader.startRefresh(adUnitId)
        }

        override fun onStop(owner: androidx.lifecycle.LifecycleOwner) {
            NativeAdLoader.stopRefresh(adUnitId)
        }
    }

    nativeAdView.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(p0: View) {
            NativeAdLoader.startRefresh(adUnitId)

            findViewTreeLifecycleOwner()?.lifecycle?.addObserver(adLifecycleObserver)
        }

        override fun onViewDetachedFromWindow(p0: View) {
            NativeAdLoader.stopRefresh(adUnitId)

            findViewTreeLifecycleOwner()?.lifecycle?.removeObserver(adLifecycleObserver)
        }
    })

    this.removeAllViews()
    this.addView(nativeAdView)
    return nativeAdView
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
    buttonCallToAction: Int = R.id.native_call_to_action,
    expandedLayout: Int = R.id.native_call_to_action_expand_layout, // nếu là native thường thì truyền id button vào đây
    collapsibleLayout: Int = R.id.native_call_to_action_collapsible_layout,
    onCollapsibleListener: UnifiedNativeAdViewBinder.OnCollapsibleListener =
        UnifiedNativeAdViewBinder.OnCollapsibleListener {}
): UnifiedNativeAdViewBinder {

    return setHeadlineResId(R.id.native_headline)
        .setBodyResId(R.id.native_body)
        .setIconResId(R.id.native_icon)
        .setMediaContentResId(R.id.native_media_container)
        .setCallToActionButtonResId(buttonCallToAction)
        .setMovableCallToActionButtonResId(buttonCallToAction)
        .setExpandedCallToActionContainerResId(expandedLayout)
        .setCollapsedCallToActionContainerResId(collapsibleLayout)
        .setAdvertiserViewResId(R.id.native_sponsored_layout)

        .setMediaGroupResId(R.id.native_media_group) // for collapsible
        .setNativeIconGroupResId(R.id.native_icon_group) // for collapsible
        .setCollapsibleCtaBtnResId(R.id.collapsible_cta_btn) // single overlay control

        .setEnableCollapsible(enableCollapsible)
        .setGoneAllAfterCollapsible(goneAllAfterCollapsible)
        .setQuickCollapsible(true)
        .setRandomClickCollapsibleTime(3000)
        .setShowHeadlineAdvertiserLogo(enableCollapsible)
        .setCollapsibleActionChancePercent(50)
        .setOnCollapsibleListener(onCollapsibleListener)
        .build()
}

private fun ViewGroup.removeAllViewAndAnimation() {
    clearAnimation()
    removeAllViews()
}

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
    val supportAds by isSupportAdState.collectAsStateWithLifecycle()
    if (!supportAds) {
        return
    }

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
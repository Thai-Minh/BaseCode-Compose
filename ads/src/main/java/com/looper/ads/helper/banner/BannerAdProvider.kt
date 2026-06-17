package com.looper.ads.helper.banner

import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.isVisible
import com.looper.admob_v2.AdmobAdapter
import com.looper.ads.AdjustManager
import com.looper.ads.createAdaptiveBannerShimmerLayout
import com.looper.ads.createBannerShimmerLayout
import com.looper.ads.helper.banner.data.BannerAdSize
import com.looper.ads.helper.banner.data.BannerAdType
import com.looper.ads.helper.banner.data.BannerState
import com.looper.ads.helper.native_ad.ui.HorizontalBaseLine
import com.looper.ads.isSupportAds
import com.zenith.adapter.AdError
import com.zenith.adapter.AdRequest
import com.zenith.adapter.AdSize
import com.zenith.adsdk.AdListener
import com.zenith.adsdk.format.AdView
import kotlin.math.roundToInt

fun BannerAdSize.getAdSize(width: Int = -1): AdSize {
    return when (this) {
        BannerAdSize.AdaptiveBanner -> AdSize.SMART_BANNER
        BannerAdSize.InlineBanner -> AdSize.inline(width)
        else -> AdSize.BANNER
    }
}

fun BannerAdSize.getShimmerLayout(
    context: Context,
): View {
    return when (this) {
        BannerAdSize.InlineBanner -> context.createAdaptiveBannerShimmerLayout()
        else -> context.createBannerShimmerLayout()
    }
}

data class BannerPadding(
    val heightDp: Dp = 0.dp,
    val defaultHeightDP: Dp = 0.dp,
    val navigationBarHeightDP: Dp = 0.dp,
)

val BannerPadding.paddingBottomDp get() = heightDp + navigationBarHeightDP

val BannerPadding.defaultPaddingBottomDp get() = defaultHeightDP + navigationBarHeightDP

val LocalBannerPadding = compositionLocalOf { BannerPadding() }

class BannerUiState(val isShowing: MutableState<Boolean> = mutableStateOf(true)) {
    fun showBanner() = updateState(true)

    fun hideBanner() = updateState(false)

    private fun updateState(isShowing: Boolean = true) {
        this.isShowing.value = isShowing
    }
}

val LocalBannerState = compositionLocalOf { BannerUiState() }

@Composable
fun BannerAdProvider(
    contentBannerPadding: PaddingValues = PaddingValues(all = 0.dp),
    type: BannerAdType,
    viewAbove: @Composable ColumnScope.() -> Unit = { HorizontalBaseLine() },
    viewBelow: @Composable ColumnScope.() -> Unit = { HorizontalBaseLine() },
    content: @Composable () -> Unit = {}
) {
    val context = LocalContext.current
    val density = LocalDensity.current

    val container = remember(key1 = type) {
        BannerWrapper(context = context, bannerAdType = type).apply { visibility = View.VISIBLE }
    }

    DisposableEffect(key1 = type) {
        onDispose {
            container.destroy()
            BannerAdPreload.removeAdView(type)
        }
    }

    val bannerUiState = remember { BannerUiState() }
    var bannerHeight by remember { mutableIntStateOf(0) }

    Box {

        val navigationBarHeight = WindowInsets.navigationBars.getBottom(LocalDensity.current)
        val bannerPadding = with(density) {
            BannerPadding(
                heightDp = bannerHeight.toDp(),
                defaultHeightDP = AdSize.SMART_BANNER.getHeight(context).dp,
                navigationBarHeightDP = navigationBarHeight.toDp()
            )
        }

        CompositionLocalProvider(
            LocalBannerPadding provides bannerPadding,
            LocalBannerState provides bannerUiState
        ) {
            val isShowingBanner = LocalBannerState.current.isShowing.value

            content()

            if (isSupportAds && isShowingBanner) {
                Column(
                    modifier = Modifier
                        .align(
                            alignment = { size, space, _ ->
                                // Fix issue
                                val centerX = (space.width - size.width).toFloat() / 2f
                                val centerY = (space.height - size.height).toFloat() / 2f

                                val x = centerX * 1
                                val y = centerY * 2

                                IntOffset(x.roundToInt(), y.roundToInt())
                            }
                        )
                        .wrapContentSize()
                        .navigationBarsPadding()
                        .padding(paddingValues = contentBannerPadding)
                        .onSizeChanged {
                            bannerHeight = it.height
                        },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val isAdShowing = container.isVisible

                    if (isAdShowing) {
                        viewAbove()
                    }

                    AndroidView(
                        factory = { _ ->
                            container
                        })

                    if (isAdShowing) {
                        viewBelow()
                    }
                }
            }
        }
    }
}

fun loadAd(
    context: Context,
    adUnitId: String,
    adSize: AdSize,
//    extras: Bundle? = null,
    onLoadResult: (state: BannerState) -> Unit = { }
) {

    val adView = AdView(context)
    adView.setAdUnitId(adUnitId)
    adView.setAdSize(adSize)

    adView.setAdListener(object : AdListener {
        override fun onAdFailedToLoad(error: AdError) {
            onLoadResult(BannerState.Error)
        }

        override fun onAdLoaded() {
            onLoadResult(BannerState.Success(view = adView, size = adSize))
        }

        override fun onPaidEvent(currencyCode: String, valueMicros: Long) {
            AdjustManager.trackAdRevenue(
                revenue = valueMicros, currency = currencyCode
            )
        }
    })

//    val request = if (extras != null) AdRequest.Builder()
//        .addNetworkExtrasBundle(AdmobAdapter::class.java, extras).build()
//    else null
//
//    adView.loadAd(request)

    adView.loadAd()
}
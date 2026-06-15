package com.looper.ads.helper.native_ad.ui

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.looper.ads.R
import com.looper.ads.helper.native_ad.NativeAdType

@Composable
fun NativeIntroFullScreenAd(
    modifier: Modifier,
    adProperty: NativeAdProperty = NativeAdProperty(),
    onAdLoaded: () -> Unit = {},
    onAdFailed: () -> Unit = {},
    viewAbove: @Composable ColumnScope.() -> Unit = { },
    viewBelow: @Composable ColumnScope.() -> Unit = { },
) {
    NativeAd(
        modifier = modifier,
        adType = NativeAdType.IntroFullScreen,
        contentLayoutId = R.layout.native_full_screen,
        shimmerLayoutId = R.layout.native_full_screen_shimmer,
        isShowHeadlineAdvertiserLogo = true,
        adProperty = adProperty,
        onAdLoaded = onAdLoaded,
        onAdFailed = onAdFailed,
        viewAbove = viewAbove,
        viewBelow = viewBelow
    )
}
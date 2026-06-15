package com.looper.ads.helper.native_ad.ui

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.looper.ads.R
import com.looper.ads.helper.native_ad.NativeAdType

@Composable
fun NativeIntro1Ad(
    modifier: Modifier,
    adProperty: NativeAdProperty = NativeAdProperty(),
    onAdLoaded: () -> Unit = {},
    onAdFailed: () -> Unit = {},
    viewAbove: @Composable ColumnScope.() -> Unit = { },
    viewBelow: @Composable ColumnScope.() -> Unit = { },
) {
    NativeAd(
        modifier = modifier,
        adType = NativeAdType.Intro1,
        contentLayoutId = R.layout.native_language_v1_item,
        shimmerLayoutId = R.layout.native_language_shimmer_v1_item,
        adProperty = adProperty,
        onAdLoaded = onAdLoaded,
        onAdFailed = onAdFailed,
        viewAbove = viewAbove,
        viewBelow = viewBelow
    )
}
package com.looper.ads.helper.native_ad.ui

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.looper.ads.R
import com.looper.ads.helper.native_ad.NativeAdType

@Composable
fun NativeHomeAd(
    modifier: Modifier,
    adProperty: NativeAdProperty = NativeAdProperty(),
    onAdLoaded: () -> Unit = {},
    onAdFailed: () -> Unit = {},
    viewAbove: @Composable ColumnScope.() -> Unit = { HorizontalBaseLine() },
    viewBelow: @Composable ColumnScope.() -> Unit = { },
) {
    NativeAd(
        modifier = modifier,
        adType = NativeAdType.Home,
        contentLayoutId = R.layout.native_collapsible_template_1,
        shimmerLayoutId = R.layout.native_rcv_shimmer_item,
        adProperty = adProperty,
        onAdLoaded = onAdLoaded,
        onAdFailed = onAdFailed,
        viewAbove = viewAbove,
        viewBelow = viewBelow
    )
}
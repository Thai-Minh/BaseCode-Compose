package com.looper.ads.helper.native_ad.ui

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.looper.ads.R
import com.looper.ads.helper.native_ad.NativeAdType

@Composable
fun NativeLanguage1Ad(
    modifier: Modifier,
    onAdLoading: () -> Unit = {},
    onAdLoaded: () -> Unit = {},
    onAdFailed: () -> Unit = {},
    adProperty: NativeAdProperty = NativeAdProperty(),
    viewAbove: @Composable ColumnScope.() -> Unit = { },
    viewBelow: @Composable ColumnScope.() -> Unit = { },
) {
    NativeAd(
        modifier = modifier,
        key = "",
        adType = NativeAdType.Language1,
        contentLayoutId = R.layout.native_language_v2_item,
        shimmerLayoutId = R.layout.native_language_shimmer_v2_item,
        adProperty = adProperty,
        onAdLoading = onAdLoading,
        onAdLoaded = onAdLoaded,
        onAdFailed = onAdFailed,
        viewAbove = viewAbove,
        viewBelow = viewBelow
    )
}

@Composable
fun NativeLanguage2Ad(
    modifier: Modifier,
    key: String? = null,
    adProperty: NativeAdProperty = NativeAdProperty(),
    onAdLoading: () -> Unit = {},
    onAdLoaded: () -> Unit = {},
    onAdFailed: () -> Unit = {},
    viewAbove: @Composable ColumnScope.() -> Unit = { },
    viewBelow: @Composable ColumnScope.() -> Unit = { },
) {
    NativeAd(
        modifier = modifier,
        adType = NativeAdType.Language2,
        contentLayoutId = R.layout.native_language_v1_item,
        shimmerLayoutId = R.layout.native_language_shimmer_v1_item,
        adProperty = adProperty,
        key = key,
        onAdLoading = onAdLoading,
        onAdLoaded = onAdLoaded,
        onAdFailed = onAdFailed,
        viewAbove = viewAbove,
        viewBelow = viewBelow
    )
}
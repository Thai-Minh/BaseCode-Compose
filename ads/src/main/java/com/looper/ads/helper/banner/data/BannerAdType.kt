package com.looper.ads.helper.banner.data

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.dp

enum class BannerAdType(
    val adUnitId: String,
    val bannerAdSize: BannerAdSize = BannerAdSize.InlineBanner,
    val removable: Boolean = true,
    val bannerPadding: PaddingValues = PaddingValues(horizontal = 0.dp),
) {
    // TODO
}
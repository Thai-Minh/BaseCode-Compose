package com.looper.ads.helper.banner.data

import com.zenith.adapter.AdSize
import com.zenith.adsdk.format.AdView

sealed class BannerState {
    data object Error : BannerState()

    data class Loading(
        val size: AdSize = AdSize.SMART_BANNER,
        val bannerAdSize: BannerAdSize = BannerAdSize.AdaptiveBanner,
    ) : BannerState()

    data class Success(
        val view: AdView,
        val size: AdSize
    ) : BannerState()
}
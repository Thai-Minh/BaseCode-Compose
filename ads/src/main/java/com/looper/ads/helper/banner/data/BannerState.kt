package com.looper.ads.helper.banner.data

import com.zenith.adapter.AdSize
import com.zenith.adsdk.format.AdView

sealed class BannerState {
    data object Error : BannerState()
    data object Loading : BannerState()
    data class Success(val view: AdView, val size: AdSize) : BannerState()
}
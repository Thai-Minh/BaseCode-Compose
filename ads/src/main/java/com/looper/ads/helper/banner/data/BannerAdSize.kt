package com.looper.ads.helper.banner.data

import android.os.Bundle
import androidx.core.os.bundleOf
import com.zenith.adapter.AdSize

enum class BannerAdSize {
    AdaptiveBanner,
    CollapsibleTopBanner,
    CollapsibleBottomBanner,
    InlineBanner
}

fun BannerAdSize.getBundle(): Bundle? {
    return when (this) {
        BannerAdSize.CollapsibleTopBanner -> bundleOf("collapsible" to "top")
        BannerAdSize.CollapsibleBottomBanner -> bundleOf("collapsible" to "bottom")
        else -> null
    }
}

fun BannerAdSize.getAdSize(width: Int = -1): AdSize {
    return when (this) {
        BannerAdSize.InlineBanner -> AdSize.inline(width)
        else -> AdSize.SMART_BANNER
    }
}
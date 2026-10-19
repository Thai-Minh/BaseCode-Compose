package com.looper.ads.helper.banner.data

import com.zenith.adapter.AdSize
import com.zenith.adsdk.MediationAd
import com.zenith.adsdk.network.BannerAdFormat

enum class BannerAdSize(
    val remoteValue: String
) {
    AdaptiveBanner(remoteValue = BannerAdFormat.ADAPTIVE),
    InlineSmartBanner(remoteValue = BannerAdFormat.INLINE),
    InlineMediumBanner(remoteValue = BannerAdFormat.MREC),
    StandardBanner(remoteValue = BannerAdFormat.BANNER),
    LargeBanner(remoteValue = BannerAdFormat.LARGE_BANNER),
    FullBanner(remoteValue = BannerAdFormat.FULL_BANNER),
    Leaderboard(remoteValue = BannerAdFormat.LEADERBOARD);

    companion object {
        fun fromRemoteValue(value: String?): BannerAdSize? {
            return when (BannerAdFormat.normalize(value)) {
                BannerAdFormat.ADAPTIVE -> AdaptiveBanner
                BannerAdFormat.INLINE -> InlineSmartBanner
                BannerAdFormat.MREC -> InlineMediumBanner
                BannerAdFormat.BANNER -> StandardBanner
                BannerAdFormat.LARGE_BANNER -> LargeBanner
                BannerAdFormat.FULL_BANNER -> FullBanner
                BannerAdFormat.LEADERBOARD -> Leaderboard
                else -> null
            }
        }
    }
}

fun BannerAdType.resolveBannerAdSize(): BannerAdSize {
    val mediationAd = MediationAd.getShared() ?: return bannerAdSize
    val effectiveValue = mediationAd.resolveAdUnitBannerAdSize(
        adUnitId,
        bannerAdSize.remoteValue,
    )
    return BannerAdSize.fromRemoteValue(effectiveValue) ?: bannerAdSize
}

fun BannerAdSize.getAdSize(width: Int = -1): AdSize {
    return when (this) {
        BannerAdSize.AdaptiveBanner -> AdSize.anchoredAdaptive(width)
        BannerAdSize.InlineSmartBanner -> AdSize.inline(width)
        BannerAdSize.InlineMediumBanner -> AdSize.MEDIUM_RECTANGLE
        BannerAdSize.StandardBanner -> AdSize.BANNER
        BannerAdSize.LargeBanner -> AdSize.LARGE_BANNER
        BannerAdSize.FullBanner -> AdSize.FULL_BANNER
        BannerAdSize.Leaderboard -> AdSize.LEADERBOARD
    }
}
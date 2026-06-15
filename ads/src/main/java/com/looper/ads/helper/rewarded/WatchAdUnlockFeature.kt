package com.looper.ads.helper.rewarded

import androidx.fragment.app.FragmentActivity
import com.looper.ads.AdsManager
import com.looper.ads.safeResume
import kotlinx.coroutines.suspendCancellableCoroutine

suspend fun FragmentActivity.showRewardedUnlockContent(
    isShowAds: Boolean
): Boolean? {
    return showRewardedAdUnlockFeature(
        adType = RewardedAdType.RewardedUnlockContent,
        isShowAds = isShowAds
    )
}

suspend fun FragmentActivity.showRewardedAdUnlockFeature(
    adType: RewardedAdType,
    isShowAds: Boolean,
    trackEventShowAd: (Boolean) -> Unit = {}
): Boolean? {
    if (!isShowAds)
        return false

    return showRewardedAdUnlockFeature(adType = adType, trackEventShowAd)
}

suspend fun FragmentActivity.showRewardedAdUnlockFeature(
    adType: RewardedAdType,
    trackEventShowAd: (Boolean) -> Unit = {}
): Boolean? =
    suspendCancellableCoroutine { continuation ->
        showRewardedAd(
            activity = this,
            adType = adType,
            timeOut = AdsManager.rewardTimeoutMs,
            callback = {
                continuation.safeResume(it)
            },
            trackEventShowAd = trackEventShowAd
        )
    }
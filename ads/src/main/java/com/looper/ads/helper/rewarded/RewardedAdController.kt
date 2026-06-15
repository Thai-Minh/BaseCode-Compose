package com.looper.ads.helper.rewarded

import androidx.activity.ComponentActivity
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import com.looper.ads.AdjustManager
import com.looper.ads.AdsManager
import com.looper.ads.awaitResumed
import com.looper.ads.dialog.SponsorLoadingDialog
import com.looper.ads.isSupportAds
import com.looper.ads.launchWhenResumed
import com.zenith.adapter.AdError
import com.zenith.adsdk.callback.FullScreenContentCallback
import com.zenith.adsdk.format.RewardedAd
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration.Companion.milliseconds

fun loadRewardedAd(
    activity: ComponentActivity,
    adType: RewardedAdType,
    onLoadAdCallback: ((Boolean) -> Unit) = { }
) {
    RewardedAdPreload.preload(
        context = activity,
        adType = adType,
        onLoadAdCallback = onLoadAdCallback
    )
}

fun showRewardedAd(
    activity: FragmentActivity,
    adType: RewardedAdType,
    timeOut: Long = AdsManager.rewardTimeoutMs,
    callback: (Boolean?) -> Unit = {},
    trackEventShowAd: (Boolean) -> Unit = {}
) {
    RewardedAdController.showRewardedAd(
        activity = activity,
        adType = adType,
        timeOut = timeOut,
        callback = callback,
        trackEventShowAd = trackEventShowAd
    )
}

object RewardedAdController {

    private val scope = MainScope()

    private var isAdShowing = false

    fun showRewardedAd(
        activity: FragmentActivity,
        adType: RewardedAdType,
        timeOut: Long = AdsManager.rewardTimeoutMs,
        callback: (Boolean?) -> Unit,
        trackEventShowAd: (Boolean) -> Unit = {}
    ) {

        if (!isSupportAds) {
            callback(null)
            return
        }

        if (isAdShowing)
            return

        scope.launch {
            var dialog: DialogFragment? = null

            if (activity is AppCompatActivity) {
                val fm = activity.supportFragmentManager
                val isResumed = activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)

                if (isResumed && !fm.isStateSaved) {
                    dialog = SponsorLoadingDialog.newInstance().apply {
                        showOnce(fm, "SponsorLoadingDialogFragment")
                    }
                }
            }

            val rewardedAd = try {
                withTimeout(timeOut.milliseconds) {
                    RewardedAdPreload.getPreloadRewardedAd(
                        context = activity,
                        adType = adType
                    )
                }
            } catch (e: Exception) {
                null
            }

            activity.lifecycle.awaitResumed()

            dialog?.dismiss()

            if (rewardedAd != null) {
                isAdShowing = true

                showAd(
                    activity = activity,
                    rewardedAd = rewardedAd,
                    callback = {
                        isAdShowing = false

                        RewardedAdPreload.removeCacheInterAds(adType = adType)

                        callback(it)
                    },
                    trackEventShowAd = trackEventShowAd
                )
            } else {
                callback(false)
            }
        }
    }

    private fun showAd(
        activity: FragmentActivity,
        rewardedAd: RewardedAd,
        callback: (Boolean) -> Unit,
        trackEventShowAd: (Boolean) -> Unit = {}
    ) {
        var success = false

        rewardedAd.setFullScreenContentCallback(object : FullScreenContentCallback {
            override fun onAdDismissedFullScreenContent() {
                activity.launchWhenResumed {
                    callback(success)
                }
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                trackEventShowAd(false)
                activity.launchWhenResumed {
                    callback(success)
                }
            }

            override fun onPaidEvent(
                currencyCode: String,
                valueMicros: Long
            ) {
                trackEventShowAd(true)
                AdjustManager.trackAdRevenue(
                    revenue = valueMicros,
                    currency = currencyCode
                )
            }
        })

        rewardedAd.show(activity) {
            success = true
        }
    }
}
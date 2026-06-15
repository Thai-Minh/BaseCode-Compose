package com.looper.ads.helper.app_open

import android.content.Context
import androidx.activity.ComponentActivity
import com.looper.ads.AdjustManager
import com.looper.ads.awaitResumed
import com.looper.ads.isSupportAds
import com.looper.ads.safeResume
import com.zenith.adapter.AdError
import com.zenith.adsdk.callback.AppOpenAdLoadCallBack
import com.zenith.adsdk.callback.FullScreenContentCallback
import com.zenith.adsdk.format.AppOpenAd
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.milliseconds

object AppOpenSplashAd {
    private val scope by lazy {
        MainScope()
    }

    private var appOpenAdDeferred: Deferred<AppOpenAd?>? = null
    private var showingJob: Job? = null

    @OptIn(ExperimentalCoroutinesApi::class)
    fun isAdLoaded(): Boolean {
        return try {
            appOpenAdDeferred?.isCompleted == true && appOpenAdDeferred?.getCompleted() != null
        } catch (_: Exception) {
            false
        }
    }

    fun load(
        context: Context,
        adUnit: String,
        timeOut: Long,
        onAdLoadCompleted: (success: Boolean) -> Unit = {},
    ) {
        if (appOpenAdDeferred != null || !isSupportAds) return

        appOpenAdDeferred = scope.async {
            val appOpenAd = withTimeoutOrNull(timeOut.milliseconds) {
                loadAppOpenAd(context = context, adUnit = adUnit)
            }

            delay(3000.milliseconds)
            onAdLoadCompleted(appOpenAd != null)

            appOpenAd
        }
    }

    fun show(
        activity: ComponentActivity,
        timeOut: Long,
        callBack: (showed: Boolean) -> Unit
    ) {
        if (showingJob?.isActive == true) return

        val deferred = appOpenAdDeferred

        if (deferred == null || !isSupportAds) {
            callBack(false)
            return
        }

        showingJob = scope.launch {
            val appOpenAd = try {
                withTimeout(timeOut.milliseconds) {
                    deferred.await()
                }.also {
                    if (it == null) appOpenAdDeferred = null
                }
            } catch (e: TimeoutCancellationException) {
                null
            }

            if (appOpenAd == null) {
                callBack(false)
            } else {
                activity.lifecycle.awaitResumed()
                val showed = appOpenAd.showAppOpenAd(activity)

                appOpenAdDeferred = null
                callBack(showed)
            }
        }
    }

    private suspend fun AppOpenAd.showAppOpenAd(
        activity: ComponentActivity,
    ): Boolean = suspendCancellableCoroutine { co ->
        setFullScreenContentCallback(object : FullScreenContentCallback {
            override fun onAdDismissedFullScreenContent() {
                co.safeResume(true)
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                co.safeResume(false)
            }

            override fun onPaidEvent(currencyCode: String, valueMicros: Long) {
                AdjustManager.trackAdRevenue(
                    revenue = valueMicros,
                    currency = currencyCode
                )
            }
        })

        show(activity)
    }

    private suspend fun loadAppOpenAd(context: Context, adUnit: String) =
        suspendCancellableCoroutine { continuation ->
            AppOpenAd.load(context, adUnit, object : AppOpenAdLoadCallBack {
                override fun onAdLoaded(ad: AppOpenAd, adUnit: String) {
                    continuation.safeResume(ad)
                }

                override fun onAdFailedToLoad(error: AdError) {
                    continuation.safeResume(null)
                }
            })
        }
}
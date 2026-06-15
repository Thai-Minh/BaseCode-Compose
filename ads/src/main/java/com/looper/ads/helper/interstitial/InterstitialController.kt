package com.looper.ads.helper.interstitial

import androidx.activity.ComponentActivity
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.looper.ads.AdUnits
import com.looper.ads.AdjustManager
import com.looper.ads.AdsManager.interstitialTimeoutMs
import com.looper.ads.awaitResumed
import com.looper.ads.dialog.SponsorLoadingDialog
import com.looper.ads.helper.interstitial.InterAdState.LoadError
import com.looper.ads.helper.interstitial.InterAdState.LoadSuccess
import com.looper.ads.helper.interstitial.InterAdState.ShowError
import com.looper.ads.helper.interstitial.InterAdState.ShowSuccess
import com.looper.ads.isSupportAds
import com.looper.ads.launchWhenResumed
import com.looper.ads.safeResume
import com.zenith.adapter.AdError
import com.zenith.adsdk.MediationAd
import com.zenith.adsdk.callback.FullScreenContentCallback
import com.zenith.adsdk.format.InterstitialAd
import com.zenith.adsdk.utils.MediationUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.lang.ref.WeakReference

enum class InterAdState {
    LoadError, LoadSuccess, ShowError, ShowSuccess
}

enum class InterstitialType(val adUnitId: String, val backup1: String?, val backup2: String?) {
    Splash(
        adUnitId = AdUnits.InterstitialSplash2Floor.key,
        backup1 = AdUnits.InterstitialSplashMedium.key,
        backup2 = AdUnits.InterstitialSplashAllPrice.key
    ),
    InApp(
        adUnitId = AdUnits.InterstitialInApp2Floor.key,
        backup1 = AdUnits.InterstitialInAppMedium.key,
        backup2 = AdUnits.InterstitialInAppAllPrice.key
    )
}

fun loadAndShowInterstitialAd(
    activity: ComponentActivity,
    type: InterstitialType,
    timeOut: Long = interstitialTimeoutMs,
    showLoadingDialog: Boolean = true,
    callback: (Boolean) -> Unit
) {
    loadAndShowInterstitialAd(
        activity = activity,
        type = type,
        timeOut = timeOut,
        showLoadingDialog = showLoadingDialog,
        onLoadAdCallback = {
            if (!it) {
                callback(false)
            }
        },
        onShowAdCallback = {
            callback(it)
        }
    )
}

fun loadAndShowInterstitialAd(
    activity: ComponentActivity,
    type: InterstitialType,
    timeOut: Long = interstitialTimeoutMs,
    showLoadingDialog: Boolean = true,
    onLoadAdCallback: (isLoadAdSuccess: Boolean) -> Unit = {},
    onShowAdCallback: (isShowAd: Boolean) -> Unit = { }
) {

    val mediationAd = MediationAd.getShared()

    val ids = listOfNotNull(type.adUnitId, type.backup1, type.backup2)

    val acceptShow = mediationAd != null && ids.any { mediationAd.getAdUnit(it) != null }

    if (!isSupportAds || !acceptShow) {
        onShowAdCallback(false)
        return
    }

    InterstitialAdPreload.preload(
        context = activity,
        adType = type,
    )

    InterstitialController.showAd(
        activity = activity,
        type = type,
        timeOut = timeOut,
        showLoadingDialog = showLoadingDialog,
        onLoadAdCallback = onLoadAdCallback,
        onShowAdCallback = onShowAdCallback
    )
}

fun loadInterstitialAd(
    activity: ComponentActivity,
    type: InterstitialType,
    onLoadAdCallback: (isLoadAdSuccess: Boolean) -> Unit = {},
) {
    InterstitialAdPreload.preload(
        context = activity,
        adType = type,
        onLoadAdCallback = onLoadAdCallback
    )
}

fun showInterstitialAd(
    activity: ComponentActivity,
    type: InterstitialType,
    timeOut: Long = interstitialTimeoutMs,
    showLoadingDialog: Boolean = true,
    onLoadAdCallback: (isLoadAdSuccess: Boolean) -> Unit = {},
    onShowAdCallback: (isShowAd: Boolean) -> Unit = {}
) {
    InterstitialController.showAd(
        activity = activity,
        type = type,
        timeOut = timeOut,
        showLoadingDialog = showLoadingDialog,
        onLoadAdCallback = onLoadAdCallback,
        onShowAdCallback = onShowAdCallback
    )
}

object InterstitialController {

    private var weakReferenceActivity: WeakReference<ComponentActivity>? = null

    private val scope by lazy {
        MainScope()
    }

    private var isAdShowing = false

    fun showAd(
        activity: ComponentActivity,
        type: InterstitialType,
        timeOut: Long = interstitialTimeoutMs,
        showLoadingDialog: Boolean = true,
        onLoadAdCallback: (isLoadAdSuccess: Boolean) -> Unit,
        onShowAdCallback: (isShowAd: Boolean) -> Unit
    ) {
        val mediationAd = MediationAd.getShared()
        val ids = listOfNotNull(type.adUnitId, type.backup1, type.backup2)

        val acceptShow = mediationAd != null && ids.any { mediationAd.getAdUnit(it) != null }

        if (!isSupportAds || !acceptShow) {
            callbackOnMain(
                activity = activity,
                state = ShowError,
                onLoadAdCallback = onLoadAdCallback,
                onShowAdCallback = onShowAdCallback
            )
            return
        }

        scope.launch {

            // Can't show interstitial ads if does not respond to the most recent display time
            if (!MediationUtils.isFullScreenAdDisplayIntervalAccepted(type.adUnitId)) {
                callbackOnMain(
                    activity = activity,
                    state = ShowError,
                    onLoadAdCallback = onLoadAdCallback,
                    onShowAdCallback = onShowAdCallback
                )
                return@launch
            }

            val oldActivity = weakReferenceActivity?.get()

            if (isAdShowing && oldActivity == activity)
                return@launch

            weakReferenceActivity = WeakReference(activity)

            isAdShowing = true

            val interState = showAdInternal(
                activity = activity,
                type = type,
                timeOut = timeOut,
                showLoadingDialog = showLoadingDialog
            )

            InterstitialAdPreload.removeCacheInterAds(adType = type)

            isAdShowing = false

            callbackOnMain(
                activity = activity,
                state = interState,
                onLoadAdCallback = onLoadAdCallback,
                onShowAdCallback = onShowAdCallback
            )
        }
    }

    private suspend fun showAdInternal(
        activity: ComponentActivity,
        type: InterstitialType,
        timeOut: Long = interstitialTimeoutMs,
        showLoadingDialog: Boolean
    ): InterAdState {

        var dialog: DialogFragment? = null

        if (showLoadingDialog && activity is AppCompatActivity) {
            dialog = SponsorLoadingDialog.newInstance().apply {
                showOnce(activity.supportFragmentManager, "SponsorLoadingDialogFragment")
            }
        }

        val startTime = System.currentTimeMillis()

        val interAd = try {
            withTimeout(timeOut) {
                async {
                    InterstitialAdPreload.getPreloadInterstitialAd(
                        context = activity,
                        adType = type
                    )
                }.await()
            }
        } catch (e: Exception) {
            null
        }

        val elapsedTime = System.currentTimeMillis() - startTime

        if (elapsedTime < 1000)
            delay(1000 - elapsedTime)

        activity.lifecycle.awaitResumed()

        dialog?.dismiss()

        return runShowAd(activity = activity, interAd = interAd)
    }

    private suspend fun runShowAd(
        activity: ComponentActivity,
        interAd: InterstitialAd?
    ): InterAdState =
        suspendCancellableCoroutine { continuation ->
            if (interAd == null) {
                continuation.safeResume(LoadError)
            } else {
                interAd.setFullScreenContentCallback(object : FullScreenContentCallback {
                    override fun onAdDismissedFullScreenContent() {
                        continuation.safeResume(ShowSuccess)
                    }

                    override fun onAdFailedToShowFullScreenContent(error: AdError) {
                        continuation.safeResume(ShowError)
                    }

                    override fun onPaidEvent(
                        currencyCode: String,
                        valueMicros: Long
                    ) {
                        AdjustManager.trackAdRevenue(
                            revenue = valueMicros,
                            currency = currencyCode
                        )
                    }
                })

                activity.lifecycleScope.launch(Dispatchers.Main) {
                    try {
                        val isReadyToShow = withTimeoutOrNull(2000) {
                            while (!activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                                delay(100)
                            }
                            true
                        }

                        if (isReadyToShow == true && !activity.isFinishing && !activity.isDestroyed) {
                            interAd.show(activity)
                        } else {
                            continuation.safeResume(ShowError)
                        }
                    } catch (e: Exception) {
                        continuation.safeResume(ShowError)
                    }
                }
            }
        }

    private fun callbackOnMain(
        activity: ComponentActivity,
        state: InterAdState,
        onLoadAdCallback: (isLoadAdSuccess: Boolean) -> Unit = {},
        onShowAdCallback: (isShowAd: Boolean) -> Unit
    ) {
        activity.launchWhenResumed {
            when (state) {
                LoadError -> onLoadAdCallback(false)
                LoadSuccess -> onLoadAdCallback(true)
                ShowError -> onShowAdCallback(false)
                ShowSuccess -> onShowAdCallback(true)
            }
        }
    }
}
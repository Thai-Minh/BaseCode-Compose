package com.looper.ads

import android.app.Activity
import android.app.Application
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.zenith.adsdk.MediationAd
import com.zenith.template_appopenad.AppOpenAdManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

private val adEligibility = AdEligibilityState()
val isSupportAdState: StateFlow<Boolean> get() = adEligibility.isEligible
val isSupportAds get() = isSupportAdState.value

private var appOpenAdObservation: Job? = null

fun bindAdEligibility(
    scope: CoroutineScope,
    remoteAllowsAds: Flow<Boolean?>,
    isPremium: Flow<Boolean?>,
) {
    adEligibility.bind(
        scope = scope,
        remoteAllowsAds = remoteAllowsAds,
        isPremium = isPremium,
    )
}

fun Application.initAd(
    fetchFirstRemoteConfig: suspend () -> String?,
    fetcher: (callback: (String?) -> Unit) -> Unit,
    onNetworkInitializationSuccess: () -> Unit,
    onNetworkInitializationFailed: (error: AdsInitializationError) -> Unit
) {
    AdsManager.init(this)

    initRemoteConfigFetcher(
        fetchFirstRemoteConfig = fetchFirstRemoteConfig,
        fetcher = fetcher,
        onNetworkInitializationSuccess = onNetworkInitializationSuccess,
        onNetworkInitializationFailed = onNetworkInitializationFailed
    )

//    initAppOpenAd()
}

fun Activity.initAppOpenAd() {
    if (MediationAd.getShared() != null) {
        AppOpenAdManager.attach(this, AdUnits.AppOpenAllPrice.key, false)
        configAppOpenAd()
    }
}

fun Application.initAppOpenAd() {
    if (MediationAd.getShared() != null) {
        AppOpenAdManager.attach(this, AdUnits.AppOpenAllPrice.key, false)
        configAppOpenAd()
    }
}

private fun configAppOpenAd() {
    AppOpenAdManager.setPaidListener { currencyCode, valueMicros ->
        AdjustManager.trackAdRevenue(
            revenue = valueMicros,
            currency = currencyCode,
            metadata = AdRevenueMetadata(adFormat = "app_open", placement = AdUnits.AppOpenAllPrice.key)
        )
    }

    synchronizedSupportAd()
}

private fun Application.initRemoteConfigFetcher(
    fetchFirstRemoteConfig: suspend () -> String?,
    fetcher: (callback: (String?) -> Unit) -> Unit,
    onNetworkInitializationSuccess: () -> Unit,
    onNetworkInitializationFailed: (error: AdsInitializationError) -> Unit
) {
    MediationAd.create(this@initRemoteConfigFetcher, object : MediationAd.Listener {
        override fun onInitializeSuccess() {
//            AdjustManager.trackEvent(ADJUST_APPLOVIN)
            onNetworkInitializationSuccess()
        }

        override fun onInitializeFail(error: MediationAd.InitializationError?) {
            val initializationError = when (error) {
                MediationAd.InitializationError.JSON_CONFIG_NULL -> {
                    AdsInitializationError.JSON_CONFIG_NULL
                }

                else -> AdsInitializationError.UNKNOWN
            }
            onNetworkInitializationFailed(initializationError)
        }

        override fun onNetworkInitializationComplete(
            networkName: String?,
            success: Boolean,
            error: String?
        ) {

        }
    })

    ProcessLifecycleOwner.get().lifecycleScope.launch(context = Dispatchers.IO) {
        val json = fetchFirstRemoteConfig()

        AdsManager.checkRemoteConfig(jsonString = json)

        if (json != null) MediationAd.reloadJsonConfigWithFirebase(json)

        MediationAd.setRemoteConfigFetcher { callback ->
            fetcher { jsonConfig ->
                AdsManager.checkRemoteConfig(jsonConfig)

                callback.onConfigLoaded(jsonConfig)
            }
        }
    }
}

fun synchronizedSupportAd() {
    // Attaching the SDK can reset its flag even when eligibility has not changed.
    updateSupportAppOpenAd(supportAds = isSupportAds)

    if (appOpenAdObservation != null) {
        return
    }

    appOpenAdObservation = ProcessLifecycleOwner.get().lifecycleScope.launch {
        isSupportAdState.collect { supportAds ->
            updateSupportAppOpenAd(supportAds = supportAds)
        }
    }
}

fun updateSupportAppOpenAd(supportAds: Boolean = isSupportAds) {
    AppOpenAdManager.setSupportAds(supportAds)
}
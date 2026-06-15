package com.looper.ads

import android.app.Activity
import android.app.Application
import androidx.lifecycle.asLiveData
import com.zenith.adsdk.MediationAd
import com.zenith.template_appopenad.AppOpenAdManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

val isSupportAdState = MutableStateFlow(false)
val isSupportAds get() = isSupportAdState.value

fun Application.initAd(
    isSupportAds: Boolean,
    fetchFirstRemoteConfig: suspend () -> String?,
    fetcher: (callback: (String?) -> Unit) -> Unit,
    onNetworkInitializationSuccess: () -> Unit,
    onNetworkInitializationFailed: (error: MediationAd.InitializationError?) -> Unit
) {
    AdsManager.init(this)

    setSupportAd(supportAds = isSupportAds)

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
            currency = currencyCode
        )
    }

    synchronizedSupportAd()
}

private fun Application.initRemoteConfigFetcher(
    fetchFirstRemoteConfig: suspend () -> String?,
    fetcher: (callback: (String?) -> Unit) -> Unit,
    onNetworkInitializationSuccess: () -> Unit,
    onNetworkInitializationFailed: (error: MediationAd.InitializationError?) -> Unit
) {
    MediationAd.create(this@initRemoteConfigFetcher, object : MediationAd.Listener {
        override fun onInitializeSuccess() {
            AdjustManager.trackEvent(ADJUST_APPLOVIN)
            onNetworkInitializationSuccess()
        }

        override fun onInitializeFail(error: MediationAd.InitializationError?) {
            onNetworkInitializationFailed(error)
        }

        override fun onNetworkInitializationComplete(
            networkName: String?,
            success: Boolean,
            error: String?
        ) {

        }
    })

    CoroutineScope(Dispatchers.IO).launch {
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
    isSupportAdState.asLiveData().observeForever {
        updateSupportAppOpenAd(it)
    }
}

fun setSupportAd(supportAds: Boolean) {
    isSupportAdState.value = supportAds
//    isSupportAdState.value = false
}

fun updateSupportAppOpenAd(supportAds: Boolean = isSupportAds) {
    AppOpenAdManager.setSupportAds(supportAds)
}
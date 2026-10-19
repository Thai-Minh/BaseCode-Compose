package com.looper.base

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.multidex.MultiDexApplication
import com.looper.ads.AdjustManager
import com.looper.ads.AdsDataPreference
import com.looper.ads.AdsInitializationError
import com.looper.ads.bindAdEligibility
import com.looper.ads.initAd
import com.looper.base.di.appModule
import com.looper.base.di.networkModule
import com.looper.base.di.notificationModule
import com.looper.base.di.repositoryModule
import com.looper.base.di.viewModelModule
import com.looper.iap.BillingManagerImpl
import com.looper.iap.OrigoIapSdk
import com.looper.iap.model.IapConfig
import com.looper.iap.model.TrustPlayPurchaseVerifier
import com.looper.base.utils.firebase.FRRemoteConfig
import com.looper.base.utils.isNetworkAvailable
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class MyApplication : MultiDexApplication(), DefaultLifecycleObserver {
    companion object {
        lateinit var instance: MyApplication
            private set

        var isAppInForeground: Boolean = false
    }

    lateinit var iapManager: BillingManagerImpl
        private set

    override fun onCreate() {
        super<MultiDexApplication>.onCreate()

        instance = this

        com.looper.base.tracking.AdRevenueAnalytics.register(application = this)
        AdjustManager.initAdjustSDK(application = this, isDebug = BuildConfig.DEBUG)

        AdsDataPreference.init(this)

        FRRemoteConfig.fetchAllConfigs()
        // TEST ONLY: TrustPlayPurchaseVerifier trusts Play client results. Replace with a
        // server-backed PurchaseVerifier before shipping or granting valuable entitlements.
        iapManager = OrigoIapSdk.create(
            context = this,
            config = IapConfig(
                catalog = PlayIapCatalog.catalog,
                purchaseVerifier = TrustPlayPurchaseVerifier,
            ),
        )

        bindAdEligibility(
            scope = ProcessLifecycleOwner.get().lifecycleScope,
            remoteAllowsAds = FRRemoteConfig.fetchShowAdsWhenReview,
            isPremium = iapManager.isPremiumFlow,
        )

        startKoin {
            androidLogger()
            androidContext(this@MyApplication)
            modules(appModule, networkModule, repositoryModule, viewModelModule, notificationModule)
        }

        ProcessLifecycleOwner.get().lifecycleScope.launch {
            FRRemoteConfig.fetchShowAdsWhenReview
                .filterNotNull()
                .first()

            initAds()
        }
    }

    private fun initAds() {
        val remoteConfigKey = BuildConfig.REMOTE_CONFIG_ADS_KEY

        // init & fetch config mediation from Remote Config
        initAd(
            fetchFirstRemoteConfig = {
                FRRemoteConfig.fetchAdsRemoteConfig(key = remoteConfigKey)
            },
            fetcher = { callback ->
                FRRemoteConfig.fetchAdsRemoteConfig(
                    key = remoteConfigKey,
                    onComplete = callback
                )
            },
            onNetworkInitializationSuccess = {
                FRRemoteConfig.updateStateRemoteConfigAd(value = true)
            },
            onNetworkInitializationFailed = { error ->
                if (error != AdsInitializationError.JSON_CONFIG_NULL || !this.isNetworkAvailable()) {
                    FRRemoteConfig.updateStateRemoteConfigAd(value = true)
                }
            }
        )
    }
}
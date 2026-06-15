package com.looper.base

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.multidex.MultiDexApplication
import com.looper.ads.AdjustManager
import com.looper.ads.AdsDataPreference
import com.looper.ads.initAd
import com.looper.base.di.appModule
import com.looper.base.di.networkModule
import com.looper.base.di.notificationModule
import com.looper.base.di.repositoryModule
import com.looper.base.di.viewModelModule
import com.looper.base.iap.core.BillingManagerImpl
import com.looper.base.utils.firebase.FRRemoteConfig
import com.looper.base.utils.isNetworkAvailable
import com.zenith.adsdk.MediationAd
import kotlinx.coroutines.MainScope
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

    override fun onCreate() {
        super<MultiDexApplication>.onCreate()

        instance = this

        AdjustManager.initAdjustSDK(application = this, isDebug = BuildConfig.DEBUG)

        AdsDataPreference.init(this)

        FRRemoteConfig.fetchAllConfigs()
        BillingManagerImpl.create(this)

        startKoin {
            androidLogger()
            androidContext(this@MyApplication)
            modules(appModule, networkModule, repositoryModule, viewModelModule, notificationModule)
        }

        MainScope().launch {
            val isShowAdWhenReview = FRRemoteConfig.fetchShowAdsWhenReview
                .filterNotNull()
                .first()

            initAds(isShowAdWhenReview = isShowAdWhenReview)
        }
    }

    private fun initAds(isShowAdWhenReview: Boolean) {
        val remoteConfigKey = BuildConfig.REMOTE_CONFIG_ADS_KEY

        val isSupportAd = if (!isShowAdWhenReview) {
            false
        } else {
            !BillingManagerImpl.getInstance().hasPremium()
        }

        // init & fetch config mediation from Remote Config
        initAd(
            isSupportAds = isSupportAd,
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
                if (error != MediationAd.InitializationError.JSON_CONFIG_NULL || !this.isNetworkAvailable()) {
                    FRRemoteConfig.updateStateRemoteConfigAd(value = true)
                }
            }
        )
    }
}
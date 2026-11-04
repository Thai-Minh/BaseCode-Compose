package com.looper.base

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.multidex.MultiDexApplication
import com.looper.ads.AdjustManager
import com.looper.ads.AdsDataPreference
import com.looper.ads.bindAdEligibility
import com.looper.ads.initAd
import com.looper.base.base.storage.firstOpen
import com.looper.base.di.appModule
import com.looper.base.di.iapModule
import com.looper.base.di.networkModule
import com.looper.base.di.notificationModule
import com.looper.base.di.repositoryModule
import com.looper.base.di.viewModelModule
import com.looper.base.iap.IAPOwner
import com.looper.base.utils.firebase.FRRemoteConfig
import com.looper.base.utils.isNetworkAvailable
import com.looper.base.utils.tracking_event.TrackEvent
import com.zenith.adsdk.MediationAd
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin

class MyApplication : MultiDexApplication(), DefaultLifecycleObserver {
    companion object {
        lateinit var instance: MyApplication
            private set

    }

    private val applicationScope =
        CoroutineScope(context = SupervisorJob() + Dispatchers.Main.immediate)

    @Volatile
    private var hasStartedOnlineInitialization = false

    override fun onCreate() {
        super<MultiDexApplication>.onCreate()

        instance = this

        AdjustManager.apply {
            initAdjustSDK(application = this@MyApplication, isDebug = BuildConfig.DEBUG)
            setPaidEventListener(TrackEvent::logAdImpressionTaichi)
        }

        ProcessLifecycleOwner.get().lifecycle.addObserver(this)

        AdsDataPreference.init(this)

        FRRemoteConfig.fetchAllConfigs()

        startKoin {
            androidLogger()
            androidContext(this@MyApplication)
            modules(
                appModule,
                networkModule,
                repositoryModule,
                viewModelModule,
                notificationModule,
                iapModule(applicationScope = applicationScope)
            )
        }

        val iapOwner = GlobalContext.get().get<IAPOwner>()
        iapOwner.start()

        bindAdEligibility(
            scope = ProcessLifecycleOwner.get().lifecycleScope,
            remoteAllowsAds = FRRemoteConfig.fetchShowAdsWhenReview,
            isPremium = iapOwner.isPremium,
        )
    }

    @Synchronized
    fun startOnlineInitialization() {
        if (hasStartedOnlineInitialization) {
            return
        }

        hasStartedOnlineInitialization = true

        FRRemoteConfig.fetchAllConfigs()

        applicationScope.launch {
            val owner = GlobalContext.get().get<IAPOwner>()

            val decision = withTimeoutOrNull(timeMillis = 10_000L) {
                combine(
                    flow = owner.isPremium,
                    flow2 = FRRemoteConfig.fetchShowAdsWhenReview,
                ) { isPremium, review ->
                    isPremium to review
                }.first { (_, review) ->
                    review != null
                }
            }

            initAds(isShowAdWhenReview = decision?.second == true && !decision.first)
        }

        applicationScope.launch(context = Dispatchers.IO) {
            val isFirstOpen = firstOpen.firstOrNull() ?: true
            if (isFirstOpen) {
                TrackEvent.logFirstOpen()
            }
        }
    }

    private fun initAds(
        isShowAdWhenReview: Boolean,
        useCloudXMediation: Boolean = false
    ) {
        val owner = GlobalContext.get().get<IAPOwner>()
        val isSupportAd = isShowAdWhenReview && !owner.isPremium.value

        if (isSupportAd) {
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
                    if (error != MediationAd.InitializationError.JSON_CONFIG_NULL || !this.isNetworkAvailable()) {
                        FRRemoteConfig.updateStateRemoteConfigAd(value = true)
                    }
                }
            )
        } else {
            FRRemoteConfig.updateStateRemoteConfigAd(value = true)
        }
    }
}
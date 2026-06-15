package com.looper.ads.helper.native_ad

import android.content.Context
import com.looper.ads.AdUnits
import com.looper.ads.AdjustManager
import com.looper.ads.isSupportAds
import com.looper.ads.safeResume
import com.zenith.adapter.AdError
import com.zenith.adapter.format.UnifiedNativeAdRender
import com.zenith.adsdk.callback.NativeAdLoadCallBack
import com.zenith.adsdk.format.NativeAdLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

enum class NativeAdType(
    val adUnitId: String,
    val adUnitBackup1: String? = null,
    val adUnitBackup2: String? = null,
    val adUnitBackup3: String? = null,
    val removable: Boolean = true,
    val collapsible: Boolean = false,
    val goneAllAfterCollapsible: Boolean = true,
    val useCardViewWrapper: Boolean = false
) {
    AdHouse(
        adUnitId = "",
    ),
    Language1(
        adUnitId = AdUnits.NativeLanguageFirst2Floor.key,
        adUnitBackup1 = AdUnits.NativeLanguageFirstMedium.key,
        adUnitBackup3 = AdUnits.NativeLanguageFirstAllPrice.key,
        useCardViewWrapper = true
    ),
    Language2(
        adUnitId = AdUnits.NativeLanguageSecond2Floor.key,
        adUnitBackup1 = AdUnits.NativeLanguageSecondMedium.key,
        adUnitBackup3 = AdUnits.NativeLanguageSecondAllPrice.key,
        useCardViewWrapper = true
    ),
    Intro1(
        adUnitId = AdUnits.NativeIntroFirstAllPrice.key,
        useCardViewWrapper = true
    ),
    Intro3(
        adUnitId = AdUnits.NativeIntroThirdAllPrice.key,
        useCardViewWrapper = true
    ),
    IntroFullScreen(
        adUnitId = AdUnits.NativeFullIntro2Floor.key,
        adUnitBackup1 = AdUnits.NativeFullIntroMedium.key
    ),
    Home(
        adUnitId = AdUnits.NativeHomeCollapsible2Floor.key,
        adUnitBackup1 = AdUnits.NativeHomeCollapsibleMedium.key,
        adUnitBackup3 = AdUnits.NativeHomeCollapsibleAllPrice.key,
        collapsible = true,
        goneAllAfterCollapsible = false
    )
}

sealed class NativeAdState {
    data object Loading : NativeAdState()
    data class Success(val adUnitId: String, val nativeAd: UnifiedNativeAdRender) : NativeAdState()
    data object Error : NativeAdState()
}

object NativeAdPreload {

    private val mainScope by lazy {
        MainScope()
    }

    private val adCache = MutableStateFlow<Map<String, NativeAdState>>(emptyMap())

    fun preload(context: Context, adType: NativeAdType) {

        if (isAlreadyPreloadAd(adType = adType)) return

        preloadInternal(context = context, adType = adType)
    }

    fun reload(context: Context, adType: NativeAdType) {
        mainScope.launch(Dispatchers.IO) {

            if (!isSupportAds) return@launch

            runLoadAd(context = context, adType = adType)
        }
    }

    fun getNativeAd(
        context: Context,
        adType: NativeAdType,
        refreshAd: Boolean
    ): Flow<NativeAdState?> {

        val isAlready = isAlreadyPreloadAd(adType)

        if ((refreshAd && isAlready) || !isAlready)
            preloadInternal(context = context, adType = adType)

        return adCache.map { map ->

            val value = map.entries.firstOrNull {
                it.key == adType.name
            }?.value

            value
        }
    }

    private fun isAlreadyPreloadAd(adType: NativeAdType): Boolean {
        val cache = queryNativeAd(adType = adType)

        return cache is NativeAdState.Loading || cache is NativeAdState.Success
    }

    private fun preloadInternal(
        context: Context,
        adType: NativeAdType
    ) {
        mainScope.launch(Dispatchers.IO) {

            if (!isSupportAds) return@launch

            updateAdState(adType = adType, state = NativeAdState.Loading)

            runLoadAd(context = context, adType = adType)
        }
    }

    private suspend fun runLoadAd(
        context: Context,
        adType: NativeAdType
    ) {
        val result = loadNativeAdInternal(context = context, adType = adType)

        val state = if (result == null) {
            NativeAdState.Error
        } else {
            NativeAdState.Success(adUnitId = result.second, nativeAd = result.first)
        }

        updateAdState(adType = adType, state = state)
    }

    private suspend fun loadNativeAdInternal(
        context: Context,
        adType: NativeAdType
    ): Pair<UnifiedNativeAdRender, String>? {
        if (!isSupportAds) return null

        val adUnitList = listOfNotNull(
            adType.adUnitId,
            adType.adUnitBackup1,
            adType.adUnitBackup2,
            adType.adUnitBackup3
        )

        return suspendCancellableCoroutine { continuation ->
            NativeAdLoader.load(context, adUnitList, object : NativeAdLoadCallBack {
                override fun onAdFailedToLoad(error: AdError) {
                    continuation.safeResume(null)

                    mainScope.launch {
                        updateAdState(adType, NativeAdState.Error)
                    }
                }

                override fun onAdLoaded(ad: UnifiedNativeAdRender, adUnitId: String) {
                    if (continuation.isActive) {
                        continuation.safeResume(ad to adUnitId)
                    } else {
                        mainScope.launch {
                            updateAdState(
                                adType = adType,
                                state = NativeAdState.Success(
                                    adUnitId = adUnitId,
                                    nativeAd = ad
                                )
                            )
                        }
                    }
                }

                override fun onPaidEvent(
                    currencyCode: String,
                    valueMicros: Long
                ) {
                    AdjustManager.trackAdRevenue(revenue = valueMicros, currency = currencyCode)
                }
            })
        }
    }

    private suspend fun updateAdState(adType: NativeAdType, state: NativeAdState) {
        withContext(Dispatchers.Main) {
            adCache.value = adCache.value.toMutableMap().apply {
                this[adType.name] = state
            }
        }
    }

    private fun queryNativeAd(adType: NativeAdType): NativeAdState? {
        return adCache.value.entries.firstOrNull { it.key == adType.name }?.value
    }

    fun removeCacheNativeAds(adType: NativeAdType) {
        adCache.value = adCache.value.toMutableMap().filterNot {
            it.key == adType.name && it.value != NativeAdState.Loading
        }
    }

}
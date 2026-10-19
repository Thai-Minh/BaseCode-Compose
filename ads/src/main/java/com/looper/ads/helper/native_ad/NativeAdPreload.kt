package com.looper.ads.helper.native_ad

import android.content.Context
import com.looper.ads.AdRevenueMetadata
import com.looper.ads.AdUnits
import com.looper.ads.AdjustManager
import com.looper.ads.isSupportAds
import com.looper.ads.safeResume
import com.zenith.adapter.AdError
import com.zenith.adapter.format.UnifiedNativeAdRender
import com.zenith.adsdk.MediationAd
import com.zenith.adsdk.callback.NativeAdLoadCallBack
import com.zenith.adsdk.format.NativeAdLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

enum class NativeAdType(
    val adUnitId: String,
    val removable: Boolean = true,
    val goneAllAfterCollapsible: Boolean = true,
    val useCardViewWrapper: Boolean = false
) {
    AdHouse(
        adUnitId = "",
    ),
    Language1(
        adUnitId = AdUnits.NativeLanguage1.key,
        useCardViewWrapper = true
    ),
    Language2(
        adUnitId = AdUnits.NativeLanguage2.key,
        useCardViewWrapper = true
    ),
    Intro1(
        adUnitId = AdUnits.NativeIntro1.key,
        useCardViewWrapper = true
    ),
    Intro3(
        adUnitId = AdUnits.NativeIntro3.key,
        useCardViewWrapper = true
    ),
    IntroFullScreen(
        adUnitId = AdUnits.NativeFullIntro2.key,
    ),
    Home(
        adUnitId = AdUnits.NativeHomeCollapsible.key,
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
        mainScope.launch {
            ensureLoadStarted(context = context, adType = adType)
        }
    }

    suspend fun preloadAndAwait(
        context: Context,
        adType: NativeAdType,
    ): Boolean {
        if (!isSupportAds) return false

        ensureLoadStarted(context = context, adType = adType)
        return awaitPreloadResult(adType = adType)
    }

    fun reload(context: Context, adType: NativeAdType) {
        mainScope.launch {
            if (!isSupportAds) {
                updateAdState(adType = adType, state = NativeAdState.Error)
                return@launch
            }
            if (queryNativeAd(adType) is NativeAdState.Loading) return@launch

            startLoad(context = context, adType = adType)
        }
    }

    fun getNativeAd(context: Context, adType: NativeAdType, refreshAd: Boolean): Flow<NativeAdState?> {
        return adCache
            .map { map -> map[adType.name] }
            .onStart {
                ensureLoadStarted(context = context, adType = adType)
            }
    }

    /**
     * Runs on Main so checking the cache and publishing Loading are one serialized operation.
     * This guarantees that preload(), preloadAndAwait() and getNativeAd() cannot start two SDK
     * requests for the same placement at the same time.
     */
    private suspend fun ensureLoadStarted(
        context: Context,
        adType: NativeAdType,
    ) = withContext(Dispatchers.Main.immediate) {
        if (!isSupportAds) {
            updateAdState(adType = adType, state = NativeAdState.Error)
            return@withContext
        }

        when (queryNativeAd(adType = adType)) {
            is NativeAdState.Loading,
            is NativeAdState.Success -> Unit

            else -> startLoad(context = context, adType = adType)
        }
    }

    private fun startLoad(context: Context, adType: NativeAdType) {
        updateAdState(adType = adType, state = NativeAdState.Loading)
        mainScope.launch(Dispatchers.IO) {
            runLoadAdSafely(context = context, adType = adType)
        }
    }

    private suspend fun runLoadAdSafely(
        context: Context,
        adType: NativeAdType,
    ): Boolean {
        return try {
            runLoadAd(context = context, adType = adType)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            updateAdState(adType = adType, state = NativeAdState.Error)
            false
        }
    }

    private suspend fun runLoadAd(
        context: Context,
        adType: NativeAdType,
    ): Boolean {
        val result = loadNativeAdInternal(context = context, adType = adType)
        val state = if (result == null) {
            NativeAdState.Error
        } else {
            NativeAdState.Success(adUnitId = result.second, nativeAd = result.first)
        }

        updateAdState(adType = adType, state = state)
        return result != null
    }

    private suspend fun awaitPreloadResult(adType: NativeAdType): Boolean {
        val state = adCache
            .map { cache -> cache[adType.name] }
            .first { state ->
                state is NativeAdState.Success || state is NativeAdState.Error
            }

        return state is NativeAdState.Success
    }

    private suspend fun loadNativeAdInternal(
        context: Context,
        adType: NativeAdType,
    ): Pair<UnifiedNativeAdRender, String>? {
        if (!isSupportAds || MediationAd.getShared() == null) return null

        return suspendCancellableCoroutine { continuation ->
            NativeAdLoader.load(context, adType.adUnitId, object : NativeAdLoadCallBack {
                override fun onAdFailedToLoad(error: AdError) {
                    if (continuation.isActive) {
                        continuation.safeResume(null)
                    } else {
                        updateAdState(adType = adType, state = NativeAdState.Error)
                    }
                }

                override fun onAdLoaded(ad: UnifiedNativeAdRender, adUnitId: String) {
                    if (continuation.isActive) {
                        continuation.safeResume(ad to adUnitId)
                    } else {
                        updateAdState(
                            adType = adType,
                            state = NativeAdState.Success(
                                adUnitId = adUnitId,
                                nativeAd = ad,
                            ),
                        )
                    }
                }

                override fun onPaidEvent(
                    currencyCode: String,
                    valueMicros: Long,
                ) {
                    AdjustManager.trackAdRevenue(
                        revenue = valueMicros,
                        currency = currencyCode,
                        metadata = AdRevenueMetadata(adFormat = "native", placement = adType.adUnitId),
                    )
                }
            })
        }
    }

    private fun updateAdState(adType: NativeAdType, state: NativeAdState) {
        adCache.update { cache ->
            cache + (adType.name to state)
        }
    }

    private fun queryNativeAd(adType: NativeAdType): NativeAdState? {
        return adCache.value[adType.name]
    }

    fun removeCacheNativeAds(adType: NativeAdType) {
        adCache.update { cache ->
            if (cache[adType.name] is NativeAdState.Loading) cache else cache - adType.name
        }
    }
}
package com.looper.ads.helper.interstitial

import android.content.Context
import com.looper.ads.isSupportAds
import com.looper.ads.safeResume
import com.zenith.adapter.AdError
import com.zenith.adsdk.callback.InterstitialAdLoadCallback
import com.zenith.adsdk.format.InterstitialAd
import com.zenith.adsdk.utils.MediationUtils
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

sealed class InterstitialAdState {
    data class Success(val interstitialAd: InterstitialAd) : InterstitialAdState()
    data object Error : InterstitialAdState()
    data object Loading : InterstitialAdState()
}

object InterstitialAdPreload {

    private val mainScope by lazy {
        MainScope()
    }

    private val adCache = MutableStateFlow<Map<String, InterstitialAdState>>(emptyMap())

    fun preload(context: Context, adType: InterstitialType, onLoadAdCallback: ((Boolean) -> Unit) = { }) {
        if (isAlreadyPreloadAd(adType)) {
            return
        }

        preloadAd(context = context, adType = adType, onLoadAdCallback = onLoadAdCallback)
    }

    suspend fun getPreloadInterstitialAd(context: Context, adType: InterstitialType): InterstitialAd? {
        if (isAlreadyPreloadAd(adType))
            return getInterstitialAdFromPreload(adType)

        return loadInterstitialAdInternal(context, adType)
    }

    fun isCompletePreloadAd(adUnitId: String): Boolean {
        val cache = adCache.value[adUnitId]
        return cache is InterstitialAdState.Success || cache is InterstitialAdState.Error
    }

    private fun isAlreadyPreloadAd(adType: InterstitialType): Boolean {
        val cache = queryInterAd(adType = adType)
        return cache is InterstitialAdState.Success || cache is InterstitialAdState.Loading
    }

    private fun queryInterAd(adType: InterstitialType): InterstitialAdState? {
        return adCache.value.entries.firstOrNull { it.key == adType.name }?.value
    }

    private fun preloadAd(context: Context, adType: InterstitialType, onLoadAdCallback: (Boolean) -> Unit) {
        mainScope.launch {
            val ids = listOf(adType.adUnitId, adType.backup1, adType.backup2)

            if (!isSupportAds || ids.all { !MediationUtils.isFullScreenAdDisplayIntervalAccepted(it) }) {
                onLoadAdCallback(false)
                return@launch
            }

            updateAdState(adType = adType, state = InterstitialAdState.Loading)

            val interstitialAd = loadInterstitialAdInternal(context = context, adType = adType)

            onLoadAdCallback(interstitialAd != null)

            val state = if (interstitialAd == null)
                InterstitialAdState.Error
            else
                InterstitialAdState.Success(interstitialAd)

            updateAdState(adType = adType, state = state)
        }
    }

    private suspend fun loadInterstitialAdInternal(
        context: Context,
        adType: InterstitialType
    ): InterstitialAd? {
        val adUnitIds = listOfNotNull(adType.adUnitId, adType.backup1, adType.backup2)

        for (adUnitId in adUnitIds) {
            if (!isSupportAds) return null

            val result = suspendCancellableCoroutine {
                InterstitialAd.load(context, adUnitId, object : InterstitialAdLoadCallback {
                    override fun onAdFailedToLoad(error: AdError) {
                        it.safeResume(null)
                    }

                    override fun onAdLoaded(ad: InterstitialAd, adUnit: String) {
                        it.safeResume(ad)
                    }
                })
            }

            if (result != null) return result
        }
        return null
    }

    private suspend fun getInterstitialAdFromPreload(adType: InterstitialType): InterstitialAd? {
        var result: InterstitialAd? = null
        adCache.map { map ->
            map.filter {
                it.key == adType.name
            }.values.first()
        }
            .collectWhile { state ->
                when (state) {
                    is InterstitialAdState.Error -> {
                        result = null
                        false
                    }

                    is InterstitialAdState.Success -> {
                        result = state.interstitialAd
                        false
                    }

                    else -> true
                }
            }
        return result
    }

    private fun updateAdState(adType: InterstitialType, state: InterstitialAdState) {
        adCache.value = adCache.value.toMutableMap().apply {
            this[adType.name] = state
        }
    }

    fun removeCacheInterAds(adType: InterstitialType) {
        adCache.value = adCache.value.toMutableMap().filterNot {
            it.key == adType.name && it.value !is InterstitialAdState.Loading
        }
    }
}

internal suspend inline fun <T> Flow<T>.collectWhile(crossinline predicate: suspend (value: T) -> Boolean) {
    val collector = FlowCollector<T> { value ->
        if (!predicate(value)) {
            throw IllegalArgumentException("Ended")
        }
    }
    try {
        collect(collector)
    } catch (_: IllegalArgumentException) {

    }
}
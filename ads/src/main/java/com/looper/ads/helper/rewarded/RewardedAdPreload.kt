package com.looper.ads.helper.rewarded

import android.content.Context
import com.looper.ads.helper.interstitial.collectWhile
import com.looper.ads.isSupportAds
import com.looper.ads.safeResume
import com.zenith.adapter.AdError
import com.zenith.adsdk.callback.RewardedAdLoadCallBack
import com.zenith.adsdk.format.RewardedAd
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

enum class RewardedAdType(
    val adUnitId: String,
    val adUnitBackup1: String? = null,
    val adUnitBackup2: String? = null,
    val removable: Boolean = true
) {
    RewardedUnlockContent(
        adUnitId = "",
        adUnitBackup1 = "",
        adUnitBackup2 = "",
    )
}

sealed class RewardedAdState {
    data class Success(val rewardedAd: RewardedAd) : RewardedAdState()
    data object Error : RewardedAdState()
    data object Loading : RewardedAdState()
}

object RewardedAdPreload {
    private val mainScope by lazy {
        MainScope()
    }

    private val adCache = MutableStateFlow<Map<String, RewardedAdState>>(emptyMap())

    fun preload(
        context: Context,
        adType: RewardedAdType,
        onLoadAdCallback: ((Boolean) -> Unit) = { }
    ) {
        if (isAlreadyPreloadAd(adType)) {
            return
        }

        preloadAd(context = context, adType = adType, onLoadAdCallback = onLoadAdCallback)
    }

    suspend fun getPreloadRewardedAd(context: Context, adType: RewardedAdType): RewardedAd? {
        if (isAlreadyPreloadAd(adType))
            return getRewardedAdFromPreload(adType)

        return loadRewardedAdInternal(context = context, adType = adType)
    }

    fun isCompletePreloadAd(adType: RewardedAdType): Boolean {
        val cache = queryNativeAd(adType = adType)

        return cache is RewardedAdState.Success || cache is RewardedAdState.Error
    }

    private fun isAlreadyPreloadAd(adType: RewardedAdType): Boolean {
        val cache = queryNativeAd(adType = adType)

        return cache is RewardedAdState.Success || cache is RewardedAdState.Loading
    }

    private fun preloadAd(
        context: Context,
        adType: RewardedAdType,
        onLoadAdCallback: (Boolean) -> Unit
    ) {
        mainScope.launch {

            if (!isSupportAds) {
                onLoadAdCallback(false)
                return@launch
            }

            updateAdState(adType = adType, state = RewardedAdState.Loading)

            val rewardedAd = loadRewardedAdInternal(context = context, adType = adType)

            onLoadAdCallback(rewardedAd != null)

            val state = if (rewardedAd == null)
                RewardedAdState.Error
            else
                RewardedAdState.Success(rewardedAd)

            updateAdState(adType = adType, state = state)
        }
    }

    private suspend fun loadRewardedAdInternal(
        context: Context,
        adType: RewardedAdType
    ): RewardedAd? {
        if (!isSupportAds) return null

        val adUnitList = listOfNotNull(
            adType.adUnitId,
            adType.adUnitBackup1,
            adType.adUnitBackup2
        )

        for (id in adUnitList) {
            val result = suspendCancellableCoroutine<RewardedAd?> { continuation ->
                RewardedAd.load(context, id, object : RewardedAdLoadCallBack {
                    override fun onAdFailedToLoad(error: AdError) {
                        continuation.safeResume(null)
                    }

                    override fun onAdLoaded(ad: RewardedAd, adUnit: String) {
                        continuation.safeResume(ad)
                    }
                })
            }

            if (result != null) return result
        }

        return null
    }

    private suspend fun getRewardedAdFromPreload(adType: RewardedAdType): RewardedAd? {
        var result: RewardedAd? = null
        adCache.map { map ->
            map.filter {
                it.key == adType.name
            }.values.first()
        }
            .collectWhile { state ->
                when (state) {
                    is RewardedAdState.Error -> {
                        result = null
                        false
                    }

                    is RewardedAdState.Success -> {
                        result = state.rewardedAd
                        false
                    }

                    else -> true
                }
            }
        return result
    }

    private fun updateAdState(adType: RewardedAdType, state: RewardedAdState) {
        adCache.value = adCache.value.toMutableMap().apply {
            this[adType.name] = state
        }
    }

    private fun queryNativeAd(adType: RewardedAdType): RewardedAdState? {
        return adCache.value.entries.firstOrNull { it.key == adType.name }?.value
    }

    fun removeCacheInterAds(adType: RewardedAdType) {
        adCache.value = adCache.value.toMutableMap().filterNot {
            it.key == adType.name && it.value !is RewardedAdState.Loading
        }
    }
}
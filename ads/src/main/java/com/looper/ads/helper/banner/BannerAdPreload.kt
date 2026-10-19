package com.looper.ads.helper.banner

import android.content.Context
import android.view.View
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.ui.unit.LayoutDirection
import com.looper.ads.getWidthScreen
import com.looper.ads.helper.banner.data.BannerAdSize
import com.looper.ads.helper.banner.data.BannerAdType
import com.looper.ads.helper.banner.data.BannerState
import com.looper.ads.helper.banner.data.getAdSize
import com.looper.ads.helper.banner.data.resolveBannerAdSize
import com.looper.ads.isSupportAds
import com.zenith.adapter.AdSize
import com.zenith.adapter.Collapsible
import com.zenith.adsdk.MediationAd
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

object BannerAdPreload {
    private val bannerAdCache = MutableStateFlow<Map<BannerAdType, BannerState>>(emptyMap())
    private val requestCounter = AtomicLong()
    private val activeRequests = ConcurrentHashMap<BannerAdType, Long>()
    private val activeSignatures = ConcurrentHashMap<BannerAdType, String>()

    fun preload(
        context: Context,
        cacheAdType: BannerAdType,
        padding: PaddingValues = PaddingValues()
    ) {
        if (!isSupportAds) return

        val effectiveBannerAdSize = cacheAdType.resolveBannerAdSize()

        val effectiveAdSize = getAdSize(
            context = context,
            bannerAdSize = effectiveBannerAdSize,
            padding = padding,
        )

        val requestSignature = createRequestSignature(
            cacheAdType = cacheAdType,
            bannerAdSize = effectiveBannerAdSize,
            adSize = effectiveAdSize,
        )

        val oldState = queryBannerAdView(cacheAdType)

        if (
            activeSignatures[cacheAdType] == requestSignature &&
            (oldState is BannerState.Loading && oldState.size == effectiveAdSize || oldState is BannerState.Success && oldState.size == effectiveAdSize)
        ) {
            return
        }

        if (oldState is BannerState.Success) {
            oldState.view.destroy()
        }

        val requestId = requestCounter.incrementAndGet()

        activeRequests[cacheAdType] = requestId
        activeSignatures[cacheAdType] = requestSignature

        addOrReplace(
            cacheAdType = cacheAdType,
            bannerState = BannerState.Loading(
                size = effectiveAdSize,
                bannerAdSize = effectiveBannerAdSize,
            ),
        )

        try {
            loadAd(
                context = context,
                adUnitId = cacheAdType.adUnitId,
                adSize = effectiveAdSize,
            ) { state ->
                if (activeRequests[cacheAdType] != requestId) {
                    if (state is BannerState.Success)
                        state.view.destroy()

                    return@loadAd
                }

                addOrReplace(cacheAdType = cacheAdType, bannerState = state)
            }
        } catch (_: Exception) {
            if (activeRequests[cacheAdType] == requestId) {
                addOrReplace(cacheAdType = cacheAdType, bannerState = BannerState.Error)
            }
        }
    }

    suspend fun preloadAndAwait(
        context: Context,
        cacheAdType: BannerAdType,
        padding: PaddingValues = PaddingValues(),
    ): Boolean {
        if (!isSupportAds) return false

        val expectedSize = getAdSize(
            context = context,
            bannerAdSize = cacheAdType.resolveBannerAdSize(),
            padding = padding,
        )
        return try {
            withContext(Dispatchers.Main.immediate) {
                preload(
                    context = context,
                    cacheAdType = cacheAdType,
                    padding = padding,
                )
            }

            !(!isSupportAds || queryBannerAdView(cacheAdType) == null) && awaitPreloadResult(cacheAdType, expectedSize)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            withContext(Dispatchers.Main.immediate) {
                if (queryBannerAdView(cacheAdType) is BannerState.Loading) {
                    addOrReplace(cacheAdType, BannerState.Error)
                }
            }
            false
        }
    }

    fun getAdView(
        context: Context,
        cacheAdType: BannerAdType,
        padding: PaddingValues = PaddingValues()
    ): Flow<BannerState> {
        preload(context = context, cacheAdType = cacheAdType, padding = padding)

        return bannerAdCache.map { map ->
            map.entries.firstOrNull { it.key == cacheAdType }?.value ?: BannerState.Error
        }
    }

    fun removeAdView(cacheAdType: BannerAdType) {
        activeRequests.remove(cacheAdType)
        activeSignatures.remove(cacheAdType)

        val oldView = queryBannerAdView(cacheAdType) ?: return

        if (oldView is BannerState.Success) {
            oldView.view.destroy()
        }

        bannerAdCache.update { cache -> cache - cacheAdType }
    }

    private fun createRequestSignature(
        cacheAdType: BannerAdType,
        bannerAdSize: BannerAdSize,
        adSize: AdSize,
    ): String {
        val collapsible = MediationAd.getShared()
            ?.getAdUnitCollapsible(cacheAdType.adUnitId)
            ?: Collapsible.NONE

        return "${bannerAdSize.remoteValue}:${adSize.width}x${adSize.height}:$collapsible"
    }

    private fun addOrReplace(cacheAdType: BannerAdType, bannerState: BannerState) {
        bannerAdCache.update { cache ->
            cache + (cacheAdType to bannerState)
        }
    }

    private fun queryBannerAdView(cacheAdType: BannerAdType): BannerState? {
        return bannerAdCache.value.entries.firstOrNull {
            it.key == cacheAdType
        }?.value
    }

    private suspend fun awaitPreloadResult(
        cacheAdType: BannerAdType,
        expectedSize: AdSize,
    ): Boolean {
        val state = bannerAdCache
            .map { cache -> cache[cacheAdType] }
            .first { state ->
                state is BannerState.Error || state is BannerState.Success && state.size == expectedSize
            }

        return state is BannerState.Success
    }

    private fun getAdSize(
        context: Context,
        bannerAdSize: BannerAdSize,
        padding: PaddingValues
    ): AdSize {
        val layoutDirection = context.resources.configuration.layoutDirection.toLayoutDirection()

        val paddingStart = padding.calculateStartPadding(layoutDirection).value.toInt()
        val paddingListEnd = padding.calculateEndPadding(layoutDirection).value.toInt()

        return bannerAdSize.getAdSize(
            width = context.getWidthScreen() - (paddingStart + paddingListEnd)
        )
    }

    private fun Int.toLayoutDirection(): LayoutDirection {
        return when (this) {
            View.LAYOUT_DIRECTION_LTR -> LayoutDirection.Ltr
            View.LAYOUT_DIRECTION_RTL -> LayoutDirection.Rtl
            else -> LayoutDirection.Ltr
        }
    }
}
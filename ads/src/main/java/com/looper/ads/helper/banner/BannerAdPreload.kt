package com.looper.ads.helper.banner

import android.content.Context
import android.view.View
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.ui.unit.LayoutDirection
import com.looper.ads.helper.banner.data.BannerAdType
import com.looper.ads.helper.banner.data.BannerState
import com.looper.ads.helper.banner.data.getAdSize
import com.looper.ads.helper.banner.data.getBundle
import com.looper.ads.getWidthScreen
import com.looper.ads.isSupportAds
import com.zenith.adapter.AdSize
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlin.collections.filter

object BannerAdPreload {
    private val bannerAdCache = MutableStateFlow<Map<BannerAdType, BannerState>>(emptyMap())

    fun preload(
        context: Context,
        cacheAdType: BannerAdType,
        paddingList: PaddingValues = PaddingValues()
    ) {
        if (!isSupportAds) return

        val oldBannerAdView = queryBannerAdView(cacheAdType)

        if (oldBannerAdView is BannerState.Loading || oldBannerAdView is BannerState.Success)
            return

        addOrReplace(cacheAdType = cacheAdType, bannerState = BannerState.Loading)

        loadAd(
            context = context,
            adUnitId = cacheAdType.adUnitId,
            adSize = getAdSize(
                context = context,
                type = cacheAdType,
                paddingList = paddingList
            ),
            extras = cacheAdType.bannerAdSize.getBundle()
        ) {
            addOrReplace(cacheAdType = cacheAdType, bannerState = it)
        }
    }

    fun getAdView(
        context: Context,
        cacheAdType: BannerAdType,
        paddingList: PaddingValues = PaddingValues()
    ): Flow<BannerState> {
        val oldBannerAdView = queryBannerAdView(cacheAdType)

        if (oldBannerAdView == null || oldBannerAdView is BannerState.Error)
            preload(context = context, cacheAdType = cacheAdType, paddingList = paddingList)

        return bannerAdCache.map { map ->
            val value = map.entries.firstOrNull { it.key == cacheAdType }?.value
            value ?: BannerState.Error
        }
    }

    fun removeAdView(cacheAdType: BannerAdType) {
        val oldView = queryBannerAdView(cacheAdType)

        if (oldView == null || oldView is BannerState.Loading) return

        if (oldView is BannerState.Success)
            oldView.view.destroy()

        bannerAdCache.value = bannerAdCache.value.filter { it.key != cacheAdType }.toMutableMap()
    }

    private fun addOrReplace(cacheAdType: BannerAdType, bannerState: BannerState) {
        bannerAdCache.value = buildMap {
            val filters = bannerAdCache.value.filter { it.key != cacheAdType }

            putAll(filters)
            put(cacheAdType, bannerState)
        }
    }

    private fun queryBannerAdView(cacheAdType: BannerAdType): BannerState? {
        return bannerAdCache.value.entries.firstOrNull {
            it.key == cacheAdType
        }?.value
    }

    private fun getAdSize(
        context: Context,
        type: BannerAdType,
        paddingList: PaddingValues
    ): AdSize {
        val bannerPadding = type.bannerPadding
        val layoutDirection = context.resources.configuration.layoutDirection.toLayoutDirection()

        val paddingStart = bannerPadding.calculateStartPadding(layoutDirection).value.toInt()

        val paddingEnd = bannerPadding.calculateEndPadding(layoutDirection).value.toInt()

        val paddingListStart = paddingList.calculateStartPadding(layoutDirection).value.toInt()
        val paddingListEnd = paddingList.calculateEndPadding(layoutDirection).value.toInt()

        return type.bannerAdSize.getAdSize(context.getWidthScreen() - (paddingStart + paddingEnd + paddingListStart + paddingListEnd))
    }

    private fun Int.toLayoutDirection(): LayoutDirection {
        return when (this) {
            View.LAYOUT_DIRECTION_LTR -> LayoutDirection.Ltr
            View.LAYOUT_DIRECTION_RTL -> LayoutDirection.Rtl
            else -> LayoutDirection.Ltr
        }
    }
}
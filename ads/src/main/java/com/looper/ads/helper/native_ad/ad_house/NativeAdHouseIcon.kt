package com.looper.ads.helper.native_ad.ad_house

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.looper.ads.R
import com.looper.ads.helper.native_ad.NativeAdOnlyIcon
import com.looper.ads.helper.native_ad.NativeAdType
import com.looper.ads.isSupportAds

enum class RippleType(val resId: Int) {
    Circle(resId = R.drawable.selectable_circle_transparent),
    Rectangle(resId = R.drawable.selectable_rectangle_transparent),
    Half(resId = R.drawable.selectable_half_transparent)
}

@Composable
fun SmallNativeAdHouseIcon(
    sizeBound: Dp = 48.dp,
    rippleType: RippleType = RippleType.Half
) {
    if (!isSupportAds) return

    Box(
        modifier = Modifier
            .size(sizeBound)
    ) {
        NativeAdOnlyIcon(
            modifier = Modifier.fillMaxSize(),
            adType = NativeAdType.AdHouse,
            rippleId = rippleType.resId
        )
    }
}

@Composable
fun BigNativeAdHouseIcon(
    sizeBound: Dp = 56.dp,
    rippleType: RippleType = RippleType.Half
) {
    if (!isSupportAds) return

    Box(
        modifier = Modifier
            .size(sizeBound)
    ) {
        NativeAdOnlyIcon(
            modifier = Modifier.fillMaxSize(),
            adType = NativeAdType.AdHouse,
            iconSize = DpSize(48.dp, 48.dp),
            rippleId = rippleType.resId
        )
    }
}

@Composable
fun MediumNativeAdHouseIcon(
    sizeBound: Dp = 48.dp,
    rippleType: RippleType = RippleType.Half
) {
    if (!isSupportAds) return

    Box(
        modifier = Modifier
            .size(sizeBound)
    ) {
        NativeAdOnlyIcon(
            modifier = Modifier.fillMaxSize(),
            adType = NativeAdType.AdHouse,
            iconSize = DpSize(36.dp, 36.dp),
            rippleId = rippleType.resId
        )
    }
}
package com.looper.ads.helper.native_ad

import android.animation.ValueAnimator
import android.app.Application
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import androidx.appcompat.widget.AppCompatButton
import androidx.cardview.widget.CardView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.looper.ads.R
import com.looper.ads.dp2Px
import com.looper.ads.helper.native_ad.ui.BoxSpace
import com.looper.ads.helper.native_ad.ui.NativeAdLifecycle
import com.looper.ads.helper.native_ad.ui.NativeSpace
import com.looper.ads.isSupportAds
import com.zenith.adapter.ui.UnifiedNativeAdViewBinder

@Composable
fun NativeAdOnlyIcon(
    modifier: Modifier = Modifier,
    iconSize: DpSize? = DpSize(24.dp, 24.dp),
    adType: NativeAdType,
    space: NativeSpace = NativeSpace(),
    rippleId: Int,
) {
    if (!isSupportAds) return

    val app = LocalContext.current.applicationContext as Application
    val viewModel: NativeAdViewModel = viewModel(
        key = adType.adUnitId,
        factory = NativeAdViewModelFactory(app = app, type = adType)
    )

    NativeAdLifecycle(viewModel = viewModel)

    val nativeAd by viewModel.nativeAd.collectAsStateWithLifecycle(initialValue = NativeAdState.Loading)

    if (nativeAd is NativeAdState.Success) {
        BoxSpace(space = space) {
            val ad = (nativeAd as NativeAdState.Success).nativeAd

            AndroidView(
                factory = {
                    FrameLayout(it)
                },
                modifier = modifier,
                update = { parent ->
                    parent.removeAllViewAndAnimation()

                    val binder = UnifiedNativeAdViewBinder.Builder(
                        parent.context,
                        R.layout.native_ad_only_icon_ad_house
                    )
                        .setCallToActionButton2ResId(R.id.native_call_to_action)
                        .setIconResId(R.id.native_icon)
                        .build()

                    val view = ad.render(binder)

                    if (iconSize != null) {
                        view.findViewById<CardView>(R.id.native_icon_bound).apply {
                            layoutParams.width = context.dp2Px(iconSize.width.value.toInt())
                            layoutParams.height = context.dp2Px(iconSize.height.value.toInt())
                        }
                    }

                    view.findViewById<AppCompatButton>(R.id.native_call_to_action).apply {
                        setBackgroundResource(rippleId)
                    }

                    val params = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    params.gravity = Gravity.CENTER

                    parent.addView(view, params)

                    startIconAnimation(view = view)
                }
            )
        }
    }
}

private fun ViewGroup.removeAllViewAndAnimation() {
    clearAnimation()
    removeAllViews()
}

private fun startIconAnimation(view: View) {
    val appIcon: View = view.findViewById(R.id.native_icon_bound)

    val animator = ValueAnimator.ofFloat(-10f, 10f).apply {
        interpolator = LinearInterpolator()
        repeatMode = ValueAnimator.REVERSE
        repeatCount = ValueAnimator.INFINITE
        duration = 250
    }

    animator.addUpdateListener { animation ->
        val animatedValue = animation.animatedValue as Float
        appIcon.rotation = animatedValue
    }

    animator.start()
}
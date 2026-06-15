package com.looper.ads.helper.banner

import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.looper.ads.helper.banner.data.BannerAdType
import com.looper.ads.helper.banner.data.BannerState
import com.looper.ads.synchronizedAdView
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

@SuppressLint("ViewConstructor")
class BannerWrapper(
    private val context: Context,
    private val bannerAdType: BannerAdType
) : FrameLayout(context) {

    private var job: Job? = null

    private val scope by lazy {
        MainScope()
    }

    private var _bannerState: BannerState? = null
    private var bannerState: BannerState?
        get() = _bannerState
        set(value) {
            if (value != _bannerState) {
                _bannerState = value
                onBannerStateChanged()
            }
        }

    private fun onBannerStateChanged() {
        val state = bannerState ?: return

        when (state) {
            BannerState.Loading -> {
                val shimmerLayout = bannerAdType.bannerAdSize.getShimmerLayout(context = context)

                synchronizedAdView(view = shimmerLayout)
            }

            is BannerState.Success -> {
                val view = state.view

                addBannerAd(view)
            }

            BannerState.Error -> {
                visibility = GONE
                removeAllViews()
            }
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        job = scope.launch {
            BannerAdPreload.getAdView(context, bannerAdType).collect {
                bannerState = it
            }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()

        job?.cancel()
        job = null
    }

    fun destroy() {
        (_bannerState as? BannerState.Success)?.view?.destroy()
    }

}

private fun FrameLayout.addBannerAd(adView: View) {
    val params = ViewGroup.MarginLayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    )

    this.synchronizedAdView(view = adView, params = params)
}
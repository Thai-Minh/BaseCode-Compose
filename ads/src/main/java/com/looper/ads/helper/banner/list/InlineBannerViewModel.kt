package com.looper.ads.helper.banner.list

import android.content.Context
import androidx.compose.foundation.layout.PaddingValues
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.looper.ads.helper.banner.BannerAdPreload
import com.looper.ads.helper.banner.data.BannerAdType
import com.looper.ads.helper.banner.data.BannerState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class InlineBannerFactory(
    private val bannerType: BannerAdType, // Dp
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(InlineBannerViewModel::class.java)) {
            return InlineBannerViewModel(bannerType = bannerType) as T
        }
        throw IllegalArgumentException("Unknown NativeAdViewModel class")
    }
}

class InlineBannerViewModel(
    private val bannerType: BannerAdType
) : ViewModel() {

    private val _bannerAdResult = MutableStateFlow<BannerState>(BannerState.Loading)
    val bannerAdResult: StateFlow<BannerState> get() = _bannerAdResult

    private var collectJob: Job? = null

    fun init(context: Context, paddingList: PaddingValues) {
        collectJob = viewModelScope.launch {
            BannerAdPreload.getAdView(
                context = context,
                cacheAdType = bannerType,
                paddingList = paddingList
            ).collect {
                _bannerAdResult.value = it
            }
        }
    }

    fun loadBannerAd(context: Context, paddingList: PaddingValues) =
        BannerAdPreload.preload(
            context = context,
            cacheAdType = bannerType,
            paddingList = paddingList
        )

    override fun onCleared() {
        super.onCleared()

        collectJob?.cancel()
        collectJob = null

        BannerAdPreload.removeAdView(cacheAdType = bannerType)
    }
}
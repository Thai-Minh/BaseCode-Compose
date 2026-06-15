package com.looper.ads.helper.native_ad

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import kotlin.math.max

private const val REFRESH_RATE = 15_000L

class NativeAdViewModelFactory(
    private val app: Application,
    private val type: NativeAdType,
    private val refreshAd: Boolean = false
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(NativeAdViewModel::class.java)) {
            return NativeAdViewModel(app = app, type = type, refreshAd = refreshAd) as T
        }
        throw IllegalArgumentException("Unknown NativeAdViewModel class")
    }
}

class NativeAdViewModel(
    private val app: Application,
    private val type: NativeAdType,
    private val refreshAd: Boolean
) :
    ViewModel() {

    private val refreshTrigger = MutableStateFlow<Any?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val nativeAd: Flow<NativeAdState?> = refreshTrigger
        .filterNotNull()
        .flatMapLatest {
            NativeAdPreload.getNativeAd(context = app, adType = type, refreshAd = refreshAd)
        }
        .distinctUntilChanged()
        .shareIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            replay = 1
        )

    private var reloadJob: Job? = null

    private var pausedTime: Long = System.currentTimeMillis()

    private var isPaused = false

    init {
        viewModelScope.launch {
            nativeAd.collect {
                if (it !is NativeAdState.Loading && !isPaused) {
                    postReload()
                }
            }
        }
    }

    fun forceRefreshAd(key: Any?) {
        refreshTrigger.value = key
    }

    fun onPaused() {
        isPaused = true
        pausedTime = System.currentTimeMillis()

        removePostReload()
    }

    fun onResumed() {
        viewModelScope.launch {
            if (!isPaused) return@launch

            isPaused = false

            if (nativeAd.firstOrNull() !is NativeAdState.Loading) {
                val time = System.currentTimeMillis() - pausedTime
                val delay = max(REFRESH_RATE - time, 0L)
                postReload(delayMs = delay)
            }
        }
    }

    fun onDisposed() = onPaused()

    fun removeAdCache() {
        if (!type.removable) return

        NativeAdPreload.removeCacheNativeAds(adType = type)
    }

    private fun postReload(delayMs: Long = REFRESH_RATE) {
        removePostReload()

        reloadJob = viewModelScope.launch {
            delay(delayMs)
            loadNativeAd()
        }
    }

    private fun removePostReload() {
        reloadJob?.cancel()
        reloadJob = null
    }

    private fun loadNativeAd() = NativeAdPreload.reload(context = app, adType = type)

    override fun onCleared() {
        super.onCleared()

        onDisposed()

        removeAdCache()
    }
}
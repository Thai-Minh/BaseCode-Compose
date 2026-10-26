package com.looper.base.utils.firebase

import com.google.firebase.Firebase
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.remoteConfig
import com.looper.ads.AdsDataPreference.intersAutoCloseDelayMs
import com.looper.ads.AdsDataPreference.intersSplashAllowAutoClose
import com.looper.ads.safeResume
import com.looper.base.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.zip
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import kotlin.time.Duration.Companion.milliseconds

data class NativeOnboardingFullAutoNextConfig(
    val enableAutoNext: Boolean = false,
    val autoNextMs: Long = -1L,
) {
    val isEnabled: Boolean
        get() = enableAutoNext && autoNextMs > 0L
}

object FRRemoteConfig {
    private const val FETCH_ALL_CONFIGS_TIMEOUT_MS = 10_000L

    private var job: Job? = null

    val fetchShowAdsWhenReview: StateFlow<Boolean?>
        field = MutableStateFlow<Boolean?>(null)

    val fetchShowIAPWhenReview: StateFlow<Boolean?>
        field = MutableStateFlow<Boolean?>(null)

    private val fetchAll = MutableSharedFlow<Boolean>(1)
    private val fetchAds = MutableSharedFlow<Boolean>(replay = 1)

    val listenerFinal = fetchAll.zip(fetchAds) { all, ads ->
        true
    }

    var autoSelectDefaultLanguageMs = -1L
    var autoNextStepAfterApplyLanguageMs = -1L
    var useLanguageConfirmScreen = true
        private set
    var useNativeAdType = true
        private set

    var nativeOnboardingFull1AutoNext = NativeOnboardingFullAutoNextConfig()
        private set
    var nativeOnboardingFull2AutoNext = NativeOnboardingFullAutoNextConfig()
        private set

    fun fetchAllConfigs() {
        if (job?.isActive == true) return

        job = CoroutineScope(Dispatchers.IO).launch {
            try {
                val remoteConfig = Firebase.remoteConfig

                val activated = withTimeoutOrThrow(
                    timeoutMs = FETCH_ALL_CONFIGS_TIMEOUT_MS,
                    message = "fetchAndActivate timeout after ${FETCH_ALL_CONFIGS_TIMEOUT_MS}ms"
                ) {
                    remoteConfig.fetchAndActivate().await()
                } ?: false

                fetchIntersSplashConfig(remoteConfig = remoteConfig)
                fetchLanguageOnboardingConfig(remoteConfig = remoteConfig)
                fetchOnboardingConfig(remoteConfig = remoteConfig)
                fetchShowAdAndIAPWhenReview(remoteConfig = remoteConfig)

                if (activated) {
                    fetchIapConfig(remoteConfig)
//                    fetchAppLaunchFlow(remoteConfig)
//                    fetchIAAdsConfig(remoteConfig)
                }

                fetchAll.tryEmit(true)
            } catch (e: Exception) {

                fetchShowAdsWhenReview.value = true
                fetchShowIAPWhenReview.value = true
                fetchAll.tryEmit(false)
            }
        }
    }

    fun updateStateRemoteConfigAd(value: Boolean) {
        fetchAds.tryEmit(value)
    }

    suspend fun fetchAdsRemoteConfig(key: String): String? =
        suspendCancellableCoroutine { continuation ->
            fetchAdsRemoteConfig(key = key) { jsonConfig ->
                continuation.safeResume(jsonConfig)
            }
        }

    fun fetchAdsRemoteConfig(key: String, onComplete: (String?) -> Unit) {
        val remoteConfig = Firebase.remoteConfig

        remoteConfig.apply {
            fetchAndActivate().addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val jsonConfig = remoteConfig.getString(key)
                    onComplete(jsonConfig)
                } else {
                    onComplete(null)
                }
            }
        }
    }

    private fun fetchConfigOptionsAds(remoteConfig: FirebaseRemoteConfig) {
        val jsonConfig = remoteConfig.getString("config_options_ads")

        try {
            JSONObject(jsonConfig)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun fetchIapConfig(remoteConfig: FirebaseRemoteConfig) {
//        val jsonConfig = remoteConfig.getString("iap_config")
//
//        if (jsonConfig.isBlank()) {
//            return
//        }
//
//        try {
//            val jsonObject = JSONObject(jsonConfig)
//
//            val bestOffer = jsonObject.optString("bestOffer", "")
//            val sale = jsonObject.optString("sale", "year")
//            val offersArray = jsonObject.optJSONArray("offersPaywall")
//            val offersJsonString = offersArray?.toString() ?: "[]"
//
//            updateBestOffer(bestOffer)
//            updateSale(sale)
//            updateOffersPaywall(offersJsonString)
//
//        } catch (e: Exception) {
//            e.printStackTrace()
//        }
    }

    private fun fetchIntersSplashConfig(remoteConfig: FirebaseRemoteConfig) {
        val jsonConfig = remoteConfig.getString("interstitial_splash_config")

        if (jsonConfig.isBlank()) {
            return
        }

        try {
            val jsonObject = JSONObject(jsonConfig)

            val allowAutoClose = jsonObject.optBoolean("allow_auto_close", false)
            val delayMS = jsonObject.optLong("auto_close_delay_ms", 5000L)

            intersSplashAllowAutoClose = allowAutoClose
            intersAutoCloseDelayMs = delayMS

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun fetchLanguageOnboardingConfig(remoteConfig: FirebaseRemoteConfig) {
        val jsonConfig = remoteConfig.getString("language_onboarding_config")

        if (jsonConfig.isBlank()) {
            return
        }

        try {
            val jsonObject = JSONObject(jsonConfig)

            autoSelectDefaultLanguageMs = jsonObject.getLong("auto_select_default_language_ms")
            autoNextStepAfterApplyLanguageMs = jsonObject.getLong("auto_next_step_after_apply_language_ms")
            useLanguageConfirmScreen = jsonObject.optBoolean(
                "use_language_confirm_screen",
                true,
            )

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun fetchOnboardingConfig(remoteConfig: FirebaseRemoteConfig) {
        val jsonConfig = remoteConfig.getString("onboarding_config")
        if (jsonConfig.isBlank()) return

        runCatching {
            val jsonObject = JSONObject(jsonConfig)

            useNativeAdType = jsonObject.optBoolean("use_native_ad_type", true)

            fun parsePlacement(key: String): NativeOnboardingFullAutoNextConfig {
                val placement = jsonObject.optJSONObject(key)
                    ?: return NativeOnboardingFullAutoNextConfig()

                return NativeOnboardingFullAutoNextConfig(
                    enableAutoNext = placement.optBoolean("enable_auto_next", false),
                    autoNextMs = placement.optLong("auto_next_ms", -1L),
                )
            }

            nativeOnboardingFull1AutoNext = parsePlacement("native_onboarding_full_1")
            nativeOnboardingFull2AutoNext = parsePlacement("native_onboarding_full_2")
        }.onFailure {
            useNativeAdType = true
            nativeOnboardingFull1AutoNext = NativeOnboardingFullAutoNextConfig()
            nativeOnboardingFull2AutoNext = NativeOnboardingFullAutoNextConfig()
        }
    }

    private fun fetchAppLaunchFlow(remoteConfig: FirebaseRemoteConfig) {
        val jsonConfig = remoteConfig.getString("app_launch_flow")
        if (jsonConfig.isBlank()) return

        try {
            val jsonObject = JSONObject(jsonConfig)

            jsonObject.optBoolean("from_splash_to_lfo", true)

            jsonObject.optBoolean("from_pw_to_lfo", true)


//            AppDataPreference.fromSplashToLfo = fromSplashToLfo
//            AppDataPreference.fromPwToLfo = fromPwToLfo

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun fetchIAAdsConfig(remoteConfig: FirebaseRemoteConfig) {
        val jsonConfig = remoteConfig.getString("iaa_config")
        if (jsonConfig.isBlank()) return

        try {
            val jsonObject = JSONObject(jsonConfig)

            jsonObject.optBoolean("useCollapsibleBanner", false)
            jsonObject.optBoolean("enable_inter_language_onboarding", false)
            jsonObject.optBoolean("enable_inter_iap_onboarding", false)

//            AppDataPreference.useCollapsibleBanner = useCollapsibleBanner
//            AppDataPreference.enableInterLanguageOnboarding = enableInterLanguageOnboarding
//            AppDataPreference.enableInterIAPOnboarding = enableInterIAPOnboarding

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun fetchShowAdAndIAPWhenReview(remoteConfig: FirebaseRemoteConfig) {
        val jsonConfig = remoteConfig.getString("config_with_review")

        val jsonObject = JSONObject(jsonConfig)

        val versionCode = jsonObject.optInt("versionCode", 1)

        val showIAPWhenReview = jsonObject.optBoolean("isShowIAP", false)
        fetchShowIAPWhenReview.value = showIAPWhenReview

        if (versionCode == BuildConfig.VERSION_CODE) {
            val showAdWhenReview = jsonObject.optBoolean("isShowAds", false)
            fetchShowAdsWhenReview.value = showAdWhenReview
        } else {
            fetchShowAdsWhenReview.value = true
        }
    }

    private suspend fun <T> withTimeoutOrThrow(
        timeoutMs: Long,
        message: String,
        block: suspend () -> T
    ): T {
        return withTimeoutOrNull(timeoutMs.milliseconds) {
            block()
        } ?: throw RemoteConfigTimeoutException(message)
    }
}

private class RemoteConfigTimeoutException(message: String) : Exception(message)
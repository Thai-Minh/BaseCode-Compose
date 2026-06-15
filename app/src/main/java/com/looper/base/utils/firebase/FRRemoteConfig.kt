package com.looper.base.utils.firebase

import com.google.firebase.Firebase
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.remoteConfig
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
import org.json.JSONObject

object FRRemoteConfig {
    private var job: Job? = null

    private val _fetchShowAdsWhenReview = MutableStateFlow<Boolean?>(null)
    val fetchShowAdsWhenReview: StateFlow<Boolean?> = _fetchShowAdsWhenReview

    private val fetchAll = MutableSharedFlow<Boolean>(1)
    private val fetchAds = MutableSharedFlow<Boolean>(replay = 1)

    val listenerFinal = fetchAll.zip(fetchAds) { all, ads ->
        true
    }

    fun fetchAllConfigs() {
        if (job?.isActive == true) return

        job = CoroutineScope(Dispatchers.IO).launch {
            try {
                val remoteConfig = Firebase.remoteConfig

                val fetchSuccess = remoteConfig.fetchAndActivate().await()

                fetchShowAdWhenReview(remoteConfig)
                fetchApiConfig(remoteConfig)

                if (fetchSuccess) {
                    fetchConfigOptionsAds(remoteConfig)
                    fetchIapConfig(remoteConfig)
                    fetchAppLaunchFlow(remoteConfig)
                    fetchIAAdsConfig(remoteConfig)
                }

                fetchAll.tryEmit(fetchSuccess)
            } catch (e: Exception) {

                _fetchShowAdsWhenReview.value = true
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
        val jsonConfig = remoteConfig.getString("iap_config")

        if (jsonConfig.isBlank()) {
            return
        }

        try {
            val jsonObject = JSONObject(jsonConfig)

            val bestOffer = jsonObject.optString("bestOffer", "")
            val offersArray = jsonObject.optJSONArray("offersPaywall")
            val offersJsonString = offersArray?.toString() ?: "[]"

//            updateBestOffer(bestOffer)
//            updateOffersPaywall(offersJsonString)

        } catch (e: Exception) {
            e.printStackTrace()
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

    private fun fetchShowAdWhenReview(remoteConfig: FirebaseRemoteConfig) {
        val jsonConfig = remoteConfig.getString("iaa_config_review")

        val jsonObject = JSONObject(jsonConfig)

        val versionCode = jsonObject.optInt("versionCode", 1)

        if (versionCode == BuildConfig.VERSION_CODE) {
            val showAdWhenReview = jsonObject.optBoolean("isShowAds", false)

            _fetchShowAdsWhenReview.value = showAdWhenReview
        } else {
            _fetchShowAdsWhenReview.value = true
        }
    }

    private fun fetchApiConfig(remoteConfig: FirebaseRemoteConfig) {
        val jsonString = remoteConfig.getString("api_config")

        try {
            val jsonObject = JSONObject(jsonString)

            val pUrl = jsonObject.optString("primary_api", "")
            val pKey = jsonObject.optString("primary_key", "")
            val sUrl = jsonObject.optString("secondary_api", "")
            val sKey = jsonObject.optString("secondary_key", "")
            val useApi = jsonObject.optInt("use_api", 1)

//            updateAPIUsed(useApi)
//            updateApiConfigs(pUrl = pUrl, pKey = pKey, sUrl = sUrl, sKey = sKey)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
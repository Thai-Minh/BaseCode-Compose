package com.looper.ads

import android.app.Activity
import android.app.Application
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.ProcessLifecycleOwner
import com.looper.ads.dialog.WelcomeBackDialog
import com.zenith.template_appopenad.AppOpenAdManager
import org.json.JSONObject
import java.lang.ref.WeakReference

const val REQUEST_SPLASH_TIME_OUT = 5000L
const val REQUEST_INTERSTITIAL_TIME_OUT = 5000L
const val REQUEST_REWARDED_TIME_OUT = 10000L

object AdsManager : DefaultLifecycleObserver {
    private lateinit var instance: Application

    private var ignoreAppOpenAdOnetime: Boolean = false

    private var isAppInBackground = false

    private var currentActivity: WeakReference<Activity>? = null

    var isShowInterInApp: Boolean = true
        private set

    var isShowAppOpen: Boolean = true
        private set

    var isShowBannerHome: Boolean = true
        private set

    var isShowBannerSketch: Boolean = true
        private set

    var interstitialTimeoutMs: Long = REQUEST_INTERSTITIAL_TIME_OUT
        private set

    var splashTimeOut: Long = REQUEST_SPLASH_TIME_OUT
        private set

    var rewardTimeoutMs: Long = REQUEST_REWARDED_TIME_OUT
        private set

    fun init(app: Application) {
        instance = app

        ProcessLifecycleOwner.get().lifecycle.addObserver(this)

        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) {
                currentActivity = WeakReference(activity)

                if (isDisableAppOpenAd(activity = activity)) {
                    handleTemporarilyAppOpenResumed(isEnable = false)
                }
            }

            override fun onActivityPaused(activity: Activity) {}

            override fun onActivityCreated(a: Activity, b: Bundle?) {}

            override fun onActivityStarted(a: Activity) {

            }

            override fun onActivityStopped(a: Activity) {

            }

            override fun onActivitySaveInstanceState(a: Activity, b: Bundle) {}
            override fun onActivityDestroyed(a: Activity) {
            }
        })
    }

    override fun onStop(owner: androidx.lifecycle.LifecycleOwner) {
        isAppInBackground = true
    }

    override fun onStart(owner: androidx.lifecycle.LifecycleOwner) {
        if (isAppInBackground) {
            val activity = currentActivity?.get()
            if (activity != null && isSupportedActivity(activity)) {
                handleAppForeground(activity)
            }
            isAppInBackground = false
        }
    }

    fun checkRemoteConfig(jsonString: String?) {
        if (jsonString.isNullOrEmpty()) {
            isShowInterInApp = false
            isShowBannerHome = false
            isShowBannerSketch = false
            isShowAppOpen = false
            return
        }

        val root = JSONObject(jsonString)

//        checkInterstitialSplash(jsonObj = root)
//        checkOptionShowAds(jsonObj = root)

        updateTimeOutForMultiAdsType(jsonObj = root)
    }

    fun updateTimeOutForMultiAdsType(
        jsonObj: JSONObject
    ) {
        splashTimeOut = jsonObj.getTimeOutWithAdsType("splashAds") * 1000L
        interstitialTimeoutMs = jsonObj.getTimeOutWithAdsType("interAds") * 1000L
        rewardTimeoutMs = jsonObj.getTimeOutWithAdsType("rewardedAds") * 1000L
    }

    fun showAdOpen(activity: Activity, onShowAdCompleteListener: () -> Unit = {}) {
        if (!isSupportAds) return

        AppOpenAdManager.showAdOpen(activity, false, onShowAdCompleteListener)
    }

    fun ignoreShowAppOpenAdOnetime() {
        ignoreAppOpenAdOnetime = true
        AppOpenAdManager.showExtraActivity()
    }

    fun handleTemporarilyAppOpenResumed(isEnable: Boolean) {
        if (isEnable) {
            enableAppOpenAd()
        } else {
            disableAppOpenAd()
        }
    }

    private fun disableAppOpenAd() {
        AppOpenAdManager.disableTemporarily()
    }

    private fun enableAppOpenAd() {
        AppOpenAdManager.removeTemporarily()
    }

    private fun JSONObject.getTimeOutWithAdsType(adType: String): Int {
        val adObj = this.optJSONObject(adType)
        return adObj?.optInt("timeOut", 10) ?: 10
    }

    private fun handleAppForeground(activity: Activity) {
        if (!isSupportAds) return

        if (activity is AppCompatActivity && isSupportedActivity(activity)) {
            showAppOpenIfNeeded(activity)
        }
    }

    private fun isSupportedActivity(activity: Activity): Boolean {
        if (activity !is AppCompatActivity) return false

        return activity::class.java.simpleName == "MainActivity"
    }

    private fun isDisableAppOpenAd(activity: Activity): Boolean {
        if (activity !is AppCompatActivity) return false

        return when (activity::class.java.simpleName) {
            "MainActivity", "MainIAPActivity" -> true
            else -> false
        }
    }

    private fun showAppOpenIfNeeded(activity: FragmentActivity) {
        if (ignoreAppOpenAdOnetime) {
            ignoreAppOpenAdOnetime = false
            return
        }

        if (!activity.isFinishing && !activity.isDestroyed) {
            WelcomeBackDialog.newInstance()
                .showOnce(activity.supportFragmentManager, "WelcomeBackDialog")
        }
    }
}
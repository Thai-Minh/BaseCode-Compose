package com.looper.base.utils.tracking_event

import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.ParametersBuilder
import com.google.firebase.analytics.logEvent
import com.looper.base.MyApplication.Companion.instance
import com.looper.iap.model.PackageType
import com.looper.iap.model.StartIAPType

object TrackEvent : ITrackEvent {
    private val firebaseAnalytics by lazy {
        FirebaseAnalytics.getInstance(instance)
    }

    private fun logEvent(event: String, block: (ParametersBuilder.() -> Unit)? = null) {
        if (block != null) firebaseAnalytics.logEvent(event, block)
        else firebaseAnalytics.logEvent(event, null)
    }

    private fun logScreenView(screenName: String) {
        firebaseAnalytics.logEvent(FirebaseAnalytics.Event.SCREEN_VIEW) {
            param(FirebaseAnalytics.Param.SCREEN_NAME, screenName)
            param(FirebaseAnalytics.Param.SCREEN_CLASS, screenName)
        }
    }

    override fun logSplashActivity() {
        logScreenView("splash_activity")
    }

    override fun logLanguageActivity() {
        logScreenView("language_activity")
    }

    override fun logLanguageSelectedActivity() {
        logScreenView("language_selected_activity")
    }

    override fun logIntro1Activity() {
        logScreenView("intro1_activity")
    }

    override fun logIntro2Activity() {
        logScreenView("intro2_activity")
    }

    override fun logIntro3Activity() {
        logScreenView("intro3_activity")
    }

    override fun logIntro4Activity() {
        logScreenView("intro4_activity")
    }

    override fun logPermissionActivity() {
        logScreenView("permission_activity")
    }

    override fun logHomeActivity() {
        logScreenView("home_activity")
    }

    override fun logFirstOpen() {
        logEvent("first_open")
    }

    override fun logUfoSplash() {
        logEvent("ufo_splash")
    }

    override fun logUfoLanguageLoading() {
        logEvent("ufo_language_loading")
    }

    override fun logUfoLanguage() {
        logEvent("ufo_language")
    }

    override fun logUfoLanguageDup() {
        logEvent("ufo_language_dup")
    }

    override fun logUfoLanguageConfirm() {
        logEvent("ufo_language_confirm")
    }

    override fun logUfoOnboarding1() {
        logEvent("ufo_onboarding_1")
    }

    override fun logUfoOnboardingFull1() {
        logEvent("ufo_onboarding_full_1")
    }

    override fun logUfoOnboarding2() {
        logEvent("ufo_onboarding_2")
    }

    override fun logUfoOnboardingFull2() {
        logEvent("ufo_onboarding_full_2")
    }

    override fun logUfoOnboarding3() {
        logEvent("ufo_onboarding_3")
    }

    override fun logUfoOnboarding4() {
        logEvent("ufo_onboarding_4")
    }

    override fun logUfoHome() {
        logEvent("ufo_home")
    }

    override fun logPremiumBuySuccess(
        packageName: PackageType,
        placement: StartIAPType
    ) {
        logEvent("iap_purchase_success") {
            param("pack_name", packageName.key)
            param("location", placement.name)
        }
    }

    override fun logPremiumBuyFailed(message: String) {
        logEvent("iap_purchase_failed") {
            param("fail_reason", message)
        }
    }

    override fun logBuyTrialSuccess() {
        logEvent("trial_success")
    }

    override fun logNotPaymentTrial() {
        logEvent("not_buy_trial")
    }

    override fun logAdImpressionTaichi(currencyCode: String, valueMicros: Long) {
        logEvent("ad_impression_taichi") {
            param(FirebaseAnalytics.Param.AD_PLATFORM, "taichi")
            param(FirebaseAnalytics.Param.CURRENCY, currencyCode)
            param(FirebaseAnalytics.Param.VALUE, valueMicros / MICROS_PER_UNIT)
        }
    }


    private const val MICROS_PER_UNIT = 1_000_000.0
}
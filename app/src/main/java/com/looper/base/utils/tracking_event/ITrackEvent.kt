package com.looper.base.utils.tracking_event

import com.looper.iap.model.PackageType
import com.looper.iap.model.StartIAPType

interface ITrackEvent {
    fun logSplashActivity()
    fun logLanguageActivity()
    fun logLanguageSelectedActivity()
    fun logIntro1Activity()
    fun logIntro2Activity()
    fun logIntro3Activity()
    fun logIntro4Activity()
    fun logPermissionActivity()
    fun logHomeActivity()

    fun logFirstOpen()
    fun logUfoSplash()
    fun logUfoLanguageLoading()
    fun logUfoLanguage()
    fun logUfoLanguageDup()
    fun logUfoLanguageConfirm()
    fun logUfoOnboarding1()
    fun logUfoOnboardingFull1()
    fun logUfoOnboarding2()
    fun logUfoOnboardingFull2()
    fun logUfoOnboarding3()
    fun logUfoOnboarding4()
    fun logUfoHome()

    fun logAdImpressionTaichi(currencyCode: String, valueMicros: Long)

    fun logPremiumBuySuccess(packageName: PackageType, placement: StartIAPType)

    fun logPremiumBuyFailed(message: String)

    fun logBuyTrialSuccess()

    fun logNotPaymentTrial()
}
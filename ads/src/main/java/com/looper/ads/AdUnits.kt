package com.looper.ads

enum class AdUnits(val key: String) {

    //OPEN APP
    AppOpenAllPrice(key = "app_name_open_ad_id"),

    // INTERSTITIAL
    InterstitialSplash2Floor(key = "app_name_inter_splash_1_id"),
    InterstitialSplashMedium(key = "app_name_inter_splash_2_id"),
    InterstitialSplashAllPrice(key = "app_name_inter_splash_id"),

    InterstitialInApp2Floor(key = "app_name_inter_1_id"),
    InterstitialInAppMedium(key = "app_name_inter_2_id"),
    InterstitialInAppAllPrice(key = "app_name_inter_id"),

    // NATIVE
    NativeLanguageFirst2Floor(key = "app_name_native_language_1_1_id"),
    NativeLanguageFirstMedium(key = "app_name_native_language_1_2_id"),
    NativeLanguageFirstAllPrice(key = "app_name_native_language_1_id"),

    NativeLanguageSecond2Floor(key = "app_name_native_language_2_1_id"),
    NativeLanguageSecondMedium(key = "app_name_native_language_2_2_id"),
    NativeLanguageSecondAllPrice(key = "app_name_native_language_2_id"),

    NativeFullIntro2Floor(key = "app_name_full_native_intro_1_id"),
    NativeFullIntroMedium(key = "app_name_full_native_intro_2_id"),

    NativeIntroFirstAllPrice(key = "app_name_native_intro_1_id"),

    NativeIntroThirdAllPrice(key = "app_name_native_intro_3_id"),

    NativeHomeCollapsible2Floor(key = "app_name_native_collap_home_1_id"),
    NativeHomeCollapsibleMedium(key = "app_name_native_collap_home_2_id"),
    NativeHomeCollapsibleAllPrice(key = "app_name_native_collap_home_id"),

}

package com.looper.ads

enum class AdUnits(val key: String) {

    //OPEN APP
    AppOpenAllPrice(key = "app_name_open_ad_id"),

    // INTERSTITIAL
    InterstitialSplash(key = "app_name_inter_splash_id"),

    InterstitialInApp(key = "app_name_inter_id"),

    // NATIVE
    NativeLanguage1(key = "app_name_native_language_1id"),

    NativeLanguage2(key = "app_name_native_language_2id"),

    NativeFullIntro2(key = "app_name_full_native_intro_1_id"),

    NativeIntro1(key = "app_name_native_intro_1_id"),

    NativeIntro3(key = "app_name_native_intro_3_id"),

    NativeHomeCollapsible(key = "app_name_native_collap_home_id"),
}

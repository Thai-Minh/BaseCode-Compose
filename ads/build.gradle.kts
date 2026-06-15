import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.looper.ads"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        minSdk = 24

        manifestPlaceholders["ADMOB_APP_ID"] = ""

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.material)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // Shimmer
    implementation(libs.shimmer)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.androidx.lifecycle.process)

    implementation(libs.android.adsdk)
    implementation(libs.android.adapter)
    implementation(libs.android.admob)
    implementation(libs.android.appopenad)
    implementation(libs.android.nativead)
    implementation(libs.android.recyclerview)
    implementation(libs.android.pangle)
    implementation(libs.android.mintegral)
    implementation(libs.android.meta)
    implementation(libs.android.ironsource)
    implementation(libs.android.liftoff)

    // not exist applovin sdk key
//    implementation(libs.android.applovin)

    implementation(libs.android.inmobi)
    implementation(libs.android.unity)

    implementation(libs.adjust.sdk)
    implementation(libs.installreferrer)
    implementation(libs.play.services.ads.identifier)
    implementation(libs.facebook.core)

    implementation(libs.google.ads.mediation.facebook)
    implementation(libs.google.ads.mediation.applovin)
    implementation(libs.google.ads.mediation.pangle)
    implementation(libs.google.ads.mediation.unity)
    implementation(libs.google.ads.mediation.inmobi)
    implementation(libs.google.ads.mediation.moloco)
    implementation(libs.google.ads.mediation.mintegral)

    implementation(libs.avLoadingIndicatorView)

    // LiveData
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.lifecycle.viewmodel.savedstate)
    implementation(libs.lifecycle.livedata.ktx)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)

    //Lottie
    implementation(libs.lottie)
}

configurations.all {
    exclude(group = "com.looper.zenith.Thai-Minh", module = "mintegral")
    exclude(group = "com.looper.zenith.Thai-Minh", module = "pangle")

    // Keep the new Google Ads Mobile SDK and remove the legacy AdMob API artifact
    // pulled transitively by older mediation adapters.
    exclude(group = "com.google.android.gms", module = "play-services-ads-api")
}
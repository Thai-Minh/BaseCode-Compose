import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.devtools.ksp)
    alias(libs.plugins.google.gms.google.services)
    alias(libs.plugins.google.firebase.crashlytics)

    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.looper.base"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.looper.base"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        base.archivesName = "app_name_V${versionName}"

        val appName = project.findProperty("appName") ?: "BaseApp"
        base.archivesName = "${appName}_V${versionName}"
    }

    signingConfigs {
        create("releaseKey") {
            storeFile = file("app_name_key.jks") // TODO: example livescorekey.jks
            storePassword = System.getenv("LOOPER_PASSWORD")
            keyAlias = "key0"
            keyPassword = System.getenv("LOOPER_PASSWORD")
        }
    }

    buildTypes {
        debug {
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )

            manifestPlaceholders["ADMOB_APP_ID"] = "ca-app-pub-3940256099942544~3347511713"
            buildConfigField("String", "REMOTE_CONFIG_ADS_KEY", "\"ad_mediation_config_test\"")
        }

        release {
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = signingConfigs.getByName("releaseKey")

            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )

            manifestPlaceholders["ADMOB_APP_ID"] = "" // TODO: fill ID
            buildConfigField("String", "REMOTE_CONFIG_ADS_KEY", "\"ad_mediation_config_prod\"")
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true

        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
        viewBinding = true
        buildConfig = true
    }

    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions {
            freeCompilerArgs.add("-XXLanguage:+PropertyParamAnnotationDefaultTargetMode")
        }
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-XXLanguage:+ExplicitBackingFields")
    }
}

dependencies {
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    implementation(libs.multidex)

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

    implementation(libs.lifecycle.livedata.ktx)
    implementation(libs.androidx.compose.runtime.livedata)

    // for ad-sdk
    implementation(project(":ads"))
    implementation(libs.android.adsdk)

    // coil
    implementation(libs.coil)
    implementation(libs.coil.video)
    implementation(libs.coil.network.okhttp)

    // Kotlin Coroutine
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)

    // Room DB
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.room.runtime)

    // DI: koin
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.navigation)
    implementation(libs.koin.androidx.compose)

    // IAP
    implementation(libs.billing.ktx)

    // adjust
    implementation(libs.adjust.sdk)
    implementation(libs.installreferrer)
    implementation(libs.play.services.ads.identifier)

    // Firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.config.ktx)
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.crashlytics)

    // Navigation
    implementation(libs.navigation.compose)

    // DataStore
    implementation(libs.datastore.preferences)

    // Lottie Animation
    implementation(libs.lottie.animation)

    // Shimmer
    implementation(libs.shimmer)
}

configurations.all {
    exclude(group = "com.looper.zenith.Thai-Minh", module = "mintegral")
    exclude(group = "com.looper.zenith.Thai-Minh", module = "pangle")

    // Keep the new Google Ads Mobile SDK and remove the legacy AdMob API artifact
    // pulled transitively by older mediation adapters.
    exclude(group = "com.google.android.gms", module = "play-services-ads-api")
}
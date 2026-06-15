import java.net.URI

pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
        maven { url = URI.create("https://artifact.bytedance.com/repository/pangle") }
        maven { url = URI.create("https://android-sdk.is.com/") }
        maven {
            url = URI.create("https://dl-maven-android.mintegral.com/repository/mbridge_android_sdk_oversea")
        }

        // GitHub Packages cho Ads-SDK
        val username = providers.gradleProperty("ads_username").get()
        val password = providers.gradleProperty("ads_password").get()
        maven {
            url = URI.create("https://maven.pkg.github.com/Thai-Minh/Ads-SDK")
            isAllowInsecureProtocol = true
            credentials {
                this.username = username
                this.password = password
            }
        }
    }
}

rootProject.name = "Base Code Compose"
include(":app")
include(":ads")

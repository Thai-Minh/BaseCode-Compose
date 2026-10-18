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

        fun githubUsername(propertyName: String) = providers.gradleProperty(propertyName)
            .orNull.orEmpty()

        fun githubPassword(propertyName: String) = providers.gradleProperty(propertyName)
            .orNull.orEmpty()

        fun githubPackage(
            repo: String,
            usernameProperty: String,
            passwordProperty: String,
        ) {
            maven {
                name = "GitHubPackages-$repo"
                url = uri("https://maven.pkg.github.com/Thai-Minh/$repo")
                credentials {
                    username = githubUsername(usernameProperty)
                    password = githubPassword(passwordProperty)
                }
            }
        }

        githubPackage(
            repo = "Ads-SDK",
            usernameProperty = "ads_username",
            passwordProperty = "ads_password",
        )
        githubPackage(
            repo = "DownloaderCore",
            usernameProperty = "downloader_core_username",
            passwordProperty = "downloader_core_password",
        )
        githubPackage(
            repo = "DownloadCatcher",
            usernameProperty = "download_catcher_username",
            passwordProperty = "download_catcher_password",
        )

        maven {
            url = URI.create("http://139.59.246.166:8082/artifactory/vincent-library")
            isAllowInsecureProtocol = true
            credentials {
                this.username = providers.gradleProperty("permission_username").get()
                this.password = providers.gradleProperty("permission_password").get()
            }
        }
    }
}

rootProject.name = "Base Code Compose"
include(":app")
include(":ads")
include(":local-file-manager")
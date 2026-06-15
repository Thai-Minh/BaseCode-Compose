package com.looper.base.iap.utils

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.net.toUri
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import com.looper.base.R
import com.looper.base.iap.core.BillingManagerImpl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.reflect.KClass

const val DELAY_10_SECONDS = 10000L
const val DELAY_2_SECONDS = 2000L

const val GOOGLE_PLAY_URL = "https://play.google.com/"
const val GOOGLE_PLAY_STORE_SUBSCRIPTION_PACKAGE_URL =
    "https://play.google.com/store/account/subscriptions?package=%s"

const val PRODUCT_LIFETIME_ID = ""
const val PRODUCT_SUBSCRIPTION_ID = "my_app_name_subs" // TODO: change name
const val PRIVACY_POLICY_URL = "https://anderson-tony.netlify.app/privacy"
const val TERM_OF_SERVICE_URL = "https://anderson-tony.netlify.app/privacy"

const val TRIAL = "trial"

const val MONTH = "month"
const val WEEK = "week"
const val YEAR = "year"

val productIdInApps = listOf<String>()

val productIdNonConsumable = listOf(
    PRODUCT_SUBSCRIPTION_ID
)

val productIdSubs = listOf(PRODUCT_SUBSCRIPTION_ID, MONTH, WEEK, YEAR)

fun Context.openSubscriptionGooglePlay(url: String) {
    try {
        val intent = Intent().apply {
            action = Intent.ACTION_VIEW
            data = url.toUri()
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        startActivity(intent)
    } catch (e: Exception) {
        Log.d("MTHAI", "openGooglePlay error: $e")
    }
}

fun Activity.openMySubscription() {
    val url = String.format(
        Locale.ENGLISH,
        GOOGLE_PLAY_STORE_SUBSCRIPTION_PACKAGE_URL,
        application.packageName
    )
    openSubscriptionGooglePlay(url)
}

fun extractDays(durationString: String): Int {
    val pattern = Regex("""P(?:([0-9]+)Y)?(?:([0-9]+)M)?(?:([0-9]+)W)?(?:([0-9]+)D)?""")

    val match = pattern.matchEntire(durationString)
    if (match != null) {
        val years = match.groups[1]?.value?.toInt() ?: 0
        val months = match.groups[2]?.value?.toInt() ?: 0
        val weeks = match.groups[3]?.value?.toInt() ?: 0
        val days = match.groups[4]?.value?.toInt() ?: 0

        return years * 365 + months * 30 + weeks * 7 + days
    }

    return 0
}

fun Context.goToPolicy() {
    openUrl(PRIVACY_POLICY_URL)
}

fun Context.goToTermOfService() {
    openUrl(TERM_OF_SERVICE_URL)
}

fun Context.goToGooglePlay() {
    openUrl(GOOGLE_PLAY_URL)
}

private fun Context.openUrl(url: String) {
    try {
        startActivity(
            Intent(
                Intent.ACTION_VIEW,
                url.toUri()
            )
        )
    } catch (_: Exception) {

    }
}


fun Activity.finishActivityFadeOutAnimation() {
    finish()
    setAnimationTransition(isStartActivity = false)
}

fun Activity.setStartActivityFadeInAnimation() {
    setAnimationTransition(isStartActivity = true)
}

@SuppressLint("WrongConstant")
private fun Activity.setAnimationTransition(isStartActivity: Boolean) {

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        val overrideType = if (isStartActivity)
            Activity.OVERRIDE_TRANSITION_OPEN
        else
            Activity.OVERRIDE_TRANSITION_CLOSE

        overrideActivityTransition(
            overrideType,
            R.anim.iap_slide_in_anim,
            R.anim.iap_slide_out_anim
        )
    } else {
        overridePendingTransition(R.anim.iap_slide_in_anim, R.anim.iap_slide_out_anim)
    }
}

fun Long.formatTime(): String {
    val hours = (this / 1000 / 3600).toInt()
    val minutes = ((this / 1000 % 3600) / 60).toInt()
    val seconds = (this / 1000 % 60).toInt()

    val formattedTime =
        String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
    return formattedTime.replace(Regex("^0*:"), "")
}


fun getScanningDelayTime(
    billingManager: BillingManagerImpl,
    min: Long = DELAY_2_SECONDS,
    max: Long = DELAY_10_SECONDS
): Long {
    return if (billingManager.hasPremium()) {
        min
    } else {
        max
    }
}

suspend fun NavHostController.awaitRoute(vararg targetRoutes: KClass<*>) {
    val isExist = targetRoutes.any { routeClass ->
        currentDestination?.hasRoute(routeClass) == true
    }

    if (isExist) return

    currentBackStackEntryFlow
        .filter { entry ->
            targetRoutes.any { routeClass ->
                entry.destination.hasRoute(routeClass)
            }
        }
        .first()
}

suspend fun <T> LiveData<T>.await(timeoutMillis: Long = 5000): T {
    return withContext(Dispatchers.Main) {
        withTimeout(timeoutMillis) {
            suspendCancellableCoroutine { continuation ->
                val observer = object : Observer<T> {
                    override fun onChanged(value: T) {
                        removeObserver(this)
                        continuation.resume(value)
                    }
                }

                observeForever(observer)

                continuation.invokeOnCancellation {
                    removeObserver(observer)
                }
            }
        }
    }
}


fun extractCurrencySymbol(amountText: String): String {
    val trimmed = amountText.trim()

    // Regex: tìm ký tự tiền tệ (₫, $, €, £, ¥, v.v.)
    val regex = Regex("[^\\d.,\\s]+")

    return regex.find(trimmed)?.value ?: ""
}
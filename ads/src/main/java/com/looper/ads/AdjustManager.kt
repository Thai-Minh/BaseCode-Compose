package com.looper.ads

import android.app.Application
import com.adjust.sdk.Adjust
import com.adjust.sdk.AdjustAdRevenue
import com.adjust.sdk.AdjustConfig
import com.adjust.sdk.AdjustEvent
import com.adjust.sdk.LogLevel

const val ADMOB_SOURCE = "admob_sdk"
const val ADJUST_TOKEN = "" // TODO
const val ADJUST_APPLOVIN = "" // TODO
const val ADJUST_AD_IMPRESSION = "" // TODO

object AdjustManager {

    @Volatile
    private var paidEventListener: ((String, Long, AdRevenueMetadata) -> Unit)? = null

    fun setPaidEventListener(listener: (String, Long, AdRevenueMetadata) -> Unit) {
        paidEventListener = listener
    }

    fun setPaidEventListener(listener: (String, Long) -> Unit) {
        paidEventListener = { currency, micros, _ -> listener(currency, micros) }
    }

    fun initAdjustSDK(
        application: Application,
        isDebug: Boolean,
    ) {
        val env = if (isDebug) {
            AdjustConfig.ENVIRONMENT_SANDBOX
        } else {
            AdjustConfig.ENVIRONMENT_PRODUCTION
        }

        val config = AdjustConfig(application, ADJUST_TOKEN, env)
        config.setLogLevel(LogLevel.VERBOSE)
        Adjust.initSdk(config)
    }

    fun trackAdRevenue(
        adRevenueSource: String = ADMOB_SOURCE,
        revenue: Long,
        currency: String,
        adImpressionEventToken: String = ADJUST_AD_IMPRESSION,
        metadata: AdRevenueMetadata = AdRevenueMetadata(),
    ) {
        val adjustAdRevenue = AdjustAdRevenue(adRevenueSource)
        adjustAdRevenue.setRevenue(revenueInCurrency(valueMicros = revenue), currency)
        // adjustAdRevenue.addPartnerParameter("key", "value")
        Adjust.trackAdRevenue(adjustAdRevenue)
        dispatchPaidEvent(currency = currency, revenue = revenue, metadata = metadata)

        if (adImpressionEventToken.isNotEmpty()) {
            trackRevenueEvent(adImpressionEventToken, revenue, currency)
        }
    }

    internal fun dispatchPaidEvent(currency: String, revenue: Long, metadata: AdRevenueMetadata) {
        paidEventListener?.invoke(currency, revenue, metadata)
    }

    fun trackRevenueEvent(eventToken: String, revenue: Long, currency: String) {
        val adjustEvent = AdjustEvent(eventToken)
        adjustEvent.setRevenue(revenueInCurrency(valueMicros = revenue), currency)
        // adjustEvent.addCallbackParameter("key", "value")
        // adjustEvent.addPartnerParameter("key", "value")
        Adjust.trackEvent(adjustEvent)
    }

    fun trackEvent(eventToken: String) {
        val adjustEvent = AdjustEvent(eventToken)
        // adjustEvent.addCallbackParameter("key", "value")
        // adjustEvent.addPartnerParameter("key", "value")
        Adjust.trackEvent(adjustEvent)
    }
}
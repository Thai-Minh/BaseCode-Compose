/**
*Created by: ThaiND
*/
package com.looper.base.tracking

import android.app.Application
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import com.looper.ads.AdjustManager

object AdRevenueAnalytics {
    fun register(application: Application) {
        val analytics = FirebaseAnalytics.getInstance(application)
        AdjustManager.setPaidEventListener { currency, micros, metadata ->
            val parameters = Bundle().apply {
                putString(FirebaseAnalytics.Param.CURRENCY, currency)
                putDouble(FirebaseAnalytics.Param.VALUE, micros / 1_000_000.0)
                // Paid callbacks do not expose the mediated network. Do not label
                // all inventory AdMob or use the logical placement as a network ID.
                metadata.adFormat?.takeIf { it.isNotBlank() }?.let { putString(FirebaseAnalytics.Param.AD_FORMAT, it) }
                metadata.placement?.takeIf { it.isNotBlank() }?.let { putString("ad_placement", it) }
            }
            analytics.logEvent("ad_impression_taichi", parameters)
        }
    }
}

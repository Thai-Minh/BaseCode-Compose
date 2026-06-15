package com.looper.base.iap.core

import com.adjust.sdk.Adjust
import com.adjust.sdk.AdjustEvent
import com.adjust.sdk.AdjustPlayStoreSubscription
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.Purchase
import com.looper.base.iap.core.data.INAPP_KEY
import com.looper.base.iap.core.data.ItemPurchased
import com.looper.base.iap.core.data.MONTHLY_KEY
import com.looper.base.iap.core.data.PackageType
import com.looper.base.iap.core.data.WEEKLY_KEY
import com.looper.base.iap.core.data.YEARLY_KEY

enum class AdjustTokens(
    val packageTypeKey: Int,
    val token: String
) {
    InApp(packageTypeKey = INAPP_KEY, token = ""), // TODO
    Week(packageTypeKey = WEEKLY_KEY, token = ""), // TODO
    Month(packageTypeKey = MONTHLY_KEY, token = ""), // TODO
    Year(packageTypeKey = YEARLY_KEY, token = ""); // TODO

    companion object {
        fun findByPackageTypeKey(key: Int): AdjustTokens {
            return AdjustTokens.entries.find {
                it.packageTypeKey == key
            } ?: Week
        }
    }
}

object AdjustIAPManager {

    fun trackIAP(purchase: Purchase, itemPurchased: ItemPurchased?) {
        val item = itemPurchased?.item ?: return

        val productDetail = item.productDetails

        val productId = purchase.products.firstOrNull() ?: return

        val priceData = if (productDetail.productType == BillingClient.ProductType.SUBS) {
            productDetail.subscriptionOfferDetails?.firstOrNull()?.pricingPhases?.pricingPhaseList?.lastOrNull()
                ?.let {
                    it.priceAmountMicros.toDouble() / 1_000_000.0 to (it.priceCurrencyCode ?: "USD")
                }
        } else {
            productDetail.oneTimePurchaseOfferDetails?.let {
                it.priceAmountMicros.toDouble() / 1_000_000.0 to (it.priceCurrencyCode ?: "USD")
            }
        } ?: (0.0 to "USD")

        val (finalPrice, currency) = priceData

        val subscription = AdjustPlayStoreSubscription(
            finalPrice.toLong(),
            currency,
            productId,
            purchase.orderId,
            purchase.signature,
            purchase.purchaseToken
        ).apply {
            purchaseTime = purchase.purchaseTime
        }

        Adjust.trackPlayStoreSubscription(subscription)

        val specificToken = getToken(item.packageType)
        if (specificToken.isNotEmpty()) {
            sendAdjustEvent(specificToken, finalPrice, currency, purchase.orderId)
        }

        if (specificToken != AdjustTokens.InApp.token) {
            sendAdjustEvent(AdjustTokens.InApp.token, finalPrice, currency, purchase.orderId)
        }
    }

    private fun sendAdjustEvent(token: String, price: Double, currency: String, orderId: String?) {
        val adjustEvent = AdjustEvent(token).apply {
            setRevenue(price, currency)
            setOrderId(orderId)
        }
        Adjust.trackEvent(adjustEvent)
    }

    private fun getToken(
        packageType: PackageType
    ): String {
        return AdjustTokens.findByPackageTypeKey(packageType.key).token
    }
}
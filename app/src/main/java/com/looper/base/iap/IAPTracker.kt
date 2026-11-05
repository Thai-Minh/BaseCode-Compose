/**
*Created by: ThaiND
*/

package com.looper.base.iap

import com.adjust.sdk.Adjust
import com.adjust.sdk.AdjustEvent
import com.adjust.sdk.AdjustPlayStoreSubscription
import com.looper.iap.model.ProductDetailItem
import com.looper.iap.tracking.IapTracker
import com.looper.iap.tracking.IapTrackingEvent
import java.util.concurrent.ConcurrentHashMap

/** Only track a user-initiated purchase with its actual first phase, never a guessed renewal price. */
class IAPTracker(
    private val eventTokensByProduct: Map<String, String> = emptyMap(),
) : IapTracker {
    private val selectedItems = ConcurrentHashMap<String, ProductDetailItem>()
    private val trackedTokens = ConcurrentHashMap.newKeySet<String>()

    fun select(item: ProductDetailItem) {
        selectedItems[item.identity.productId] = item
    }

    override suspend fun trackPurchase(event: IapTrackingEvent) {
        val item = selectedItems.remove(event.identity.productId) ?: return
        val phase = item.subsOfferDetails?.pricingPhases?.pricingPhaseList?.firstOrNull() ?: return

        if (!trackedTokens.add(event.purchaseToken)) {
            return
        }

        val subscription = AdjustPlayStoreSubscription(
            phase.priceAmountMicros,
            phase.priceCurrencyCode,
            event.identity.productId,
            event.orderId,
            event.signature,
            event.purchaseToken,
        ).apply {
            purchaseTime = event.purchaseTime
        }
        Adjust.trackPlayStoreSubscription(subscription)

        // Existing client tokens are empty TODO values. Never invent or submit an empty token.
        val token = eventTokensByProduct[event.identity.productId]?.takeIf { value -> value.isNotBlank() }
            ?: return
        val adjustEvent = AdjustEvent(token).apply {
            setRevenue(phase.priceAmountMicros.toDouble() / 1_000_000.0, phase.priceCurrencyCode)
            setOrderId(event.orderId)
        }
        Adjust.trackEvent(adjustEvent)
    }
}

/**
*Created by: ThaiND
*/

package com.looper.base

import com.android.billingclient.api.BillingClient
import com.looper.base.activities.IapTestState
import com.looper.iap.catalog.IapProductConfig
import com.looper.iap.model.BillingState
import com.looper.iap.model.ProductIdentity
import com.looper.iap.model.PurchaseResult
import com.looper.iap.model.PurchaseStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class IapTestBehaviorTest {
    @Test
    fun baseOffersHaveNoPlayTagsOrOfferIds() {
        val subscription = PlayIapCatalog.catalog.products.single() as IapProductConfig.Subscription

        subscription.plans.forEach { plan ->
            val offer = plan.offers.single()

            assertEquals(emptySet<String>(), offer.tags)
            assertNull(offer.offerId)
        }
    }

    @Test
    fun catalogQueriesOneSubscriptionProductForAllThreePlans() {
        val queryProduct = PlayIapCatalog.catalog.queryProducts.single()

        assertEquals("ai_photo_gen_subs", queryProduct.productId)
        assertEquals(BillingClient.ProductType.SUBS, queryProduct.productType)
        assertEquals(3, PlayIapCatalog.catalog.findSubscription(productId = queryProduct.productId)?.plans?.size)
    }

    @Test
    fun unrelatedPurchaseEventsDoNotCompleteCurrentPurchase() {
        val pending = identity(basePlanId = "ai-photo-editor-subs-week")
        val state = IapTestState(billing = BillingState.Success, pending = pending)

        val next = state.onPurchaseResult(
            result = PurchaseResult(
                status = PurchaseStatus.Failed,
                identity = identity(basePlanId = "ai-photo-editor-subs-month"),
            ),
        )

        assertSame(state, next)
        assertTrue(next.busy)
        assertNull(next.message)
    }

    @Test
    fun purchaseOutcomesClearOnlyMatchingPendingPurchase() {
        val pending = identity(basePlanId = "ai-photo-editor-subs-week")
        val state = IapTestState(billing = BillingState.Success, pending = pending)

        listOf(
            PurchaseStatus.Purchased,
            PurchaseStatus.Pending,
            PurchaseStatus.Cancelled,
            PurchaseStatus.Failed,
        ).forEach { status ->
            val next = state.onPurchaseResult(
                result = PurchaseResult(status = status, identity = pending),
            )

            assertNull(next.pending)
            assertFalse(next.busy)
            assertFalse(next.message.isNullOrBlank())
            assertFalse(next.premium) // A purchase event is not an entitlement confirmation.
        }
    }

    @Test
    fun cancellationWithoutIdentityClearsPendingButUnspecifiedStatusDoesNot() {
        val pending = identity(basePlanId = "ai-photo-editor-subs-year")
        val state = IapTestState(billing = BillingState.Success, pending = pending)

        val cancelled = state.onPurchaseResult(result = PurchaseResult(status = PurchaseStatus.Cancelled))
        assertNull(cancelled.pending)
        assertEquals("Purchase cancelled.", cancelled.message)
        assertSame(
            state,
            state.onPurchaseResult(
                result = PurchaseResult(status = PurchaseStatus.Unspecified, identity = pending),
            ),
        )
    }

    private fun identity(basePlanId: String): ProductIdentity = ProductIdentity(
        productId = "ai_photo_gen_subs",
        basePlanId = basePlanId,
        productType = BillingClient.ProductType.SUBS,
    )
}

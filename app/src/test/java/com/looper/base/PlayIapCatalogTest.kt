package com.looper.base

import com.looper.base.iap.data.PlayIapCatalog
import com.looper.iap.catalog.IapProductConfig
import com.looper.iap.model.OfferKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlayIapCatalogTest {
    @Test fun subscriptionIdentityMatchesSample() {
        val product = PlayIapCatalog.catalog.products.single() as IapProductConfig.Subscription
        assertEquals("ai_photo_gen_subs", product.productId)
        assertEquals("premium", product.entitlementId)
        assertEquals(listOf("weekly", "monthly", "yearly"), product.plans.map { it.packageType.key })
        assertEquals(listOf("ai-photo-editor-subs-week", "ai-photo-editor-subs-month", "ai-photo-editor-subs-year"), product.plans.map { it.basePlanId })
        product.plans.forEach { plan ->
            val offer = plan.offers.single()
            assertEquals(OfferKind.BASE, offer.kind)
            assertNull(offer.offerId)
            assertEquals(plan.packageType.key, offer.packageType.key)
        }
    }
}

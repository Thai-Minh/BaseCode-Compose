/**
*Created by: ThaiND
*/

package com.looper.base.iap

import com.looper.iap.catalog.IapCatalog
import com.looper.iap.catalog.IapOfferConfig
import com.looper.iap.catalog.IapPlanConfig
import com.looper.iap.catalog.IapProductConfig
import com.looper.iap.model.OfferKind
import com.looper.iap.model.PackageType

/** Recognition-only entries must never be exposed as offers available for purchase. */
object RecognitionCatalog {
    private val campaignProducts = listOf(
        "week_399", "week_499", "week_699", "week_899",
        "year_3499", "year_5999", "year_8999",
    )

    // These identifiers are present in the client's legacy BillingUtils subscription query list. (các gói cũ)
    private val legacyProducts = listOf("ai_cleaner_subs", "month", "week", "year")

    fun merge(selection: SubscriptionSelection): IapCatalog {
        val selectedIds = selection.catalog.products.map { product -> product.productId }.toSet()

        val selectedKeys = selection.catalog.products
            .asSequence()
            .filterIsInstance<IapProductConfig.Subscription>()
            .flatMap { product -> product.plans }
            .flatMap { plan -> plan.offers }
            .map { offer -> offer.packageType.key }
            .toMutableSet()

        val hidden = (campaignProducts + legacyProducts).distinct()
            .filterNot { productId -> productId in selectedIds }
            .mapIndexed { index, productId ->
                var key = "Recognition${index}Original"

                while (!selectedKeys.add(element = key)) {
                    key += "Hidden"
                }

                IapProductConfig.Subscription(
                    productId = productId,
                    entitlementId = "premium",
                    plans = listOf(
                        IapPlanConfig(
                            packageType = PackageType(key = key),
                            // A null plan matches any returned BASE plan for restore recognition.
                            basePlanId = null,
                            offers = listOf(
                                IapOfferConfig(
                                    packageType = PackageType(key = key),
                                    offerId = null,
                                    kind = OfferKind.BASE,
                                ),
                            ),
                        ),
                    ),
                    metadata = mapOf("recognitionOnly" to "true"),
                )
            }

        return IapCatalog(products = selection.catalog.products + hidden)
    }
}

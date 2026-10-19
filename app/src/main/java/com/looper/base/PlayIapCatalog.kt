/**
*Created by: ThaiND
*/

package com.looper.base

import com.looper.iap.catalog.IapCatalog

/**
 * Matches the IAP-SDK sample catalog for com.cubstudio.aiphotoeditor.photoenhance.
 * Prices and offer tokens are supplied by Google Play, not by this catalog.
 */
object PlayIapCatalog {
    val catalog: IapCatalog = IapCatalog {
        subscription(
            productId = "ai_photo_gen_subs",
            entitlementId = "premium",
        ) {
            plan(
                key = "weekly",
                basePlanId = "ai-photo-editor-subs-week",
            ) {
                baseOffer()
            }

            plan(
                key = "monthly",
                basePlanId = "ai-photo-editor-subs-month",
            ) {
                baseOffer()
            }

            plan(
                key = "yearly",
                basePlanId = "ai-photo-editor-subs-year",
            ) {
                baseOffer()
            }
        }
    }
}

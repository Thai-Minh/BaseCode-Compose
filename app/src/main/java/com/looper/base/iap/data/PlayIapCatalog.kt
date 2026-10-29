/**
*Created by: ThaiND
*/

package com.looper.base.iap.data

import com.looper.iap.catalog.IapCatalog

/**
 * Matches the IAP-SDK sample catalog for com.cubstudio.aiphotoeditor.photoenhance.
 * Prices and offer tokens are supplied by Google Play, not by this catalog.
 */
object PlayIapCatalog {
    val catalog: IapCatalog = IapCatalog {
        subscription(
            productId = "ai_cleaner_subs",
            entitlementId = "premium",
        ) {
            plan(
                key = "WeeklyOriginal",
                basePlanId = "ai-cleaner-week",
            ) {
                baseOffer()
            }

//            plan(
//                key = "MonthlyOriginal",
//                basePlanId = "ai-cleaner-subs-month",
//            ) {
//                baseOffer()
//            }

            plan(
                key = "YearlyOriginal",
                basePlanId = "ai-cleaner-year",
            ) {
                baseOffer()
            }
        }
    }
}

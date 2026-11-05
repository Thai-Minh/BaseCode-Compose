/**
*Created by: ThaiND
*/

package com.looper.base.iap.data

/** Campaign IDs supplied for this migration; Play availability still requires license testing. */
object DefaultSubscriptionConfig {
    val payload: String = """
        {"bestoffer":"","products":[{
          "productId":"week_699","entitlementId":"premium",
          "plan":[{"key":"WeeklyOriginal","basePlanId":"a","offer":[
            {"key":"WeeklyOriginal","offerId":null,"kind":"BASE","tags":[]},
            {"key":"WeeklyTrial","offerId":"trial-week","kind":"TRIAL","tags":[]}
          ]}]
        }]}
    """.trimIndent()
}

/**
*Created by: ThaiND
*/
package com.looper.ads

/** Logical placement is not a mediation network identifier. */
data class AdRevenueMetadata(
    val adFormat: String? = null,
    val placement: String? = null,
)

internal fun revenueInCurrency(valueMicros: Long): Double {
    return valueMicros / 1_000_000.0
}

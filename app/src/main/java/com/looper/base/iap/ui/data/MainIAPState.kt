package com.looper.base.iap.ui.data

import com.looper.iap.model.BillingState
import com.looper.iap.model.ProductDetailItem
import com.looper.iap.model.ProductIdentity
import com.looper.iap.model.PurchaseResult
import com.looper.iap.model.PurchaseStatus

data class MainIAPState(
    val billing: BillingState = BillingState.Loading,
    val products: List<ProductDetailItem> = emptyList(),
    val premium: Boolean = false,
    val pending: ProductIdentity? = null,
    val restoring: Boolean = false,
    val message: String? = null,
    val isPurchaseSuccessful: Boolean = false,
) {
    val busy: Boolean
        get() = (pending != null || restoring || billing == BillingState.Loading)

    fun onPurchaseResult(result: PurchaseResult): MainIAPState {
        val expected = pending ?: return this
        val identity = result.identity

        // Play can report a cancelled billing flow without a product identity.
        if (identity == null && result.status != PurchaseStatus.Cancelled &&
            result.status != PurchaseStatus.Failed
        ) {
            return this
        }

        if (identity != null) {
            val matches = identity.productId == expected.productId &&
                    identity.basePlanId == expected.basePlanId &&
                    identity.offerId == expected.offerId

            if (!matches) {
                return this
            }
        }

        val message = when (result.status) {
            PurchaseStatus.Purchased -> "Play reported a purchase. TEST ONLY: not server-verified."
            PurchaseStatus.Pending -> "Payment pending. Restore after completion."
            PurchaseStatus.Cancelled -> "Purchase cancelled."
            PurchaseStatus.Failed -> "Purchase failed. Check Play account and connection."
            PurchaseStatus.Unspecified -> return this
        }

        // Sample correlation only: the SDK has no per-launch transaction ID. A delayed
        // event for the same product/plan/offer can match a new request. Never use this
        // UI flag as entitlement verification; startup/restore alone cannot set it.
        return copy(
            pending = null,
            message = if (restoring) this.message else message,
            isPurchaseSuccessful = !restoring && result.status == PurchaseStatus.Purchased,
        )
    }
}
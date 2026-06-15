package com.looper.base.iap.core.data

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.android.billingclient.api.Purchase

enum class PurchaseStatus(val key: Int) {
    Unspecified(key = 0), Purchased(key = 1), Pending(key = 2)
}

data class PurchaseResult(val status: PurchaseStatus, val purchase: Purchase? = null)

enum class PurchaseStatusUI(
    val key: Int,
    @DrawableRes val labelId: Int,
    @StringRes val titleId: Int,
    @StringRes val descriptionId: Int,
    @StringRes val textButtonId: Int,
) {
    Failed(
        key = 0,
        labelId = -1,
        titleId = -1,
        descriptionId = -1,
        textButtonId = -1
    ),
    Success(
        key = 1,
        labelId = -1,
        titleId = -1,
        descriptionId = -1,
        textButtonId = -1
    ),
    Expiry(
        key = 3,
        labelId = -1,
        titleId = -1,
        descriptionId = -1,
        textButtonId = -1
    );

    companion object {
        fun findByKey(key: Int): PurchaseStatusUI? {
            return when (key) {
                Success.key -> Success
                Failed.key -> Failed
                Expiry.key -> Expiry
                else -> null
            }
        }
    }
}
package com.looper.base.iap.core.data

import com.android.billingclient.api.BillingClient
import com.looper.base.iap.utils.PRODUCT_SUBSCRIPTION_ID

data class PackageInfo(
    val id: String,
    val productType: String,
    val packageType: PackageType? = null
)

val packageInfoItems = listOf(
    PackageInfo(
        id = PRODUCT_SUBSCRIPTION_ID,
        productType = BillingClient.ProductType.SUBS
    )
)
package com.looper.base.iap.core.data

import com.android.billingclient.api.ProductDetails
import com.looper.base.R

data class ProductDetailItem(
    val offerNameId: Int = R.string.monthly,
    val planId: String,
    val productDetails: ProductDetails,
    val packageType: PackageType,
    val subsOfferDetails: ProductDetails.SubscriptionOfferDetails? = null,
    var saveOff: Int = 0,
    var saleOff: Int = 0,
    val freeTrialDay: Int? = null,
    val price: Long = 0L,
    val originalPrice: Long = 0L,
    val priceStr: String = "",
    val originalPriceStr: String = "",
    val priceCurrencyCode: String,
    val isBestOffer: Boolean = false,
    val isSelected: Boolean = false,
    val isRecommend: Boolean = false
) {
    override fun toString(): String {
        return "ProductDetailItem(" +
                "offerNameId=$offerNameId, " +
                "planId='$planId', " +
                "packageType=$packageType, " +
                "saleOff=$saleOff, " +
                "freeTrialDay=$freeTrialDay, " +
                "price=$price, " +
                "originalPrice=$originalPrice, " +
                "priceStr='$priceStr', " +
                "originalPriceStr='$originalPriceStr', " +
                "isBestOffer=$isBestOffer, " +
                "isSelected=$isSelected, " +
                "isRecommend=$isRecommend" +
                ")"
    }
}

val ProductDetailItem.isMonthPackage: Boolean
    get() {
        return packageType.key == MONTHLY_KEY
    }

val ProductDetailItem.isWeekPackage: Boolean
    get() {
        return packageType.key == WEEKLY_KEY
    }



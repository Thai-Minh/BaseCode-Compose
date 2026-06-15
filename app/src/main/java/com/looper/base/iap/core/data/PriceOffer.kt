package com.looper.base.iap.core.data

data class PriceOffer(
    var originalPrice: Long = 0L,
    var originalPriceStr: String = "",
    var lowestPrice: Long = 0L,
    var lowestPriceStr: String = "",
    var saleOff: Int = 0,
    var priceCurrencyCode: String = "US"
)
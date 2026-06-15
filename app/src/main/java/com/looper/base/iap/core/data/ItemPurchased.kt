package com.looper.base.iap.core.data

import com.looper.base.iap.ui.data.StartIAPType


data class ItemPurchased(
    val typeFeature: StartIAPType? = null,
    val item: ProductDetailItem? = null,
)
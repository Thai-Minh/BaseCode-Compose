package com.looper.base.iap.core.data

sealed class GiftState {
    data class Success(val item: ProductDetailItem) : GiftState()

    object Loading : GiftState()

    object Error : GiftState()

    object NotFound : GiftState()
}
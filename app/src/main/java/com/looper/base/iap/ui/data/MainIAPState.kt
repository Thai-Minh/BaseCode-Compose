package com.looper.base.iap.ui.data

import com.looper.base.iap.core.data.ProductDetailItem

sealed class MainIAPState {
    data class Success(val items: List<ProductDetailItem>, val tabSelected: Int = 0) : MainIAPState()

    object Loading : MainIAPState()

    object Error : MainIAPState()
}
package com.looper.base.iap.core.repository

import android.app.Activity
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.looper.base.iap.core.data.BillingState
import com.looper.base.iap.core.data.PackageType
import com.looper.base.iap.core.data.ProductDetailItem
import com.looper.base.iap.core.data.PurchaseResult
import com.looper.base.iap.ui.data.StartIAPType
import kotlinx.coroutines.flow.Flow

interface IBillingRepository {

    val state: Flow<BillingState>

    val productDetailItems: Flow<List<ProductDetailItem>?>

    val purchaseInApp: Flow<List<Purchase?>?>

    val purchaseSubs: Flow<List<Purchase?>?>

    val purchaseResult: Flow<PurchaseResult?>

    val itemPurchase: Pair<StartIAPType, ProductDetailItem>?

    fun startConnection()

    fun stopConnection()

    fun reConnection()

    fun refreshPurchase()

    fun buy(activity: Activity, startBuy: StartIAPType, item: ProductDetailItem)

    fun setItemPurchaseSelected(startBuy: StartIAPType?, item: ProductDetailItem?)

    suspend fun queryProductDetails(productId: String, packageType: String): ProductDetails?

    suspend fun getProductDetailItem(packageType: PackageType): ProductDetailItem?
}
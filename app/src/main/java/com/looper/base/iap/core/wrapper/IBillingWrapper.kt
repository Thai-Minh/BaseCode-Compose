package com.looper.base.iap.core.wrapper

import android.app.Activity
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.looper.base.iap.core.data.BillingState
import com.looper.base.iap.core.data.PurchaseResult
import kotlinx.coroutines.flow.Flow

interface IBillingWrapper {

    val purchaseResult: Flow<PurchaseResult?>

    val billingState: Flow<BillingState>

    val purchaseInApps: Flow<List<Purchase?>?>

    val purchaseSubscriptions: Flow<List<Purchase?>?>

    val productDetailsMap: Flow<Map<String, ProductDetails>?>

    fun startConnection()

    fun stopConnection()

    fun queryPurchase()

    fun buy(activity: Activity, params: BillingFlowParams)

    fun reConnection()

    suspend fun queryProductDetails(productId: String, productType: String): ProductDetails?
}
package com.looper.base.iap.core

import android.app.Activity
import androidx.lifecycle.LiveData
import com.looper.base.iap.core.data.BillingState
import com.looper.base.iap.core.data.PackageType
import com.looper.base.iap.core.data.ProductDetailItem
import com.looper.base.iap.core.data.PurchaseResult
import com.looper.base.iap.core.data.SubscriptionInfo
import com.looper.base.iap.ui.data.StartIAPType

interface IBillingManager {

    val subscriptions: LiveData<List<SubscriptionInfo>>

    val subscription: LiveData<SubscriptionInfo?>

    val state: LiveData<BillingState>

    val productDetailItems: LiveData<List<ProductDetailItem>?>

    val purchaseResult: LiveData<PurchaseResult?>

    val isPremium: LiveData<Boolean>

    fun hasPremium(): Boolean

    suspend fun hasUserNeverPurchased(): Boolean

    fun hasPackageSale(callback: (isExist: Boolean) -> Unit)
    fun hasPackageSale(packageType: PackageType, callback: (isExist: Boolean) -> Unit)

    fun hasSubscriptionOnHold(): Boolean

    fun hasSubscriptionNotice(): Boolean // show any subscription status // success, expiry, on hold...

    fun reConnection()

    fun buy(activity: Activity, startBuy: StartIAPType, item: ProductDetailItem)

    fun setItemPurchaseSelected(startBuy: StartIAPType, item: ProductDetailItem?)

    suspend fun getPackage(packageType: PackageType): ProductDetailItem?
}
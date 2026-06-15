package com.looper.base.iap.core

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.util.Base64
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.asLiveData
import androidx.lifecycle.distinctUntilChanged
import androidx.lifecycle.map
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.Purchase
import com.looper.ads.setSupportAd
import com.looper.base.activities.MainActivity
import com.looper.base.base.navigation.MainInput
import com.looper.base.base.navigation.SplashInput
import com.looper.base.base.storage.cancelSubsCount
import com.looper.base.base.storage.lastDayShowPending
import com.looper.base.base.storage.subscriptionInfo
import com.looper.base.base.storage.tokenPurchaseSuccess
import com.looper.base.base.storage.updateCancelSubsCount
import com.looper.base.base.storage.updateLastDayShowPending
import com.looper.base.base.storage.updateSubscriptionInfo
import com.looper.base.base.storage.updateTokenPurchaseSuccess
import com.looper.base.iap.core.data.BillingState
import com.looper.base.iap.core.data.ItemPurchased
import com.looper.base.iap.core.data.PackageType
import com.looper.base.iap.core.data.ProductDetailItem
import com.looper.base.iap.core.data.PurchaseResult
import com.looper.base.iap.core.data.PurchaseStatus
import com.looper.base.iap.core.data.SubscriptionActivityType
import com.looper.base.iap.core.data.SubscriptionInfo
import com.looper.base.iap.core.data.decodeBase64JsonOrEmpty
import com.looper.base.iap.core.data.isExpiry
import com.looper.base.iap.core.data.packageSales
import com.looper.base.iap.core.data.toJson
import com.looper.base.iap.core.data.toSubscriptionList
import com.looper.base.iap.core.repository.BillingRepositoryImpl
import com.looper.base.iap.ui.MainIAPActivity
import com.looper.base.iap.ui.data.StartIAPType
import com.looper.base.iap.utils.PRODUCT_LIFETIME_ID
import com.looper.base.iap.utils.awaitRoute
import com.looper.base.iap.utils.extractDays
import com.looper.base.iap.utils.productIdInApps
import com.looper.base.iap.utils.productIdSubs
import com.looper.base.utils.TransformationsUnit
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BillingManagerImpl(context: Context) : IBillingManager {

    private val scope = MainScope()

    private val repository = BillingRepositoryImpl(context = context)

    private val _subscriptions = MutableLiveData<List<SubscriptionInfo>>(emptyList())
    override val subscriptions: LiveData<List<SubscriptionInfo>> = _subscriptions

    override val subscription: LiveData<SubscriptionInfo?> = subscriptions.map {
//        Log.d("MTHAI", "LiveData subscriptionInfo: ${it.toJson()}")

        it.findSubscriptionSelected()
    }

    override val state: LiveData<BillingState> = repository.state.asLiveData()

    override val productDetailItems: LiveData<List<ProductDetailItem>?> =
        repository.productDetailItems.asLiveData()

    override val purchaseResult: LiveData<PurchaseResult?> = repository.purchaseResult.asLiveData()

    override val isPremium: LiveData<Boolean> = subscription.map {
        it != null && it.purchaseState == Purchase.PurchaseState.PURCHASED
    }

    private val purchaseSuccess: Flow<PurchaseResult?> = repository.purchaseResult.map {
        if (it?.status == PurchaseStatus.Purchased) {
            it
        } else {
            null
        }
    }

    private val componentActivity = MutableLiveData<AppCompatActivity?>(null)

    private val subscriptionActivityType = MutableLiveData<SubscriptionActivityType?>()

    private val navigateActivityType: LiveData<Pair<AppCompatActivity?, SubscriptionActivityType?>> =
        TransformationsUnit.map(
            scope, componentActivity, subscriptionActivityType
        ) { act, type ->
            act to type
        }

    private val navigateObserver = Observer<Pair<AppCompatActivity?, SubscriptionActivityType?>> {
        scope.launch {
            val activity = it.first
            val type = it.second

            if (activity != null && type != null) {

                if (activity is MainActivity) {
                    try {
                        activity.navController.awaitRoute(
                            MainInput::class,
                            SplashInput::class
                        )

                        handleShowDialog(activity = activity, type = type)

                        updateActivityType(type = null)
                    } catch (e: Exception) {
                    }
                } else {
                    if (activity is MainIAPActivity) {
                        activity.finishWithIAPState(isSuccess = true)
                    } else {
                        activity.finish()
                    }
                }
            }
        }
    }

    private fun handleShowDialog(activity: AppCompatActivity, type: SubscriptionActivityType) {
//        if (type == SubscriptionActivityType.SubscriptionExpire || type == SubscriptionActivityType.CancelPurchase)
//            return
//
//        var iconRes = R.drawable.ic_premium_success
//        var titleRes = R.string.you_are_all_set
//        var messageRes = R.string.your_purchase_was_successful
//        var textButtonRes = R.string.done
//
//        if (type == SubscriptionActivityType.SubscriptionFailed) {
//            iconRes = R.drawable.ic_premium_failed
//            titleRes = R.string.something_went_wrong
//            messageRes = R.string.your_purchase_could_not_be_completed
//            textButtonRes = R.string.done
//        }
//
//        PremiumStateDialog.newInstance(
//            iconRes = iconRes,
//            titleRes = titleRes,
//            messageRes = messageRes,
//            textButtonRes = textButtonRes,
//            onClick = {
//
//            }
//        ).showOnce(activity.supportFragmentManager, "PremiumStateDialog")
    }

    private val String.expiryTimeMillis: Long
        get() {
            val days = extractDays(durationString = this)
            return days * 86400000L
        }

    private var activityCounter = 0

    init {
        scope.launch {
            loadDataPreferences()
        }
    }

    init {
        observerActivityLifecycle(context = context)
    }

    init {
        navigateActivityType.observeForever(navigateObserver)
    }

    init {
        scope.launch {
            combine(
                repository.purchaseInApp, repository.purchaseSubs
            ) { inApps, subs ->

                val inAppsInfo = inApps.toInAppSubscriptionInfo()
                val subsInfo = subs.toSubscriptionInfo(item = repository.itemPurchase?.second)

                val result = getSubscriptionInfoAfterCheck(inApps = inAppsInfo, subs = subsInfo)

                updateSubscriptions(subscriptions = result)

                result
            }.combine(purchaseSuccess) { subsInfoItems, purchaseResult ->
                subsInfoItems to purchaseResult
            }.collect {
                collectDialogType(list = it.first, purchaseResult = it.second)
            }
        }
    }

    init {
        isPremium.distinctUntilChanged().observeForever {
            //Log.d("MTHAI", "isPremium: $it")
            setSupportAd(supportAds = !it)
        }
    }

    override fun hasPremium(): Boolean {
        return isPremium.value == true
    }

    override suspend fun hasUserNeverPurchased(): Boolean {
        return tokenPurchaseSuccess.first().isEmpty()
    }

    override fun hasSubscriptionOnHold(): Boolean {
        return subscriptionActivityType.value == SubscriptionActivityType.CancelPurchase
    }

    override fun hasSubscriptionNotice(): Boolean {
//        return subscriptionActivityType.value != null
        return false
    }

    override fun hasPackageSale(callback: (isExist: Boolean) -> Unit) {
        scope.launch {
            val items = repository.productDetailItems.firstOrNull()

            callback(items?.any { it.packageType in packageSales } == true)
        }
    }

    override fun hasPackageSale(packageType: PackageType, callback: (isExist: Boolean) -> Unit) {
        scope.launch {
            val items = repository.productDetailItems.firstOrNull()

            callback(items?.any { it.packageType == packageType } == true)

        }
    }

    override fun reConnection() = repository.reConnection()

    override fun buy(
        activity: Activity, startBuy: StartIAPType, item: ProductDetailItem
    ) {

        repository.buy(
            activity = activity, startBuy = startBuy, item = item
        )
    }

    override fun setItemPurchaseSelected(startBuy: StartIAPType, item: ProductDetailItem?) =
        repository.setItemPurchaseSelected(startBuy = startBuy, item = item)

    override suspend fun getPackage(packageType: PackageType): ProductDetailItem? {
        return repository.getProductDetailItem(packageType = packageType)
    }

    private fun observerActivityLifecycle(context: Context) {
        (context.applicationContext as Application).registerActivityLifecycleCallbacks(object :
            Application.ActivityLifecycleCallbacks {

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                if (activityCounter == 0) {
                    repository.reConnection()
                    refreshPurchase()
                }

                activityCounter++
            }

            override fun onActivityStarted(activity: Activity) {

            }

            override fun onActivityResumed(activity: Activity) {
                if (activity !is MainActivity && activity !is MainIAPActivity) return

                repository.refreshPurchase()

                scope.launch {
                    loadDataPreferences()
                }
                attachActivity(activity = activity)
            }

            override fun onActivityPaused(activity: Activity) {

            }

            override fun onActivityStopped(activity: Activity) {

            }

            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {

            }

            override fun onActivityDestroyed(activity: Activity) {
                activityCounter--

                if (activityCounter == 0) {
                    repository.stopConnection()
                }
            }

        })
    }

    private fun refreshPurchase() {
        scope.launch {
            val inAppsInfo = repository.purchaseInApp.firstOrNull().toInAppSubscriptionInfo()
            val subsInfo = repository.purchaseSubs.firstOrNull()
                .toSubscriptionInfo(item = repository.itemPurchase?.second)

            val result = getSubscriptionInfoAfterCheck(inApps = inAppsInfo, subs = subsInfo)

            updateSubscriptions(subscriptions = result)

            collectDialogType(list = result, purchaseResult = null)
        }
    }

    private suspend fun combineSaveData(
        inApps: List<SubscriptionInfo>, subs: List<SubscriptionInfo>
    ): MutableList<SubscriptionInfo> {
        val temps = mutableListOf<SubscriptionInfo>()

        val saveList = subscriptionInfo.first().decodeBase64JsonOrEmpty().toSubscriptions()

        val oldSubsInfo = saveList.find {
            productIdSubs.any { id -> it.productId.contains(id) }
        }

        val oldInApps = saveList.filter {
            !productIdSubs.any { id -> it.productId.contains(id) }
        }

        temps.addAll(inApps.ifEmpty { oldInApps })

        if (subs.isNotEmpty()) {
            val newSubs = subs.map {
                if (oldSubsInfo != null && (oldSubsInfo.productId == it.productId || oldSubsInfo.basePlanId == it.basePlanId)) {

                    val priceAmountMicros =
                        if (it.priceAmountMicros == -1L) oldSubsInfo.priceAmountMicros
                        else it.priceAmountMicros

                    val billingPeriod = it.billingPeriod.ifEmpty { oldSubsInfo.billingPeriod }

                    val formattedPrice = it.formattedPrice.ifEmpty { oldSubsInfo.formattedPrice }

                    var expiryTime = if (it.expiryTime == -1L) oldSubsInfo.expiryTime
                    else it.expiryTime

                    // check renew => update expiryTime from SubscriptionInfo
                    if (billingPeriod.isNotEmpty() && expiryTime != 1L && expiryTime < System.currentTimeMillis()) {
                        expiryTime += billingPeriod.expiryTimeMillis
                    }

                    it.copy(
                        expiryTime = expiryTime,
                        billingPeriod = billingPeriod,
                        formattedPrice = formattedPrice,
                        priceAmountMicros = priceAmountMicros
                    )
                } else {
                    it
                }
            }

            temps.addAll(newSubs)
        } else {
            if (oldSubsInfo != null && !oldSubsInfo.isExpiry) {
                temps.add(oldSubsInfo)
            }
        }

        return temps
    }

    private suspend fun List<Purchase?>?.toInAppSubscriptionInfo(): List<SubscriptionInfo> {
        if (this.isNullOrEmpty()) return emptyList()

        val temps = mutableListOf<SubscriptionInfo>()

        this.forEach { purchase ->
            if (purchase == null) return@forEach

            val productId = if (purchase.products.isNotEmpty()) purchase.products.first()
            else PRODUCT_LIFETIME_ID

            val productDetail =
                repository.queryProductDetails(productId, BillingClient.ProductType.INAPP)

            val onTimeDetails = productDetail?.oneTimePurchaseOfferDetails

            temps.add(
                SubscriptionInfo(
                    productId = productId,
                    productType = BillingClient.ProductType.INAPP,
                    isAutoRenewal = purchase.isAutoRenewing,
                    purchaseState = purchase.purchaseState,
                    purchaseTime = purchase.purchaseTime,
                    expiryTime = -1L,
                    billingPeriod = "",
                    formattedPrice = onTimeDetails?.formattedPrice ?: "",
                    priceAmountMicros = onTimeDetails?.priceAmountMicros ?: 0,
                    isPremium = true,
                )
            )
        }

        return temps
    }

    private fun List<Purchase?>?.toSubscriptionInfo(item: ProductDetailItem?): List<SubscriptionInfo> {

        if (this.isNullOrEmpty()) return emptyList()

        val temps = mutableListOf<SubscriptionInfo>()

        forEach { purchase ->
            if (purchase == null) return@forEach

            val purchaseID = purchase.products.first()

            val subsOffer = item?.subsOfferDetails

            val pricingPhases = subsOffer?.pricingPhases?.pricingPhaseList
            val pricingPhaseOriginal = pricingPhases?.get(pricingPhases.size - 1)

            val expiryTimeMillis = pricingPhaseOriginal?.billingPeriod?.expiryTimeMillis

            val expiryTime = if (expiryTimeMillis != null) purchase.purchaseTime + expiryTimeMillis
            else -1L

            temps.add(
                SubscriptionInfo(
                    productId = item?.productDetails?.productId ?: purchaseID,
                    basePlanId = item?.planId ?: purchaseID,
                    productType = BillingClient.ProductType.SUBS,
                    isAutoRenewal = purchase.isAutoRenewing,
                    purchaseState = purchase.purchaseState,
                    purchaseTime = purchase.purchaseTime,
                    expiryTime = expiryTime,
                    billingPeriod = pricingPhaseOriginal?.billingPeriod ?: "",
                    formattedPrice = pricingPhaseOriginal?.formattedPrice ?: "",
                    priceAmountMicros = pricingPhaseOriginal?.priceAmountMicros ?: -1L,
                    isPremium = true,
                )
            )
        }
        return temps
    }

    private suspend fun getSubscriptionInfoAfterCheck(
        inApps: List<SubscriptionInfo>, subs: List<SubscriptionInfo>
    ): List<SubscriptionInfo> = combineSaveData(inApps, subs).findSubsNotExpiry()

    private fun List<SubscriptionInfo>?.findSubscriptionSelected(): SubscriptionInfo? {

        if (this.isNullOrEmpty()) return null

        val lifeTime = find {
            productIdInApps.any { id ->
                it.productId.contains(id)
            }
        }

        if (lifeTime != null) return lifeTime

        val subscription = find {
            productIdSubs.any { id ->
                it.productId.contains(id)
            }
        }

        if (subscription != null) return subscription

        return first()
    }

    private suspend fun loadDataPreferences() {
        val subs = subscriptionInfo.first().decodeBase64JsonOrEmpty().toSubscriptions()
        updateSubscriptions(subscriptions = subs)
    }

    private fun attachActivity(activity: AppCompatActivity) {
        if (componentActivity.value?.hashCode() == activity.hashCode()) return

        componentActivity.value = activity
    }

    private fun String.toSubscriptions(): List<SubscriptionInfo> {
        return this.toSubscriptionList().findSubsNotExpiry()
    }

    private suspend fun collectDialogType(
        list: List<SubscriptionInfo>, purchaseResult: PurchaseResult?
    ) {
        val oldSaveList = subscriptionInfo.first().decodeBase64JsonOrEmpty().toSubscriptionList()
        val encodedSubscription = Base64.encodeToString(list.toJson().toByteArray(), Base64.NO_WRAP)

        updateSubscriptionInfo(encodedSubscription)

        if (list.isNotEmpty() || purchaseResult != null) {
            val purchase = purchaseResult?.purchase

            if (purchaseResult != null && purchase != null && purchase.purchaseToken != tokenPurchaseSuccess.first()) {
                updateActivityType(type = SubscriptionActivityType.SubscriptionSuccess)

                updateTokenPurchaseSuccess(purchase.purchaseToken)
                //productDetail buy
                val itemPurchase = repository.itemPurchase

                if (itemPurchase != null) {
                    val startIAPType = itemPurchase.first
                    val productDetailItem = itemPurchase.second

                    AdjustIAPManager.trackIAP(
                        purchase = purchase,
                        itemPurchased = ItemPurchased(
                            typeFeature = startIAPType,
                            item = productDetailItem
                        )
                    )
                }

                return
            }

            if (checkShowReasonDialog(infos = list)) {
                updateActivityType(type = SubscriptionActivityType.CancelPurchase)
                return
            }

            val currentDay = formatDateFromTimestamp(timestamp = System.currentTimeMillis())

            if (checkPaymentFailed(infos = list, currentDay = currentDay)) {
                updateActivityType(type = SubscriptionActivityType.SubscriptionFailed)
                return
            }
        }

        if (checkShowExpiryDialog(list = list, oldSaveList = oldSaveList)) {
            updateActivityType(type = SubscriptionActivityType.SubscriptionExpire)
            return
        }

//        updateActivityType(type = null)
    }

    private suspend fun checkPaymentFailed(
        infos: List<SubscriptionInfo>, currentDay: String
    ): Boolean {
        val purchasedSubs = infos.filter {
            val state = it.purchaseState
            state == Purchase.PurchaseState.PENDING || state == Purchase.PurchaseState.UNSPECIFIED_STATE
        }

        val lastDaySaved = lastDayShowPending.first()

        if (purchasedSubs.isNotEmpty() && lastDaySaved != currentDay) {

            updateLastDayShowPending(currentDay)

            return true
        }

        return false
    }

    private fun checkShowExpiryDialog(
        list: List<SubscriptionInfo>, oldSaveList: List<SubscriptionInfo>
    ): Boolean {
        val saveListNotExpire = oldSaveList.findSubsNotExpiry()

        return list.isEmpty() && oldSaveList.isNotEmpty() && saveListNotExpire.isEmpty()
    }

    private suspend fun checkShowReasonDialog(
        infos: List<SubscriptionInfo>,
    ): Boolean {
        val size = infos.filter {
            it.productType == BillingClient.ProductType.SUBS && !it.isAutoRenewal
        }.size

        val oldCancelSubCount = cancelSubsCount.first()

        val result = size > 0 && size > oldCancelSubCount

        if (result) {
            updateCancelSubsCount(size)
        }

        return result
    }

    private fun List<SubscriptionInfo>.findSubsNotExpiry(): List<SubscriptionInfo> {

        val temps = mutableListOf<SubscriptionInfo>()

        val lifeTime = this.find {
            productIdInApps.any { id -> it.productId.contains(id) }
        }

        val subscription = this.find {
            val isExist = productIdSubs.any { id -> it.productId.contains(id) }

            (isExist) && (!it.isExpiry || it.expiryTime == -1L)
        }
        if (lifeTime != null) {
            temps.add(lifeTime)
        }

        if (subscription != null) {
            temps.add(subscription)
        }

        return temps
    }

    private fun formatDateFromTimestamp(timestamp: Long): String {
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return format.format(Date(timestamp))
    }

    private fun updateSubscriptions(subscriptions: List<SubscriptionInfo>) {
        _subscriptions.value = subscriptions
    }

    private fun updateActivityType(type: SubscriptionActivityType?) {
        subscriptionActivityType.value = type
    }

    companion object {
        @Volatile
        private var instance: BillingManagerImpl? = null

        fun getInstance(context: Context? = null): BillingManagerImpl {
            return instance ?: synchronized(this) {
                instance ?: context?.let {
                    BillingManagerImpl(it.applicationContext).also { manager ->
                        instance = manager
                    }
                } ?: throw IllegalStateException("BillingManagerImpl must be created first!")
            }
        }

        fun create(context: Context) {
            getInstance(context)
        }
    }

}
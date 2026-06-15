package com.looper.base.iap.core.wrapper

import android.app.Activity
import android.content.Context
import android.util.Log
import android.widget.Toast
import com.android.billingclient.api.AccountIdentifiers
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.ProductDetailsResult
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchaseHistoryRecord
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.queryProductDetails
import com.looper.base.R
import com.looper.base.iap.core.data.BillingState
import com.looper.base.iap.core.data.PurchaseResult
import com.looper.base.iap.core.data.PurchaseStatus
import com.looper.base.iap.core.data.packageInfoItems
import com.looper.base.iap.utils.productIdInApps
import com.looper.base.iap.utils.productIdNonConsumable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.text.contains

private const val MAX_RETRY = 5

class BillingWrapperImpl(private val context: Context) : IBillingWrapper, PurchasesUpdatedListener {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var billingClient: BillingClient? = null

    private val _purchaseResult = MutableStateFlow<PurchaseResult?>(null)
    override val purchaseResult: Flow<PurchaseResult?> = _purchaseResult

    private val _billingState = MutableStateFlow(BillingState.Loading)
    override val billingState: Flow<BillingState> = _billingState

    private val _purchaseInApps = MutableStateFlow<List<Purchase?>?>(emptyList())
    override val purchaseInApps: Flow<List<Purchase?>?> = _purchaseInApps

    private val _purchaseSubscriptions = MutableStateFlow<List<Purchase?>?>(emptyList())
    override val purchaseSubscriptions: Flow<List<Purchase?>?> = _purchaseSubscriptions

    private val _productDetailsMap = MutableStateFlow<Map<String, ProductDetails>?>(null)
    override val productDetailsMap: Flow<Map<String, ProductDetails>?> = _productDetailsMap

    private var reConnectionJob: Job? = null

    override fun startConnection() {
        scope.launch(Dispatchers.IO) {

            createInstanceBillingClient()

            val client = billingClient ?: return@launch

            if (!client.isReady) {
                client.startConnection(object : BillingClientStateListener {
                    override fun onBillingServiceDisconnected() {
                        reConnection()
                    }

                    override fun onBillingSetupFinished(billingResult: BillingResult) {
                        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                            queryData()
                        } else {
                            updateBillingState(value = BillingState.Error)
                        }
                    }
                })
            }
        }
    }

    override fun stopConnection() {
        val client = billingClient ?: return

        if (client.isReady) {
            client.endConnection()
        }
    }

    override fun queryPurchase() {
        val client = billingClient ?: return

        if (client.isReady) {
            queryPurchases(productType = BillingClient.ProductType.INAPP)
            queryPurchases(productType = BillingClient.ProductType.SUBS)
        }
    }

    override fun buy(
        activity: Activity,
        params: BillingFlowParams
    ) {
        val client = billingClient ?: return

        _purchaseResult.value = null


        client.ready {
            if (it) {
                client.launchBillingFlow(activity, params)
            } else {
                Toast.makeText(activity, R.string.service_unavailable, Toast.LENGTH_SHORT)
                    .show()
            }
        }
    }

    override fun reConnection() {
        val client = billingClient

        if (client == null || !client.isReady) {

            if (reConnectionJob?.isActive == true) return

            reConnectionJob = scope.launch(Dispatchers.IO) {
                if (retryBillingServiceConnection())
                    queryData()
            }
        } else {
            queryData()
        }
    }

    override suspend fun queryProductDetails(
        productId: String,
        productType: String
    ): ProductDetails? = withContext(Dispatchers.IO) {

        val client = billingClient ?: return@withContext null

        val products = mutableListOf<QueryProductDetailsParams.Product>().apply {
            add(
                QueryProductDetailsParams.Product.newBuilder().setProductId(productId)
                    .setProductType(productType).build()
            )
        }

        val params = QueryProductDetailsParams.newBuilder()
        val productDetailsResult = client.queryProductDetails(
            params.setProductList(products).build()
        )

        val responseCode = productDetailsResult.billingResult.responseCode
        val productDetailsList = productDetailsResult.productDetailsList

        if (responseCode == BillingClient.BillingResponseCode.OK) {
            productDetailsList?.find { it.productId.contains(productId) }
        } else {
            null
        }
    }

    override fun onPurchasesUpdated(
        p0: BillingResult,
        p1: List<Purchase?>?
    ) {
        if (p0.responseCode == BillingClient.BillingResponseCode.OK && !p1.isNullOrEmpty()) {

            p1.forEachIndexed { index, purchase ->
                Log.d("MTHAI", "onPurchasesUpdated index $index: ${purchase?.toText()}")
            }

            if (p1.any { productIdInApps.contains(it?.products[0]) }) {
                _purchaseInApps.value = p1
            } else {
                _purchaseSubscriptions.value = p1
            }

            for (purchase in p1) {
                handlePurchase(purchase)
            }
        }
    }

    private fun createInstanceBillingClient() {
        billingClient = BillingClient.newBuilder(context)
            .setListener(this@BillingWrapperImpl)
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
            )
            .build()
    }

    private suspend fun retryBillingServiceConnection(): Boolean = withContext(Dispatchers.IO) {
        var tries = 0
        var isConnected = false

        createInstanceBillingClient()

        val client = billingClient ?: return@withContext false

        while (tries < MAX_RETRY && !isConnected) {
            tries++

            try {
                val result = suspendCancellableCoroutine { continuation ->
                    client.startConnection(object : BillingClientStateListener {
                        override fun onBillingSetupFinished(billingResult: BillingResult) {
                            if (continuation.isActive) {
                                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                                    continuation.resume(true)
                                    isConnected = true
                                } else {
                                    continuation.resume(false)
                                }
                            }
                        }

                        override fun onBillingServiceDisconnected() {
                            // Handle disconnection if needed
                        }
                    })
                }

                if (result) {
                    return@withContext true
                }
            } catch (e: Exception) {
                // Handle exceptions if needed
            }
        }

        return@withContext false
    }

    private fun queryData() {
        updateBillingState(value = BillingState.Loading)

        queryPurchase()

        scope.launch {
            queryProductDetails()
        }
    }

    private suspend fun queryProductDetails() = withContext(Dispatchers.IO) {
        val client = billingClient ?: return@withContext

        val groupBy = packageInfoItems.groupBy { it.productType }

        try {
            groupBy.onEach {
                val params = QueryProductDetailsParams.newBuilder().apply {
                    val products = mutableListOf<QueryProductDetailsParams.Product>()

                    it.value.forEach { packageInfo ->
                        products.add(
                            QueryProductDetailsParams.Product.newBuilder()
                                .setProductId(packageInfo.id)
                                .setProductType(packageInfo.productType)
                                .build()
                        )
                    }
                    setProductList(products)
                }

                val response = client.queryProductDetails(params.build())

                val detailsMap = response.getProductDetailsMap()

                val newMap = addToProductDetailsMap(newMap = detailsMap)

                updateProductDetailsMap(value = newMap)
            }

            updateBillingState(BillingState.Success)
        } catch (e: Exception) {
            updateBillingState(BillingState.Error)
        }
    }

    private fun queryPurchases(productType: String) {
        val client = billingClient ?: return

        client.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(productType)
                .build()
        ) { billingResult, purchaseList ->

            try {
                val purchases = getPurchase(
                    billingResult = billingResult,
                    purchases = purchaseList,
                    productType = productType
                )

                for (purchase in purchases) {
                    handlePurchase(purchase)
                }

                if (productType == BillingClient.ProductType.SUBS) {
                    _purchaseSubscriptions.value = purchases
                } else {
                    _purchaseInApps.value = purchases
                }

            } catch (e: Exception) {
                updateBillingState(value = BillingState.Error)
            }
        }
    }

    private fun getPurchase(
        billingResult: BillingResult,
        purchases: List<Purchase>,
        productType: String
    ): List<Purchase> {
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
//            purchases.forEachIndexed { index, purchase ->
//                Log.d(
//                    "MTHAI",
//                    "queryPurchases index $index: ${purchase.toText()} --  productType:$productType"
//                )
//            }

            return purchases
        }

        throw NullPointerException("Purchase is a NullPointerException")
    }

    private fun handlePurchase(purchase: Purchase?) {
        purchase?.let {
            if (purchase.products.isNotEmpty()) {
                if (productIdNonConsumable.contains(purchase.products[0])) {
                    acknowledgePurchases(it)
                }
            }
        }
    }

    private fun ProductDetailsResult.getProductDetailsMap(): Map<String, ProductDetails> {
        val billingResult = this.billingResult
        val productDetails = this.productDetailsList

        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && !productDetails.isNullOrEmpty()) {

//            productDetails.forEachIndexed { index, productDetail ->
//                Log.d("MTHAI", "onProductDetailsResponse index $index: ${productDetail.toText()}")
//            }

            return productDetails.associateBy {
                it.productId
            }
        }

        throw NullPointerException("ProductDetailsResult is a NullPointerException")
    }

    private fun addToProductDetailsMap(newMap: Map<String, ProductDetails>): Map<String, ProductDetails> {
        return mutableMapOf<String, ProductDetails>().apply {

            val oldMap = _productDetailsMap.value

            if (oldMap != null)
                putAll(oldMap)

            putAll(newMap)
        }
    }

    private fun acknowledgePurchases(purchase: Purchase) {
        val client = billingClient ?: return

        when (purchase.purchaseState) {
            Purchase.PurchaseState.PURCHASED -> {

                if (purchase.isAcknowledged) {
                    updatePurchaseStatus(
                        PurchaseResult(
                            purchase = purchase,
                            status = PurchaseStatus.Purchased
                        )
                    )

                    return
                }

                val params = AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                    .build()

                client.acknowledgePurchase(params) { billingResult ->
                    if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                        updatePurchaseStatus(
                            PurchaseResult(
                                purchase = purchase,
                                status = PurchaseStatus.Purchased
                            )
                        )
                    }
                }
            }

            Purchase.PurchaseState.PENDING -> {
                updatePurchaseStatus(PurchaseResult(status = PurchaseStatus.Pending))
            }

            else -> {
                updatePurchaseStatus(PurchaseResult(status = PurchaseStatus.Unspecified))
            }
        }
    }

    private fun updateProductDetailsMap(value: Map<String, ProductDetails>) {
        _productDetailsMap.value = value
    }

    private fun updateBillingState(value: BillingState) {
        _billingState.value = value
    }

    private fun updatePurchaseStatus(value: PurchaseResult) {
        _purchaseResult.value = value
    }
}


/**
 *
 *
 * Logcat Test
 *
 * */
private fun BillingClient.ready(block: (isReady: Boolean) -> Unit = {}) {
    if (this.isReady) {
        block(true)
    } else {
        block(false)
    }
}

private fun ProductDetails.toText(): String {
    return """
        productId: $productId
        name: $name
        description: $description
        productType: $productType
        title: $title
        oneTimePurchaseOfferDetails: ${oneTimePurchaseOfferDetails?.toText()}
        subscriptionOfferDetails: ${subscriptionOfferDetails?.toText()}
    """
}

private fun List<ProductDetails.SubscriptionOfferDetails>.toText(): String {
    return this.mapIndexed { index, subscriptionOfferDetails ->
        "\nSubscriptionOfferIndex: $index --- subscriptionOfferDetails: detail below ${subscriptionOfferDetails.toText()}"
    }.joinToString("\n")
}

private fun ProductDetails.SubscriptionOfferDetails.toText(): String {
    return """
        - basePlanId: $basePlanId
        - offerId: $offerId
        - offerTags: ${offerTags.joinToString(", ")}
        - offerToken: $offerToken
        - pricingPhases: ${
        pricingPhases.pricingPhaseList.mapIndexed { index, pricingPhase ->
            "\npricingPhaseIndex: $index --- pricingPhase: detail below ${pricingPhase.toText()}"
        }.joinToString("\n")
    }
    """
}

private fun ProductDetails.PricingPhase.toText(): String {
    return """
        + billingCycleCount: $billingCycleCount
        + formattedPrice: $formattedPrice
        + billingPeriod: $billingPeriod
        + priceAmountMicros: $priceAmountMicros
        + priceCurrencyCode: $priceCurrencyCode
        + recurrenceMode: $recurrenceMode
    """
}

private fun ProductDetails.OneTimePurchaseOfferDetails.toText(): String {
    return """
        - priceAmountMicros: $priceAmountMicros
        - formattedPrice: $formattedPrice
        - priceCurrencyCode: $priceCurrencyCode
    """
}

private fun Purchase.toText(): String {
    return """
        orderId: $orderId
        packageName: $packageName
        products: ${products.joinToString(", ")}
        isAcknowledged: $isAcknowledged
        developerPayload: $developerPayload
        quantity: $quantity
        isAutoRenewing: $isAutoRenewing
        signature: $signature
        purchaseState: $purchaseState
        purchaseTime: $purchaseTime
        purchaseToken: $purchaseToken
        originalJson: $originalJson
        accountIdentifiers: detail below ${accountIdentifiers?.toText()}
    """
}

private fun PurchaseHistoryRecord.toText(): String {
    return """
        products: ${products.joinToString(", ")}
        developerPayload: $developerPayload
        quantity: $quantity
        signature: $signature
        purchaseTime: $purchaseTime
        purchaseToken: $purchaseToken
        originalJson: $originalJson
    """
}

private fun AccountIdentifiers.toText(): String {
    return """
        obfuscatedAccountId: $obfuscatedAccountId
        obfuscatedProfileId: $obfuscatedProfileId
    """
}

// ERROR Purchase
private fun Int.getErrorStr(): String {
    return when (this) {
        -3 -> "SERVICE_TIMEOUT"
        -2 -> "FEATURE_NOT_SUPPORTED"
        -1 -> "SERVICE_DISCONNECTED"
        0 -> "OK"
        1 -> "USER_CANCELED"
        2 -> "SERVICE_UNAVAILABLE"
        3 -> "BILLING_UNAVAILABLE"
        4 -> "ITEM_UNAVAILABLE"
        5 -> "DEVELOPER_ERROR"
        6 -> "ERROR"
        7 -> "ITEM_ALREADY_OWNED"
        else -> "ITEM_NOT_OWNED"
    }
}
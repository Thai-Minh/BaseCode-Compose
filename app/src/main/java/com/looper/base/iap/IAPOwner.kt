/**
 *Created by: ThaiND
 */

package com.looper.base.iap

import android.app.Activity
import android.content.Context
import android.util.Log
import com.looper.base.iap.data.IapActionResult
import com.looper.base.iap.data.IapReadiness
import com.looper.base.iap.data.IapSessionState
import com.looper.iap.BillingManagerImpl
import com.looper.iap.OrigoIapSdk
import com.looper.iap.model.BillingState
import com.looper.iap.model.IapConfig
import com.looper.iap.model.ProductDetailItem
import com.looper.iap.model.PurchaseResult
import com.looper.iap.model.PurchaseStatus
import com.looper.iap.model.StartIAPType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/** Stable, synchronous facade; only this application-owned object can create or access the SDK. */
class IAPOwner(
    context: Context,
    private val scope: CoroutineScope,
    private val catalogLoader: SessionCatalogLoader,
) {
    private val applicationContext = context.applicationContext
    private val reconciliationMutex = Mutex()
    private val tracker = IAPTracker()
    private var manager: BillingManagerImpl? = null
    private var initialization: Job? = null
    private var selection: SubscriptionSelection? = null

    val state: StateFlow<IapSessionState>
        field = MutableStateFlow(value = IapSessionState())

    val isPremium: StateFlow<Boolean>
        field = MutableStateFlow(value = false)

    val products: StateFlow<List<ProductDetailItem>>
        field = MutableStateFlow(value = emptyList())

    val purchaseEvents: SharedFlow<PurchaseResult>
        field = MutableSharedFlow(extraBufferCapacity = 1)

    // Only ActivityResult callbacks for a completed paywall purchase add to this queue.
    // Entitlement reconciliation and restores never enqueue a congratulations dialog.
    val pendingPurchaseSuccesses: StateFlow<Int>
        field = MutableStateFlow(value = 0)

    fun recordSuccessfulPurchase() {
        pendingPurchaseSuccesses.update { count -> count + 1 }
    }

    fun consumeSuccessfulPurchase() {
        pendingPurchaseSuccesses.update { count -> (count - 1).coerceAtLeast(minimumValue = 0) }
    }

    @Synchronized
    fun start() {
        if (initialization?.isActive == true || manager != null) {
            return
        }

        initialization = scope.launch {
            try {
                val session = catalogLoader.load()
                selection = session.selection
                state.update { current -> current.copy(readiness = IapReadiness.Connecting) }

                val catalog = RecognitionCatalog.merge(selection = session.selection)

                val sdk = OrigoIapSdk.create(
                    context = applicationContext,
                    config = IapConfig(
                        catalog = catalog,
                        dataStoreName = "iap_v2",
                        tracker = tracker,
                        // Never promote a legacy boolean or stale SDK cache into verified premium.
                        cacheEntitlements = false,
                    ),
                )
                manager = sdk
                observe(sdk = sdk)
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                state.update { current ->
                    current.copy(
                        readiness = IapReadiness.Unavailable,
                        message = "Purchases unavailable. Retry."
                    )
                }
            }
        }
    }

    fun retry() {
        val sdk = manager

        if (sdk == null) {
            start()
            return
        }

        state.update { current ->
            current.copy(
                readiness = IapReadiness.Connecting,
                message = null
            )
        }
        sdk.reConnection()
    }

    fun buy(
        activity: Activity,
        item: ProductDetailItem,
        startBuy: StartIAPType = StartIAPType.Onboarding,
    ): IapActionResult {
        val sdk = manager

        if (sdk == null || state.value.readiness != IapReadiness.Ready || item !in products.value) {
            return IapActionResult.NotReady
        }

        tracker.select(item = item)
        sdk.buy(
            activity = activity,
            startBuy = startBuy,
            item = item,
        )

        return IapActionResult.Started
    }

    suspend fun restore(): IapActionResult {
        val sdk = manager

        if (sdk == null || state.value.readiness != IapReadiness.Ready) {
            return IapActionResult.NotReady
        }

        return reconcile(sdk = sdk)
    }

    private fun observe(sdk: BillingManagerImpl) {
        scope.launch {
            sdk.purchaseEvents.collect { result ->
                purchaseEvents.emit(result)
            }
        }

        scope.launch {
            sdk.productDetailItemsFlow.collect { items ->
                val availableItems = items.orEmpty().map { item ->
                    AvailablePaywallOffer(
                        productId = item.identity.productId,
                        basePlanId = item.identity.basePlanId,
                        offerId = item.identity.offerId,
                        item = item,
                    )
                }

                products.value =
                    selection?.selectAvailableOffers(available = availableItems).orEmpty()

                val availablePackages = products.value.map { item ->
                    "${item.packageType.key} (productId=${item.identity.productId}, " +
                            "basePlanId=${item.identity.basePlanId}, offerId=${item.identity.offerId}, " +
                            "kind=${item.offerKind}, trialDays=${item.freeTrialDay ?: 0})"
                }

                Log.i("AppIapCatalog", "Available paywall packages: $availablePackages")
            }
        }

        scope.launch {
            sdk.entitlementStateFlow.collect { entitlement ->
                if (entitlement.sources.any { source -> source.entitlementId == "premium" && source.verified }) {
                    isPremium.value = true
                }
            }
        }

        scope.launch {
            sdk.billingStateFlow.collectLatest { billing ->
                when (billing) {
                    BillingState.Loading -> state.update { current ->
                        current.copy(readiness = IapReadiness.Connecting)
                    }

                    BillingState.Error -> state.update { current ->
                        current.copy(
                            readiness = IapReadiness.Unavailable,
                            message = "Google Play unavailable. Retry."
                        )
                    }

                    BillingState.Success -> reconcile(sdk = sdk)
                }
            }
        }
    }

    private suspend fun reconcile(sdk: BillingManagerImpl): IapActionResult =
        reconciliationMutex.withLock {
            // SDK BillingState.Success does not propagate a failed automatic restore. Query explicitly.
            val result = withTimeoutOrNull(timeMillis = 8_000L) { sdk.restorePurchases() }
            val purchases = result?.getOrNull()

            if (purchases == null || purchases.any { purchase ->
                    purchase.status == PurchaseStatus.Failed || purchase.status == PurchaseStatus.Unspecified
                }) {
                state.update { current ->
                    current.copy(
                        readiness = IapReadiness.Unavailable,
                        message = "Could not reconcile purchases. Retry."
                    )
                }

                return@withLock IapActionResult.Failed
            }

            val active = sdk.entitlementStateFlow.value.sources.any { source ->
                source.entitlementId == "premium" && source.verified
            }

            isPremium.value = active

            state.update { current ->
                current.copy(
                    readiness = IapReadiness.Ready,
                    message = null,
                )
            }

            IapActionResult.Started
        }
}

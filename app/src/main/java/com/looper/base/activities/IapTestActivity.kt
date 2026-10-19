/**
*Created by: ThaiND
*/

package com.looper.base.activities

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.looper.base.MyApplication
import com.looper.base.ui.theme.BaseCodeComposeTheme
import com.looper.iap.BillingManagerImpl
import com.looper.iap.model.BillingState
import com.looper.iap.model.ProductDetailItem
import com.looper.iap.model.ProductIdentity
import com.looper.iap.model.PurchaseResult
import com.looper.iap.model.PurchaseStatus
import com.looper.iap.model.StartIAPType
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class IapTestState(
    val billing: BillingState = BillingState.Loading,
    val products: List<ProductDetailItem> = emptyList(),
    val premium: Boolean = false,
    val pending: ProductIdentity? = null,
    val restoring: Boolean = false,
    val message: String? = null,
) {
    val busy: Boolean
        get() = (pending != null || restoring || billing == BillingState.Loading)

    fun onPurchaseResult(result: PurchaseResult): IapTestState {
        val expected = pending ?: return this
        val identity = result.identity

        // Play can report a cancelled billing flow without a product identity.
        if (identity == null && result.status != PurchaseStatus.Cancelled &&
            result.status != PurchaseStatus.Failed
        ) {
            return this
        }

        if (identity != null) {
            val matches = identity.productId == expected.productId &&
                identity.basePlanId == expected.basePlanId &&
                identity.offerId == expected.offerId

            if (!matches) {
                return this
            }
        }

        val message = when (result.status) {
            PurchaseStatus.Purchased -> "Play reported a purchase. TEST ONLY: not server-verified."
            PurchaseStatus.Pending -> "Payment pending. Restore after completion."
            PurchaseStatus.Cancelled -> "Purchase cancelled."
            PurchaseStatus.Failed -> "Purchase failed. Check Play account and connection."
            PurchaseStatus.Unspecified -> return this
        }

        return copy(
            pending = null,
            message = if (restoring) this.message else message,
        )
    }
}

/** Retained UI owner; the application owns the single BillingClient and its entitlement Flow. */
class IapTestViewModel(private val manager: BillingManagerImpl) : ViewModel() {
    val state: StateFlow<IapTestState>
        field = MutableStateFlow(IapTestState())

    init {
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            manager.purchaseEvents.collect { result ->
                state.update { current -> current.onPurchaseResult(result = result) }
            }
        }

        viewModelScope.launch {
            combine(manager.billingStateFlow, manager.productDetailItemsFlow, manager.isPremiumFlow) { billing, products, premium ->
                Triple(billing, products.orEmpty(), premium)
            }.collect { (billing, products, premium) ->
                state.update { it.copy(billing = billing, products = products, premium = premium) }
            }
        }
    }

    fun buy(activity: IapTestActivity, item: ProductDetailItem) {
        if (state.value.busy || state.value.billing != BillingState.Success || item !in state.value.products ||
            item.priceStr.isBlank() || item.identity.offerToken.isNullOrBlank()
        ) {
            return
        }

        state.update { it.copy(pending = item.identity) }
        manager.buy(activity, StartIAPType.Account, item)
    }

    fun restore() {
        if (state.value.busy) {
            return
        }

        state.update { it.copy(restoring = true) }
        viewModelScope.launch {
            val result = manager.restorePurchases()
            state.update { current ->
                current.copy(
                    restoring = false,
                    message = result.fold(
                        onSuccess = { purchases ->
                            "Restore complete (${purchases.size} results). Premium: ${manager.isPremiumFlow.value} (test-only, not server-verified)."
                        },
                        onFailure = { "Restore failed. Check Play account and connection." },
                    ),
                )
            }
        }
    }

    fun reconnect() {
        if (!state.value.busy) {
            manager.reConnection()
        }
    }

    fun dismiss() {
        state.update { it.copy(message = null) }
    }
}

class IapTestActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val manager = (application as MyApplication).iapManager
        val model = ViewModelProvider(
            this,
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    modelClass.cast(IapTestViewModel(manager = manager)) ?: error("Unexpected ViewModel")
            },
        )[IapTestViewModel::class.java]

        setContent {
            BaseCodeComposeTheme {
                val state by model.state.collectAsStateWithLifecycle()
                IapTestContent(
                    state = state,
                    onBuy = { item -> model.buy(activity = this, item = item) },
                    onRestore = model::restore,
                    onReconnect = model::reconnect,
                    onDismiss = model::dismiss,
                )
            }
        }
    }
}

@Composable
fun IapTestContent(
    state: IapTestState,
    onBuy: (ProductDetailItem) -> Unit,
    onRestore: () -> Unit,
    onReconnect: () -> Unit,
    onDismiss: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(all = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text("IAP test • AI Photo Editor", style = MaterialTheme.typography.headlineMedium)
            Text(if (state.premium) "Premium active (test-only, not server-verified)" else "Premium inactive")
            Text("TEST ONLY: Play purchases may incur real charges. Configure products for com.cubstudio.aiphotoeditor.photoenhance; never ship without server verification.")
        }
        item {
            when {
                state.billing == BillingState.Loading -> CircularProgressIndicator()
                state.billing == BillingState.Error -> Text("Unable to load products. Try reconnecting.")
                state.products.isEmpty() -> Text("No Play products available for this app/account.")
            }
        }
        items(state.products, key = { it.packageType.key }) { product ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(product.packageType.key, style = MaterialTheme.typography.titleLarge)
                    Text(product.priceStr.ifBlank { "Price unavailable" })
                    Button(
                        onClick = { onBuy(product) },
                        enabled = !state.busy && state.billing == BillingState.Success &&
                            product.priceStr.isNotBlank() &&
                            product.subsOfferDetails?.pricingPhases?.pricingPhaseList?.isNotEmpty() == true &&
                            !product.identity.offerToken.isNullOrBlank(),
                    ) { Text("Buy") }
                }
            }
        }
        item {
            if (state.pending != null) {
                Text("Waiting for Play payment…")
            }
            TextButton(onClick = onRestore, enabled = !state.busy && state.billing == BillingState.Success) {
                Text(if (state.restoring) "Restoring…" else "Restore purchases")
            }
            TextButton(onClick = onReconnect, enabled = !state.busy) { Text("Reconnect") }
        }
    }
    state.message?.let { message ->
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Play Billing test") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
        )
    }
}

package com.looper.base.iap.ui

import com.looper.base.iap.data.MainIAPState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.looper.iap.BillingManagerImpl
import com.looper.iap.model.BillingState
import com.looper.iap.model.ProductDetailItem
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.collections.orEmpty


class MainIAPViewModel(private val manager: BillingManagerImpl) : ViewModel() {
    val state: StateFlow<MainIAPState>
        field = MutableStateFlow(MainIAPState())

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

    fun requestPurchase(item: ProductDetailItem): Boolean {
        if (state.value.busy || state.value.billing != BillingState.Success || item !in state.value.products ||
            item.priceStr.isBlank() || item.identity.offerToken.isNullOrBlank()
        ) {
            return false
        }

        state.update { it.copy(pending = item.identity, isPurchaseSuccessful = false) }

        return true
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
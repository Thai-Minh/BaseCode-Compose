package com.looper.base.iap.ui

import android.app.Activity
import com.looper.base.iap.data.MainIAPState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.looper.base.iap.IAPOwner
import com.looper.base.iap.data.IapActionResult
import com.looper.base.iap.data.IapReadiness
import com.looper.iap.BillingManagerImpl
import com.looper.iap.model.BillingState
import com.looper.iap.model.ProductDetailItem
import com.looper.iap.model.StartIAPType
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.collections.copy
import kotlin.collections.orEmpty


class MainIAPViewModel(private val owner: IAPOwner) : ViewModel() {
    val state: StateFlow<MainIAPState>
        field = MutableStateFlow(MainIAPState())

    init {
        viewModelScope.launch {
            owner.purchaseEvents.collect { result ->
                state.update { current ->
                    current.onPurchaseResult(result = result)
                }
            }
        }

        viewModelScope.launch {
            combine(
                flow = owner.state,
                flow2 = owner.products,
                flow3 = owner.isPremium,
            ) { session, products, premium ->
                val billing = when (session.readiness) {
                    IapReadiness.LoadingConfig,
                    IapReadiness.Connecting -> BillingState.Loading

                    IapReadiness.Ready -> BillingState.Success
                    IapReadiness.Unavailable -> BillingState.Error
                }

                Triple(
                    first = billing,
                    second = products,
                    third = premium,
                )
            }.collect { (billing, products, premium) ->
                state.update { current ->
                    current.copy(
                        billing = billing,
                        products = products,
                        premium = premium,
                    )
                }
            }
        }
    }

    fun buy(activity: Activity, startBuy: StartIAPType) {
        val item = state.value.selectedItem ?: return

        if (
            state.value.busy
            || state.value.billing != BillingState.Success
            || item !in state.value.products
            || item.priceStr.isBlank()
            || item.identity.offerToken.isNullOrBlank()
        ) {
            return
        }

        state.update { it.copy(pending = item.identity, isPurchaseSuccessful = false) }

        val result = owner.buy(
            activity = activity,
            item = item,
            startBuy = startBuy,
        )
        if (result != IapActionResult.Started) {
            state.update { current ->
                current.copy(
                    pending = null,
                    message = "Purchases unavailable. Retry.",
                )
            }
        }
    }

    fun restore() {
        if (state.value.busy) {
            return
        }

        state.update { it.copy(restoring = true) }
        viewModelScope.launch {
            val result = owner.restore()

            state.update { current ->
                current.copy(
                    restoring = false,
                    message = when (result) {
                        IapActionResult.Started -> "Restore completed."
                        IapActionResult.NotReady -> "Restore unavailable. Retry."
                        IapActionResult.Failed -> "Restore failed. Check Play account and connection."
                    },
                )
            }
        }
    }

    fun reconnect() {
        if (!state.value.busy) {
            owner.retry()
        }
    }

    fun dismiss() {
        state.update { it.copy(message = null) }
    }
}
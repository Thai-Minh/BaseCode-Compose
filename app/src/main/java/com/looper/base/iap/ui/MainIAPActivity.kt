/**
*Created by: ThaiND
*/

package com.looper.base.iap.ui

import com.looper.base.iap.data.MainIAPState
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.looper.base.MyApplication
import com.looper.base.base.ui.BaseActivity
import com.looper.base.databinding.ActivityMainIapBinding
import com.looper.base.ui.theme.BaseCodeComposeTheme
import com.looper.iap.model.BillingState
import com.looper.iap.model.ProductDetailItem
import com.looper.iap.model.StartIAPType
import kotlinx.coroutines.launch

/** Hosts the SDK test UI while the application owns the shared billing manager. */
class MainIAPActivity : BaseActivity<ActivityMainIapBinding>() {
    private lateinit var model: MainIAPViewModel

    override fun createBinding(): ActivityMainIapBinding {
        return ActivityMainIapBinding.inflate(layoutInflater)
    }

    override fun initView() {
        // Also covers a plain finish without a purchase, including after recreation.
        setResult(RESULT_OK, Intent().putExtra(IAP_SUCCESS, false))

        val manager = (application as MyApplication).iapManager
        model = ViewModelProvider(
            this,
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return modelClass.cast(MainIAPViewModel(manager = manager))
                        ?: error(message = "Unexpected ViewModel")
                }
            },
        )[MainIAPViewModel::class.java]

        // BaseActivity reattaches its root: keep composition until Activity destruction.
        binding.composeView.setViewCompositionStrategy(
            strategy = ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed,
        )
        binding.composeView.setContent {
            BaseCodeComposeTheme {
                val state by model.state.collectAsStateWithLifecycle()

                // BaseActivity forwards (but does not consume/pad) insets. Compose owns
                // them here; Scaffold passes safe system-bar padding to the lazy list.
                Scaffold { contentPadding ->
                    IapTestContent(
                        state = state,
                        contentPadding = contentPadding,
                        onBuy = { item ->
                            if (model.requestPurchase(item = item)) {
                                manager.buy(
                                    activity = this@MainIAPActivity,
                                    startBuy = StartIAPType.Account,
                                    item = item,
                                )
                            }
                        },
                        onRestore = model::restore,
                        onReconnect = model::reconnect,
                        onDismiss = model::dismiss,
                        onClose = { finishWithIAPState(isSuccess = false) },
                    )
                }
            }
        }
    }

    override fun addObserver() {
        lifecycleScope.launch {
            repeatOnLifecycle(state = Lifecycle.State.STARTED) {
                model.state.collect { state ->
                    if (state.isPurchaseSuccessful && !isFinishing) {
                        finishWithIAPState(isSuccess = true)
                    }
                }
            }
        }
    }

    override fun handleOnBackPressed() {
        finishWithIAPState(isSuccess = false)
    }
}

@Composable
fun IapTestContent(
    state: MainIAPState,
    contentPadding: PaddingValues,
    onBuy: (ProductDetailItem) -> Unit,
    onRestore: () -> Unit,
    onReconnect: () -> Unit,
    onDismiss: () -> Unit,
    onClose: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(all = 24.dp),
        contentPadding = contentPadding,
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
            TextButton(onClick = onClose) { Text(text = "Close") }
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

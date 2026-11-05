/**
*Created by: ThaiND
*/

package com.looper.base.iap.ui

import android.app.Activity.RESULT_OK
import android.content.Context
import com.looper.base.iap.data.MainIAPState
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.appcompat.app.AppCompatActivity
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
import androidx.compose.material3.OutlinedTextFieldDefaults.contentPadding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.looper.base.MyApplication
import com.looper.base.base.ui.BaseActivity
import com.looper.base.base.ui.BaseActivity.Companion.IAP_SUCCESS
import com.looper.base.databinding.ActivityMainIapBinding
import com.looper.base.iap.utils.finishActivityFadeOutAnimation
import com.looper.base.iap.utils.setStartActivityFadeInAnimation
import com.looper.base.screen.AppStateProvider
import com.looper.base.ui.theme.BaseCodeComposeTheme
import com.looper.iap.model.BillingState
import com.looper.iap.model.ProductDetailItem
import com.looper.iap.model.StartIAPType
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel
import java.util.Locale
import kotlin.getValue
import kotlin.jvm.java

/** Hosts the SDK test UI while the application owns the shared billing manager. */
class MainIAPActivity : AppCompatActivity() {
    private val viewModel: MainIAPViewModel by viewModel()
    private var startIapType = StartIAPType.Onboarding

    override fun attachBaseContext(newBase: Context?) {
        val languageCode = getLanguageCodeFromIntent(intent)

        if (newBase == null || languageCode == null) {
            super.attachBaseContext(newBase)
        } else {
            super.attachBaseContext(updateLocale(newBase, languageCode))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge(
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )

        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(WindowInsetsCompat.Type.navigationBars())
        windowInsetsController.isAppearanceLightStatusBars = false

        initStartIapType()

        onBackPressedDispatcher.addCallback(this) {
            finishWithIAPState(isSuccess = false)
        }

        setContent {
            AppStateProvider {
                val state by viewModel.state.collectAsStateWithLifecycle()

                LaunchedEffect(state.isPurchaseSuccessful) {
                    if (state.isPurchaseSuccessful) {
                        finishWithIAPState(isSuccess = true)
                    }
                }

                BaseCodeComposeTheme {
                    IapTestContent(
                        state = state,
                        onBuy = { item ->
                            viewModel.buy(
                                activity = this@MainIAPActivity,
                                startBuy = startIapType,
                            )
                        },
                        onRestore = viewModel::restore,
                        onReconnect = viewModel::reconnect,
                        onDismiss = viewModel::dismiss,
                        onClose = { finishWithIAPState(isSuccess = false) },
                    )
                }
            }
        }
    }

    fun finishWithIAPState(isSuccess: Boolean) {
        if (isFinishing) {
            return
        }

        val data = Intent().apply { putExtra(IAP_SUCCESS, isSuccess) }
        setResult(RESULT_OK, data)
        finishActivityFadeOutAnimation()
    }

    private fun initStartIapType() {
        startIapType = intent?.getStringExtra(EXTRA_START_IAP_TYPE)
            ?.let { runCatching { StartIAPType.valueOf(it) }.getOrNull() }
            ?: StartIAPType.Onboarding
    }

    private fun getLanguageCodeFromIntent(intent: Intent?): String? {
        return intent?.getStringExtra(LANGUAGE_CODE_KEY)
    }

    private fun updateLocale(context: Context, language: String): Context {
        val locale = Locale(language)
        Locale.setDefault(locale)

        val configuration: Configuration = context.resources.configuration
        configuration.setLocale(locale)
        configuration.setLayoutDirection(locale)

        return context.createConfigurationContext(configuration)
    }

    companion object {
        private const val EXTRA_START_IAP_TYPE = "EXTRA_START_IAP_TYPE"
        private const val LANGUAGE_CODE_KEY = "language_code_key"

        fun start(
            activity: ComponentActivity,
            launcher: ActivityResultLauncher<Intent>,
            type: StartIAPType,
            languageCode: String? = null,
        ) {
            val intent = Intent(activity, MainIAPActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra(EXTRA_START_IAP_TYPE, type.name)

                languageCode?.let {
                    putExtra(LANGUAGE_CODE_KEY, it)
                }
            }

            launcher.launch(intent)
            activity.setStartActivityFadeInAnimation()
        }
    }
}

@Composable
fun IapTestContent(
    state: MainIAPState,
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

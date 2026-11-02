package com.looper.base.activities

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.looper.ads.ui.AppConsentManager
import com.looper.base.base.ui.BaseActivity
import com.looper.base.deeplink.IncomingLinkDispatcher
import com.looper.base.player.PlayerConnection
import com.looper.base.player.PlayerState
import com.looper.base.player.pip.PipManager
import com.looper.base.screen.MyApp
import com.looper.base.ui.theme.BaseCodeComposeTheme
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class MainActivity : AppCompatActivity() {

    lateinit var navController: NavHostController

    private val playerConnection: PlayerConnection by inject()
    private val pipManager: PipManager by inject()
    private val incomingLinkDispatcher: IncomingLinkDispatcher by inject()

    private var latestPlayerState = PlayerState()

    private val pipActionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == PipManager.ACTION_PLAY_PAUSE) {
                playerConnection.togglePlayPause()
            }
        }
    }

    private val iapLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val purchased = result.resultCode == RESULT_OK &&
                result.data?.getBooleanExtra(BaseActivity.IAP_SUCCESS, false) == true

        // Test feedback only. The SDK's entitlement flow remains the source of truth.
        Toast.makeText(
            this,
            if (purchased) "IAP test: purchase reported (not server-verified)." else "IAP test closed without a new purchase.",
            Toast.LENGTH_SHORT,
        ).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge(
            navigationBarStyle = SystemBarStyle.light(
                Color.TRANSPARENT, // Màu nền
                Color.TRANSPARENT  // Màu biểu tượng
            )
        )

        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(WindowInsetsCompat.Type.navigationBars())
        windowInsetsController.isAppearanceLightStatusBars = false

        AppConsentManager.gatherConsent(this)

        if (savedInstanceState == null) {
            incomingLinkDispatcher.handle(intent = intent)
        }

        setupPictureInPicture()

        setContent {
            val controller = rememberNavController()
            this.navController = controller

            BaseCodeComposeTheme {
                Box(
                    modifier = Modifier.fillMaxSize()
                ) {
                    MyApp(navController = navController)

                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(all = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) { }
                }
            }
        }
    }


    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        incomingLinkDispatcher.handle(intent = intent)
    }

    private fun setupPictureInPicture() {
        if (!pipManager.isSupported(context = this)) {
            return
        }

        ContextCompat.registerReceiver(
            this,
            pipActionReceiver,
            IntentFilter(PipManager.ACTION_PLAY_PAUSE),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )

        lifecycleScope.launch {
            repeatOnLifecycle(state = Lifecycle.State.STARTED) {
                playerConnection.state.collect { state ->
                    latestPlayerState = state
                    setPictureInPictureParams(
                        pipManager.buildParams(
                            context = this@MainActivity,
                            state = state
                        )
                    )
                }
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) return
        if (!pipManager.isSupported(context = this) || !pipManager.shouldEnter(latestPlayerState)) {
            return
        }
        runCatching {
            enterPictureInPictureMode(
                pipManager.buildParams(
                    context = this,
                    state = latestPlayerState
                )
            )
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration,
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        pipManager.onPipModeChanged(inPip = isInPictureInPictureMode)
    }

    override fun onDestroy() {
        if (pipManager.isSupported(context = this)) {
            runCatching {
                unregisterReceiver(pipActionReceiver)
            }
        }
        super.onDestroy()
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    BaseCodeComposeTheme {
        Greeting("Android")
    }
}
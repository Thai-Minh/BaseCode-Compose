package com.looper.base.activities

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.looper.ads.ui.AppConsentManager
import com.looper.base.screen.MyApp
import com.looper.base.ui.theme.BaseCodeComposeTheme

class MainActivity : AppCompatActivity() {

    lateinit var navController: NavHostController

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

        setContent {
            val controller = rememberNavController()
            this.navController = controller

            BaseCodeComposeTheme {
                Box(
                    modifier = Modifier.fillMaxSize()
                ) {
                    MyApp(navController = navController)

                    Button(
                        onClick = {
                            startActivity(
                                Intent(
                                    this@MainActivity,
                                    IapTestActivity::class.java
                                )
                            )
                        },
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(16.dp),
                    ) {
                        Text("IAP test")
                    }
                }
            }
        }
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
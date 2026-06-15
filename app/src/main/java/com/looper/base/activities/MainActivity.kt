package com.looper.base.activities

import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.looper.base.base.ui.BaseActivity.Companion.IAP_SUCCESS
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

        setContent {
            val controller = rememberNavController()
            this.navController = controller

            BaseCodeComposeTheme {
                MyApp(navController = navController)
            }
        }
    }

    fun finishWithResult(isSuccess: Boolean) {
        val data = android.content.Intent().apply {
            putExtra(IAP_SUCCESS, isSuccess)
        }
        setResult(RESULT_OK, data)
        finish()
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
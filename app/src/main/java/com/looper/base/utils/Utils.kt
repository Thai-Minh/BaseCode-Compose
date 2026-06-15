package com.looper.base.utils

import android.content.Context
import android.content.ContextWrapper
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.core.net.toUri

fun Context.getActivity(): ComponentActivity {
    var currentContext = this
    while (currentContext is ContextWrapper) {
        if (currentContext is ComponentActivity) {
            return currentContext
        }
        currentContext = currentContext.baseContext
    }

    throw IllegalAccessException("Can't get activity!!!!")
}

fun parseFormation(formation: String): List<Int> {
    return formation.split("-").mapNotNull { it.toIntOrNull() }
}

@Composable
fun getNavigationBar(): Dp {
    return with(LocalDensity.current) { WindowInsets.navigationBars.getBottom(this).toDp() }
}

@Composable
fun getStatusBar(): Dp {
    return with(LocalDensity.current) { WindowInsets.statusBars.getTop(this).toDp() }
}

fun openGooglePlay(context: Context) {
    val packageName = context.packageName
    val intent = android.content.Intent(
        android.content.Intent.ACTION_VIEW,
        "market://details?id=$packageName".toUri()
    ).apply {
        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    try {
        context.startActivity(intent)
    } catch (e: Exception) {
        val webIntent = android.content.Intent(
            android.content.Intent.ACTION_VIEW,
            "https://play.google.com/store/apps/details?id=$packageName".toUri()
        )
        context.startActivity(webIntent)
    }
}

fun Context.isNetworkAvailable(): Boolean {
    val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    // Lấy mạng đang hoạt động (Active Network)
    val network = connectivityManager.activeNetwork ?: return false

    // Lấy các khả năng của mạng đó (Capabilities)
    val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false

    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) && // Có khả năng truy cập internet
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) && // Kết nối đã được hệ thống xác thực là có internet thật
            (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||        // Qua Wifi
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||    // Qua Mobile Data
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ||    // Qua cáp LAN
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN))           // Qua VPN
}
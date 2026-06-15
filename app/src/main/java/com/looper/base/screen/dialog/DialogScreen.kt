package com.looper.base.screen.dialog

import android.content.Intent
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.looper.base.screen.dialog.child.NoInternetDialog

@Composable
fun DialogScreen() {

    val dialogState = LocalDialogState.current
    val context = LocalContext.current

    val showNoInternetDialog = dialogState.showNoInternetDialog
    if (showNoInternetDialog) {
        NoInternetDialog(
            onOpenSettings = {
                val intent = Intent(Settings.ACTION_WIFI_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }
        )
    }
}
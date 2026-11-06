package com.looper.base.screen.home.data

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri

class SocialAppLauncher(
    private val context: Context,
) {

    fun open(app: SocialApp) {
        if (launchApp(app = app)) return

        val packageName = app.packageNames.first()
        if (startActivity(
                intent = Intent(
                    Intent.ACTION_VIEW,
                    "market://details?id=$packageName".toUri()
                )
            )
        ) return

        startActivity(
            intent = Intent(
                Intent.ACTION_VIEW,
                "https://play.google.com/store/apps/details?id=$packageName".toUri(),
            ),
        )
    }

    private fun launchApp(app: SocialApp): Boolean {
        val intent = app.packageNames.firstNotNullOfOrNull { packageName ->
            context.packageManager.getLaunchIntentForPackage(packageName)
        } ?: return false

        return startActivity(intent = intent)
    }

    private fun startActivity(intent: Intent): Boolean = runCatching {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.isSuccess
}

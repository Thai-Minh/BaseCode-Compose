package com.looper.base.deeplink

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.looper.base.R
import com.looper.base.activities.MainActivity

object AppShortcuts {

    private data class Spec(
        val destination: AppDestination,
        val label: Int,
        val icon: Int,
    )

    private val specs = listOf(
        Spec(destination = AppDestination.SEARCH, label = R.string.shortcut_search, icon = R.drawable.ic_shortcut_search),
        Spec(destination = AppDestination.LIBRARY, label = R.string.shortcut_library, icon = R.drawable.ic_shortcut_library),
        Spec(destination = AppDestination.LISTEN_TOGETHER, label = R.string.shortcut_listen_together, icon = R.drawable.ic_shortcut_group),
    )

    fun publish(context: Context) {
        val shortcuts = specs.map { spec ->
            ShortcutInfoCompat.Builder(context, spec.destination.name.lowercase())
                .setShortLabel(context.getString(spec.label))
                .setIcon(IconCompat.createWithResource(context, spec.icon))
                .setIntent(
                    Intent(Intent.ACTION_VIEW, IncomingLinkParser.openUri(destination = spec.destination))
                        .setClass(context, MainActivity::class.java)
                )
                .build()
        }
        runCatching {
            ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts)
        }
    }
}

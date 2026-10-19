package com.looper.base.activities.lockscreen

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import com.looper.base.base.storage.audioLockScreenEnabled
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class LockScreenPlayerManager(context: Context, private val player: Player) {
    private val context = context.applicationContext
    private val keyguard = this.context.getSystemService(KeyguardManager::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var enabled = false
    private var shownForCurrentLock = false

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) showIfLocked()
        }
    }
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    shownForCurrentLock = false
                    showIfLocked()
                }

                Intent.ACTION_SCREEN_ON -> {
                    showIfLocked()
                }

                Intent.ACTION_USER_PRESENT -> {
                    shownForCurrentLock = false
                }
            }
        }
    }

    init {
        player.addListener(playerListener)
        ContextCompat.registerReceiver(
            this.context,
            receiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        scope.launch {
            audioLockScreenEnabled.collect {
                enabled = it
                if (enabled) showIfLocked()
            }
        }
    }

    private fun showIfLocked() {
        if (!enabled || shownForCurrentLock || !Settings.canDrawOverlays(context)) return
        if (!keyguard.isKeyguardLocked || player.currentMediaItem == null || !player.isPlaying) return

        // Overlay consent permits the background Activity launch; the secure keyguard stays locked.
        try {
            context.startActivity(
                Intent(context, LockScreenPlayerActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            )
        } catch (error: RuntimeException) {
            Log.w("LockScreenPlayer", "Unable to show lock-screen player", error)
        }
    }

    fun onPlayerShown() {
        shownForCurrentLock = true
    }

    fun release() {
        player.removeListener(playerListener)
        context.unregisterReceiver(receiver)
        scope.cancel()
    }
}

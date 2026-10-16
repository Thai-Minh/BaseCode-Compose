package com.looper.base.audio.lockscreen

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.looper.base.audio.playback.AudioPlaybackConnection
import com.looper.base.audio.service.AudioPlaybackService
import com.looper.base.base.storage.audioLockScreenEnabled
import com.looper.base.ui.theme.BaseCodeComposeTheme
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class LockScreenPlayerActivity : ComponentActivity() {
    private val playback by lazy { AudioPlaybackConnection(this) }
    private var connected = false
    private val unlockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_USER_PRESENT) finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
        }

        ContextCompat.registerReceiver(
            this, unlockReceiver, IntentFilter(Intent.ACTION_USER_PRESENT),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        playback.connect()
        lifecycleScope.launch {
            playback.state.collect { state ->
                if (state.isConnected) connected = true
                if (connected && (!state.isConnected || state.mediaId == null)) finish()
            }
        }

        lifecycleScope.launch {
            audioLockScreenEnabled.collect { enabled -> if (!enabled) finish() }
        }

        WindowCompat.setDecorFitsSystemWindows(window, false)

        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.navigationBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        setContent {
            BaseCodeComposeTheme {
                LockScreenPlayerScreen(
                    playback = playback,
                    onPrevious = playback::previous,
                    onToggle = playback::togglePlayPause,
                    onNext = playback::next,
                    onClose = ::finish
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val keyguard = getSystemService(KeyguardManager::class.java)
        val power = getSystemService(PowerManager::class.java)
        if (!Settings.canDrawOverlays(this) ||
            (!keyguard.isKeyguardLocked && power.isInteractive)
        ) {
            finish()
        } else if (keyguard.isKeyguardLocked) {
            AudioPlaybackService.lockScreenManager?.onPlayerShown()
        }
    }

    override fun onDestroy() {
        unregisterReceiver(unlockReceiver)
        playback.release()
        super.onDestroy()
    }
}

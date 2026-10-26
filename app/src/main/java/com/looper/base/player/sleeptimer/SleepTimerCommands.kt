package com.looper.base.player.sleeptimer

import android.os.Bundle
import androidx.media3.session.SessionCommand

object SleepTimerCommands {

    const val ACTION_START = "com.looper.base.sleeptimer.START"
    const val ACTION_CANCEL = "com.looper.base.sleeptimer.CANCEL"
    const val ACTION_EXTEND = "com.looper.base.sleeptimer.EXTEND"

    const val EXTRA_MODE = "sleep_timer_mode"

    const val EXTRA_DURATION_MS = "sleep_timer_duration_ms"

    const val EXTRA_END_AT_MS = "sleep_timer_end_at_ms"

    val all: List<SessionCommand> = listOf(
        SessionCommand(ACTION_START, Bundle.EMPTY),
        SessionCommand(ACTION_CANCEL, Bundle.EMPTY),
        SessionCommand(ACTION_EXTEND, Bundle.EMPTY),
    )

    fun SleepTimerState.writeTo(bundle: Bundle): Bundle = bundle.apply {
        val mode = mode
        if (mode == null) {
            remove(EXTRA_MODE)
            remove(EXTRA_END_AT_MS)
        } else {
            putString(EXTRA_MODE, mode.name)
            endAtMs?.let {
                putLong(EXTRA_END_AT_MS, it)
            } ?: remove(EXTRA_END_AT_MS)
        }
    }

    fun Bundle.readSleepTimerState(): SleepTimerState {
        val mode = getString(EXTRA_MODE)
            ?.let { name ->
                SleepTimerMode.entries.firstOrNull {
                    it.name == name
                }
            }
            ?: return SleepTimerState.Inactive
        val endAt = if (containsKey(EXTRA_END_AT_MS)) {
            getLong(EXTRA_END_AT_MS)
        } else {
            null
        }
        return SleepTimerState(mode = mode, endAtMs = endAt)
    }
}

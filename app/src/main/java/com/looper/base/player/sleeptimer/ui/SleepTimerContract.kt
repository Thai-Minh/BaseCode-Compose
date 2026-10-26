package com.looper.base.player.sleeptimer.ui

import androidx.annotation.StringRes
import com.looper.base.base.mvi.UiEffect
import com.looper.base.base.mvi.UiIntent
import com.looper.base.base.mvi.UiState
import com.looper.base.player.sleeptimer.SleepTimerMode
import com.looper.base.player.sleeptimer.SleepTimerState

data class SleepTimerUiState(
    val isConnected: Boolean = false,
    val timer: SleepTimerState = SleepTimerState.Inactive,
    val remainingMs: Long = 0L,
    val presetMinutes: List<Int> = listOf(5, 10, 15, 30, 45, 60),
) : UiState {
    val isActive: Boolean get() = timer.isActive
    val isCountingDown: Boolean get() = timer.mode == SleepTimerMode.DURATION
    val isEndOfTrack: Boolean get() = timer.mode == SleepTimerMode.END_OF_TRACK
}

sealed interface SleepTimerIntent : UiIntent {
    data class Start(val minutes: Int) : SleepTimerIntent
    data object StartEndOfTrack : SleepTimerIntent
    data class Extend(val minutes: Int = 5) : SleepTimerIntent
    data object Cancel : SleepTimerIntent
}

sealed interface SleepTimerEffect : UiEffect {
    data class ShowMessage(@StringRes val resId: Int) : SleepTimerEffect
}

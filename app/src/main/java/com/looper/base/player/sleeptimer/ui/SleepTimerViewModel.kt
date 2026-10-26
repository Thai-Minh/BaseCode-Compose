package com.looper.base.player.sleeptimer.ui

import androidx.lifecycle.viewModelScope
import com.looper.base.R
import com.looper.base.base.mvi.MviViewModel
import com.looper.base.player.PlayerConnection
import com.looper.base.player.sleeptimer.SleepTimerMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class SleepTimerViewModel(
    private val connection: PlayerConnection,
) : MviViewModel<SleepTimerUiState, SleepTimerIntent, SleepTimerEffect>(
    initialState = SleepTimerUiState(),
) {

    private companion object {
        const val TICK_MS = 1_000L
        const val MINUTE_MS = 60_000L
    }

    init {
        viewModelScope.launch {
            connection.state.collect { player ->
                setState {
                    copy(isConnected = player.isConnected)
                }
            }
        }
        viewModelScope.launch {
            connection.sleepTimer.collect { timer ->
                setState {
                    copy(timer = timer, remainingMs = timer.remainingMs())
                }
            }
        }
        viewModelScope.launch {
            connection.sleepTimer
                .flatMapLatest { timer ->
                    if (timer.mode == SleepTimerMode.DURATION) {
                        flow {
                            while (currentCoroutineContext().isActive) {
                                emit(value = timer.remainingMs())
                                delay(timeMillis = TICK_MS)
                            }
                        }
                    } else {
                        flowOf(value = 0L)
                    }
                }
                .collect { remaining ->
                    setState {
                        copy(remainingMs = remaining)
                    }
                }
        }
    }

    override fun onIntent(intent: SleepTimerIntent) {
        if (!currentState.isConnected) {
            sendEffect(SleepTimerEffect.ShowMessage(resId = R.string.sleep_timer_not_connected))
            return
        }
        when (intent) {
            is SleepTimerIntent.Start -> {
                connection.startSleepTimer(durationMs = intent.minutes * MINUTE_MS)
                sendEffect(SleepTimerEffect.ShowMessage(resId = R.string.sleep_timer_started))
            }

            SleepTimerIntent.StartEndOfTrack -> {
                connection.startSleepTimerAtEndOfTrack()
                sendEffect(SleepTimerEffect.ShowMessage(resId = R.string.sleep_timer_started))
            }

            is SleepTimerIntent.Extend ->
                connection.extendSleepTimer(byMs = intent.minutes * MINUTE_MS)

            SleepTimerIntent.Cancel -> {
                connection.cancelSleepTimer()
                sendEffect(SleepTimerEffect.ShowMessage(resId = R.string.sleep_timer_cancelled))
            }
        }
    }
}

package com.looper.base.base.mvi

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update

interface UiState

interface UiIntent

interface UiEffect

abstract class MviViewModel<S : UiState, I : UiIntent, E : UiEffect>(
    initialState: S,
) : ViewModel() {

    val state: StateFlow<S>
        field = MutableStateFlow(value = initialState)

    private val effectChannel = Channel<E>(Channel.BUFFERED)

    val effect: Flow<E> = effectChannel.receiveAsFlow()

    protected val currentState: S
        get() = state.value

    abstract fun onIntent(intent: I)

    protected fun setState(reducer: S.() -> S) {
        state.update { it.reducer() }
    }

    protected fun sendEffect(effect: E) {
        effectChannel.trySend(effect)
    }
}

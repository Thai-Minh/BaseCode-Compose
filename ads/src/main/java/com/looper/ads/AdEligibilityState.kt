package com.looper.ads

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

class AdEligibilityState {
    private val _isEligible = MutableStateFlow(false)
    val isEligible: StateFlow<Boolean> = _isEligible.asStateFlow()

    private var observation: Job? = null

    @Synchronized
    fun bind(
        scope: CoroutineScope,
        remoteAllowsAds: Flow<Boolean?>,
        isPremium: Flow<Boolean?>,
    ) {
        if (observation != null) {
            return
        }

        observation = scope.launch {
            combine(remoteAllowsAds, isPremium) { remoteAllowed, premium ->
                remoteAllowed == true && premium == false
            }.distinctUntilChanged().collect { eligible ->
                _isEligible.value = eligible
            }
        }
    }
}

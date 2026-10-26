package com.looper.base.player.sleeptimer

enum class SleepTimerMode {
    DURATION,

    END_OF_TRACK,
}

data class SleepTimerState(
    val mode: SleepTimerMode? = null,
    val endAtMs: Long? = null,
) {
    val isActive: Boolean
        get() = mode != null

    fun remainingMs(nowMs: Long = System.currentTimeMillis()): Long = endAtMs?.let {
        (it - nowMs).coerceAtLeast(minimumValue = 0L)
    } ?: 0L

    companion object {
        val Inactive = SleepTimerState()
    }
}

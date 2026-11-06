package com.looper.base.screen.player.data

import com.looper.base.base.mvi.UiEffect
import com.looper.base.base.mvi.UiIntent
import com.looper.base.base.mvi.UiState
import com.looper.base.player.PlayerQueue
import com.looper.base.player.PlayerState

data class PlayerUiState(
    val player: PlayerState = PlayerState(),
    val positionMs: Long = 0L,
    val isFavorite: Boolean = false,
    val queue: PlayerQueue = PlayerQueue(),
) : UiState {
    val progress: Float
        get() = if (player.durationMs > 0L) {
            (positionMs.toFloat() / player.durationMs).coerceIn(minimumValue = 0f, maximumValue = 1f)
        } else {
            0f
        }

    val remainingMs: Long
        get() = (player.durationMs - positionMs).coerceAtLeast(minimumValue = 0L)

    val canSeek: Boolean
        get() = player.isConnected && player.durationMs > 0L
}

sealed interface PlayerIntent : UiIntent {
    data object PlayPauseClick : PlayerIntent
    data object NextClick : PlayerIntent
    data object PreviousClick : PlayerIntent
    data class SeekTo(val positionMs: Long) : PlayerIntent
    data object ShuffleClick : PlayerIntent
    data object RepeatClick : PlayerIntent
    data object FavoriteClick : PlayerIntent
    data class VideoToggle(val enabled: Boolean) : PlayerIntent
    data class QueueItemClick(val index: Int) : PlayerIntent
    data class QueueItemMove(val fromIndex: Int, val toIndex: Int) : PlayerIntent
    data class QueueItemPlayNext(val index: Int) : PlayerIntent
    data class QueueItemRemove(val index: Int) : PlayerIntent
    data object QueueClear : PlayerIntent
}

sealed interface PlayerEffect : UiEffect {
    data object NotConnected : PlayerEffect
}

package com.looper.base.screen.player

import androidx.lifecycle.viewModelScope
import com.looper.base.base.mvi.MviViewModel
import com.looper.base.library.data.LibraryRepository
import com.looper.base.player.PlayerConnection
import com.looper.base.screen.player.data.PlayerEffect
import com.looper.base.screen.player.data.PlayerIntent
import com.looper.base.screen.player.data.PlayerUiState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModel(
    private val connection: PlayerConnection,
    private val libraryRepository: LibraryRepository,
) : MviViewModel<PlayerUiState, PlayerIntent, PlayerEffect>(
    initialState = PlayerUiState(),
) {
    init {
        viewModelScope.launch {
            connection.state.collect { player ->
                setState {
                    copy(player = player)
                }
            }
        }
        viewModelScope.launch {
            connection.queue.collect { queue ->
                setState {
                    copy(queue = queue)
                }
            }
        }
        viewModelScope.launch {
            connection.position.collect { position ->
                setState {
                    copy(positionMs = position)
                }
            }
        }
        viewModelScope.launch {
            connection.state
                .map {
                    it.mediaId
                }
                .distinctUntilChanged()
                .flatMapLatest { mediaId ->
                    if (mediaId == null) {
                        flowOf(value = null)
                    } else {
                        libraryRepository.observeMediaItem(id = mediaId)
                    }
                }
                .collect { item ->
                    setState {
                        copy(isFavorite = item?.isFavorite == true)
                    }
                }
        }
    }

    override fun onIntent(intent: PlayerIntent) {
        if (!currentState.player.isConnected) {
            sendEffect(effect = PlayerEffect.NotConnected)
            return
        }
        when (intent) {
            PlayerIntent.PlayPauseClick -> connection.togglePlayPause()

            PlayerIntent.NextClick -> connection.skipToNext()

            PlayerIntent.PreviousClick -> connection.skipToPrevious()

            is PlayerIntent.SeekTo -> {
                connection.seekTo(positionMs = intent.positionMs)
                setState {
                    copy(positionMs = intent.positionMs)
                }
            }

            PlayerIntent.ShuffleClick -> connection.toggleShuffle()

            PlayerIntent.RepeatClick -> connection.cycleRepeat()

            PlayerIntent.FavoriteClick -> connection.toggleFavorite()

            is PlayerIntent.VideoToggle -> connection.setVideoEnabled(enabled = intent.enabled)

            is PlayerIntent.QueueItemClick -> connection.skipToQueueItem(index = intent.index)

            is PlayerIntent.QueueItemMove -> if (currentState.queue.canReorder) {
                connection.moveQueueItem(fromIndex = intent.fromIndex, toIndex = intent.toIndex)
            }

            is PlayerIntent.QueueItemPlayNext -> if (currentState.queue.canReorder) {
                connection.moveQueueItemToNext(index = intent.index)
            }

            is PlayerIntent.QueueItemRemove -> connection.removeQueueItems(indices = listOf(intent.index))

            PlayerIntent.QueueClear -> connection.clearQueue()
        }
    }
}

package com.looper.base.service

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.looper.base.R
import com.looper.base.repository.audio_local.data.DeviceAudio
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AudioPlaybackState(
    val isConnected: Boolean = false,
    val mediaId: String? = null,
    val title: String = "",
    val artist: String = "",
    val audioUri: Uri? = null,
    val artworkUri: Uri? = null,
    val isPlaying: Boolean = false,
    val canSkip: Boolean = false,
    val error: Int? = null,
)

@OptIn(UnstableApi::class)
class AudioPlaybackConnection(context: Context) {
    private val context = context.applicationContext
    private val mutableState = MutableStateFlow(AudioPlaybackState())
    val state = mutableState.asStateFlow()
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = updateState()
    }

    fun connect() {
        if (controllerFuture != null) return
        val future = MediaController.Builder(
            context, SessionToken(context, ComponentName(context, AudioPlaybackService::class.java))
        ).setListener(object : MediaController.Listener {
            override fun onDisconnected(controller: MediaController) {
                this@AudioPlaybackConnection.controller = null
                controllerFuture?.let(MediaController::releaseFuture)
                controllerFuture = null
                mutableState.value = AudioPlaybackState(error = R.string.audio_connection_error)
            }
        }).buildAsync()
        controllerFuture = future
        future.addListener({
            if (controllerFuture !== future) return@addListener
            try {
                controller = future.get().also { it.addListener(listener) }
                updateState()
            } catch (_: Exception) {
                MediaController.releaseFuture(future)
                controllerFuture = null
                mutableState.value = AudioPlaybackState(error = R.string.audio_connection_error)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun play(audio: List<DeviceAudio>, selected: DeviceAudio) {
        val player = controller ?: return
        val index = audio.indexOfFirst { it.id == selected.id }
        if (index < 0) return
        val playlist = audio.map { track ->
            MediaItem.Builder()
                .setMediaId(track.id.toString())
                .setUri(track.uri)
                // LocalConfiguration is not shared with MediaController; keep the source URI accessible.
                .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(track.uri).build())
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(track.name)
                        .setArtist(track.artist)
                        .setArtworkUri(track.artworkUri)
                        .build()
                ).build()
        }
        player.setMediaItems(playlist, index, 0L)
        player.prepare()
        player.play()
    }

    fun togglePlayPause() {
        val player = controller ?: return
        Util.handlePlayPauseButtonAction(player)
    }

    fun previous() {
        controller?.seekToPreviousMediaItem()
    }

    fun next() {
        controller?.seekToNextMediaItem()
    }

    private fun updateState() {
        val player = controller ?: return
        val item = player.currentMediaItem
        mutableState.value = mutableState.value.copy(
            isConnected = true,
            mediaId = item?.mediaId,
            title = player.mediaMetadata.title?.toString().orEmpty(),
            artist = player.mediaMetadata.artist?.toString().orEmpty(),
            audioUri = item?.requestMetadata?.mediaUri ?: item?.localConfiguration?.uri,
            artworkUri = player.mediaMetadata.artworkUri,
            isPlaying = !Util.shouldShowPlayButton(player),
            canSkip = player.mediaItemCount > 0,
            error = if (player.playerError != null) R.string.audio_playback_error else null,
        )
    }

    fun release() {
        controller?.removeListener(listener)
        controller = null
        val future = controllerFuture
        controllerFuture = null
        future?.let(MediaController::releaseFuture)
    }
}

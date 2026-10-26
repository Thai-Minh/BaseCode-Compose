package com.looper.base.player

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.VideoSize

data class PlayerState(
    val isConnected: Boolean = false,
    val isPlaying: Boolean = false,
    val playWhenReady: Boolean = false,
    @Player.State val playbackState: Int = Player.STATE_IDLE,
    val mediaId: String? = null,
    val title: String? = null,
    val artist: String? = null,
    val artworkUri: Uri? = null,
    val durationMs: Long = 0L,
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false,
    @Player.RepeatMode val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val shuffleEnabled: Boolean = false,
    val videoSize: VideoSize = VideoSize.UNKNOWN,
) {
    val hasMedia: Boolean
        get() = mediaId != null

    val hasVideo: Boolean
        get() = videoSize.width > 0 && videoSize.height > 0

    val isBuffering: Boolean
        get() = playbackState == Player.STATE_BUFFERING

    companion object {
        fun from(player: Player, isConnected: Boolean = true): PlayerState {
            val metadata = player.mediaMetadata
            val duration = player.duration
            return PlayerState(
                isConnected = isConnected,
                isPlaying = player.isPlaying,
                playWhenReady = player.playWhenReady,
                playbackState = player.playbackState,
                mediaId = player.currentMediaItem?.mediaId,
                title = metadata.title?.toString(),
                artist = metadata.artist?.toString(),
                artworkUri = metadata.artworkUri,
                durationMs = if (duration == C.TIME_UNSET) {
                    0L
                } else {
                    duration
                },
                hasNext = player.hasNextMediaItem(),
                hasPrevious = player.hasPreviousMediaItem(),
                repeatMode = player.repeatMode,
                shuffleEnabled = player.shuffleModeEnabled,
                videoSize = player.videoSize,
            )
        }
    }
}

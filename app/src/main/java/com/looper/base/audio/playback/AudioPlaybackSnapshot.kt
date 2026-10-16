package com.looper.base.audio.playback

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player

data class AudioPlaybackSnapshot(
    val mediaId: String,
    val uri: Uri,
    val title: String,
    val artist: String,
    val artworkUri: Uri?,
    val positionMs: Long,
) {
    fun toMediaItem(): MediaItem = MediaItem.Builder()
        .setMediaId(mediaId)
        .setUri(uri)
        .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(uri).build())
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setArtworkUri(artworkUri)
                .build()
        )
        .build()

    companion object {
        fun fromPlayer(player: Player): AudioPlaybackSnapshot? {
            val item = player.currentMediaItem ?: return null
            val uri = item.localConfiguration?.uri ?: return null
            return AudioPlaybackSnapshot(
                mediaId = item.mediaId,
                uri = uri,
                title = player.mediaMetadata.title?.toString().orEmpty(),
                artist = player.mediaMetadata.artist?.toString().orEmpty(),
                artworkUri = player.mediaMetadata.artworkUri,
                positionMs = player.currentPosition.coerceAtLeast(0L),
            )
        }
    }
}

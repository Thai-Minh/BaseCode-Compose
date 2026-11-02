package com.looper.base.player.source

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import com.looper.base.database.data.MediaType

enum class StreamTrack { MAIN, VIDEO, AUDIO, SPLIT }

object MediaItemKeys {
    const val MEDIA_TYPE = "media_type"
    const val SOURCE_URL = "source_url"
    const val IS_LIVE = "is_live"
    const val STREAM_SCHEME = "looper-stream"

    fun streamUri(mediaItemId: String, track: StreamTrack = StreamTrack.MAIN): Uri = Uri.Builder()
        .scheme(STREAM_SCHEME)
        .appendPath(mediaItemId)
        .apply {
            if (track != StreamTrack.MAIN) {
                appendPath(track.name.lowercase())
            }
        }.build()

    fun streamMediaItemId(uri: Uri?): String? =
        uri?.takeIf {
            it.scheme == STREAM_SCHEME
        }?.pathSegments?.firstOrNull()

    fun streamTrack(uri: Uri?): StreamTrack =
        uri?.takeIf {
            it.scheme == STREAM_SCHEME
        }?.pathSegments?.getOrNull(index = 1)?.let { name ->
            StreamTrack.entries.firstOrNull {
                it.name.equals(other = name, ignoreCase = true)
            }
        } ?: StreamTrack.MAIN

    fun cacheKey(mediaItemId: String, track: StreamTrack): String =
        if (track == StreamTrack.MAIN) {
            mediaItemId
        } else {
            "$mediaItemId#${track.name.lowercase()}"
        }

    fun isAdaptive(mimeType: String?): Boolean =
        mimeType == MimeTypes.APPLICATION_M3U8 || mimeType == MimeTypes.APPLICATION_MPD

    fun MediaItem.isLive(): Boolean = mediaMetadata.extras?.getBoolean(IS_LIVE) == true

    fun MediaItem.mediaType(): MediaType? =
        mediaMetadata.extras?.getString(MEDIA_TYPE)
            ?.let { name ->
                MediaType.entries.firstOrNull {
                    it.name == name
                }
            }
}

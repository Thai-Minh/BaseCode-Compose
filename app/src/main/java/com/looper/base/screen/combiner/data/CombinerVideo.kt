package com.looper.base.screen.combiner.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri

/** A video picked from the device; [width] x [height] is the upright (displayed) size. */
data class CombinerVideo(
    val uri: Uri,
    val width: Int,
    val height: Int,
    val durationUs: Long,
) {
    val aspectRatio: Float get() = width.toFloat() / height

    companion object {
        /** Reads [uri]'s metadata (blocking), or null if it isn't a playable video. */
        fun read(context: Context, uri: Uri): CombinerVideo? = MediaMetadataRetriever().run {
            try {
                setDataSource(context, uri)
                val width = extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                    ?.toIntOrNull() ?: return null
                val height = extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                    ?.toIntOrNull() ?: return null
                val rotation = extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                    ?.toIntOrNull() ?: 0
                val durationMs = extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull() ?: return null
                if (width <= 0 || height <= 0 || durationMs <= 0) return null
                val sideways = rotation % 180 != 0
                CombinerVideo(
                    uri = uri,
                    width = if (sideways) height else width,
                    height = if (sideways) width else height,
                    durationUs = durationMs * 1000,
                )
            } catch (_: RuntimeException) {
                null
            } finally {
                release()
            }
        }
    }
}

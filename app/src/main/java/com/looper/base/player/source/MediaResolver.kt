package com.looper.base.player.source

import android.content.Context
import android.os.Bundle
import androidx.core.net.toUri
import androidx.core.os.bundleOf
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import com.looper.base.database.data.MediaType
import io.ytdlp.android.YtDlpAndroid
import io.ytdlp.core.model.Format
import io.ytdlp.core.model.MediaInfo
import io.ytdlp.core.model.PlayableMedia
import io.ytdlp.core.model.PlayableSelector
import io.ytdlp.core.model.StreamType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MediaResolver(
    private val context: Context,
) {

    class ResolveException(message: String, cause: Throwable? = null) : Exception(message, cause)

    private data class Stream(
        val url: String,
        val mimeType: String?,
        val headers: Map<String, String>,
    )

    suspend fun resolve(sourceUrl: String): MediaItem = withContext(Dispatchers.IO) {
        val ytdlp = YtDlpAndroid.init(context)
        val info = ytdlp.getInfo(sourceUrl)
        if (info.isPlaylist) throw ResolveException("Link là playlist, hãy dán link một bài.")
        if (info.drmProtected) throw ResolveException("Nội dung có DRM, không phát được.")

        val playable = info.formats.filter { it.streamType != StreamType.OTHER && !it.isMerged }
        val isVideoSource = playable.any { it.hasVideo }
        val stream = (if (isVideoSource) selectVideo(info, playable) else selectAudio(info))
            ?: throw ResolveException("Không tìm thấy luồng phát được.")

        buildMediaItem(
            info = info,
            stream = stream,
            mediaType = if (isVideoSource) MediaType.VIDEO else MediaType.AUDIO,
            sourceUrl = sourceUrl,
        )
    }

    private fun selectVideo(info: MediaInfo, formats: List<Format>): Stream? {
        val hls = formats.firstOrNull { it.streamType == StreamType.HLS && it.manifestUrl != null }
        if (hls != null) {
            return Stream(
                url = hls.manifestUrl!!,
                mimeType = MimeTypes.APPLICATION_M3U8,
                headers = hls.httpHeaders.ifEmpty { info.httpHeaders },
            )
        }
        val progressive = formats
            .filter { it.streamType == StreamType.PROGRESSIVE && it.hasAudio && it.hasVideo }
            .maxByOrNull { it.height ?: 0 }
            ?.let { PlayableMedia.from(info, it) }
            ?: PlayableSelector.select(info, preferAudioOnly = false)
        return progressive?.toStream()
    }

    private fun selectAudio(info: MediaInfo): Stream? =
        PlayableSelector.select(info, preferAudioOnly = true)?.toStream()

    private fun PlayableMedia.toStream() = Stream(
        url = url,
        mimeType = when (streamType) {
            StreamType.HLS -> MimeTypes.APPLICATION_M3U8
            StreamType.DASH -> MimeTypes.APPLICATION_MPD
            else -> null
        },
        headers = headers,
    )

    private fun buildMediaItem(
        info: MediaInfo,
        stream: Stream,
        mediaType: MediaType,
        sourceUrl: String,
    ): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(info.title)
            .setArtist(info.artist ?: info.uploader ?: info.channel)
            .setArtworkUri(info.bestThumbnailUrl?.toUri())
            .setExtras(
                bundleOf(
                    MediaItemKeys.MEDIA_TYPE to mediaType.name,
                    MediaItemKeys.SOURCE_URL to sourceUrl,
                )
            )
            .build()
        val request = MediaItem.RequestMetadata.Builder()
            .setMediaUri(sourceUrl.toUri())
            .setExtras(
                Bundle().apply {
                    putBundle(MediaItemKeys.HTTP_HEADERS, MediaItemKeys.headersBundle(stream.headers))
                }
            )
            .build()
        return MediaItem.Builder()
            .setMediaId(info.id)
            .setUri(stream.url)
            .setMimeType(stream.mimeType)
            .setMediaMetadata(metadata)
            .setRequestMetadata(request)
            .build()
    }
}

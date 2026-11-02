package com.looper.base.player.source

import android.content.Context
import androidx.media3.common.MimeTypes
import com.looper.base.database.data.MediaType
import io.ytdlp.android.YtDlpAndroid
import io.ytdlp.core.model.Format
import io.ytdlp.core.model.MediaInfo
import io.ytdlp.core.model.PlayableMedia
import io.ytdlp.core.model.PlayableSelector
import io.ytdlp.core.model.StreamType
import io.ytdlp.core.model.Subtitle
import io.ytdlp.core.model.UrlExpiry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

data class ResolvedStream(
    val url: String,
    val mimeType: String?,
    val headers: Map<String, String>,
    val hasVideo: Boolean,
    val formatId: String?,
    val expiresAt: Long?,
    val audioUrl: String? = null,
    val subtitles: List<SubtitleTrack> = emptyList(),
)

@Serializable
data class SubtitleTrack(
    val url: String,
    val language: String,
    val label: String,
    val mimeType: String,
    val isAutomatic: Boolean,
)

data class ResolveOptions(
    val maxVideoHeight: Int,
    val preferredAudioLanguage: String? = null,
    val subtitleLanguage: String? = null,
)

data class ResolvedMedia(
    val sourceId: String,
    val extractorKey: String?,
    val title: String,
    val artist: String?,
    val album: String?,
    val artworkUrl: String?,
    val durationMs: Long,
    val mediaType: MediaType,
    val isLive: Boolean,
    val stream: ResolvedStream,
)

class MediaResolver(
    private val context: Context,
) {

    class ResolveException(message: String, cause: Throwable? = null) : Exception(message, cause)

    private companion object {
        val VIDEO_CODEC_RANK = listOf("avc1", "vp09", "vp9", "hev1", "hvc1", "av01")
        val AUDIO_CODEC_RANK = listOf("mp4a", "opus")
        val SUBTITLE_MIME_TYPES = linkedMapOf(
            "vtt" to MimeTypes.TEXT_VTT,
            "ttml" to MimeTypes.APPLICATION_TTML,
            "srt" to MimeTypes.APPLICATION_SUBRIP,
        )
        const val MAX_SUBTITLE_TRACKS = 20
    }

    suspend fun resolve(sourceUrl: String, options: ResolveOptions): ResolvedMedia =
        withContext(Dispatchers.IO) {
            val ytdlp = YtDlpAndroid.init(context)
            val info = ytdlp.getInfo(sourceUrl)
            if (info.isPlaylist) {
                throw ResolveException("The link is for a playlist; please paste the link to a song.")
            }
            if (info.drmProtected) {
                throw ResolveException("The Content Contains DRM And Cannot Be Played")
            }

            val playable = info.formats.filter {
                it.streamType != StreamType.OTHER && !it.isMerged
            }
            val isVideoSource = playable.any {
                it.hasVideo
            }
            val stream = if (isVideoSource) {
                selectSplit(
                    info = info,
                    formats = playable,
                    maxHeight = options.maxVideoHeight,
                    audioLanguage = options.preferredAudioLanguage,
                ) ?: selectVideo(info = info, formats = playable)
            } else {
                selectAudio(
                    info = info,
                    formats = playable,
                    audioLanguage = options.preferredAudioLanguage
                )
            } ?: throw ResolveException("No Stream Found")
            val subtitles = if (isVideoSource) {
                selectSubtitles(info = info, preferredLanguage = options.subtitleLanguage)
            } else {
                emptyList()
            }

            ResolvedMedia(
                sourceId = info.id,
                extractorKey = info.extractorKey,
                title = info.track ?: info.title ?: sourceUrl,
                artist = info.artist ?: info.uploader ?: info.channel,
                album = info.album,
                artworkUrl = info.bestThumbnailUrl,
                durationMs = info.durationMs ?: 0L,
                mediaType = if (isVideoSource) {
                    MediaType.VIDEO
                } else {
                    MediaType.AUDIO
                },
                isLive = info.isLive == true || info.liveStatus == "is_live",
                stream = stream.copy(subtitles = subtitles),
            )
        }

    private fun selectSplit(
        info: MediaInfo,
        formats: List<Format>,
        maxHeight: Int,
        audioLanguage: String?,
    ): ResolvedStream? {
        val videoCandidates = formats.filter {
            it.streamType == StreamType.PROGRESSIVE && it.isVideoOnly && it.url != null && (it.height
                ?: 0) <= maxHeight
        }
        val decodable = videoCandidates
            .filterNot {
                it.vcodec.orEmpty().startsWith(prefix = "av01")
            }
            .ifEmpty {
                videoCandidates
            }
        val video = decodable
            .sortedWith(
                comparator = compareByDescending<Format> {
                    it.height ?: 0
                }.thenBy {
                    codecRank(codec = it.vcodec, ranking = VIDEO_CODEC_RANK)
                }.thenByDescending {
                    it.tbr ?: 0.0
                }
            )
            .firstOrNull()
            ?: return null
        val audio = formats
            .filter {
                it.streamType == StreamType.PROGRESSIVE && it.isAudioOnly && it.url != null
            }
            .sortedWith(
                comparator = compareBy<Format> {
                    languageRank(format = it, preferred = audioLanguage)
                }.thenBy {
                    codecRank(codec = it.acodec, ranking = AUDIO_CODEC_RANK)
                }.thenByDescending {
                    it.abr ?: it.tbr ?: 0.0
                }
            ).firstOrNull() ?: return null
        val videoUrl = video.url!!
        val audioUrl = audio.url!!
        return ResolvedStream(
            url = videoUrl,
            mimeType = null,
            headers = video.httpHeaders.ifEmpty { info.httpHeaders },
            hasVideo = true,
            formatId = "${video.formatId}+${audio.formatId}",
            expiresAt = listOfNotNull(
                UrlExpiry.parse(url = videoUrl),
                UrlExpiry.parse(url = audioUrl)
            ).minOrNull(),
            audioUrl = audioUrl,
        )
    }

    private fun languageRank(format: Format, preferred: String?): Int {
        val language = format.language?.lowercase()
        val note = format.formatNote.orEmpty().lowercase()
        return when {
            preferred != null && language != null && (language == preferred.lowercase() || language.startsWith(
                prefix = preferred.lowercase() + "-"
            )) -> 0

            language == null || "original" in note || "default" in note -> 1
            else -> 2
        }
    }

    private fun selectSubtitles(info: MediaInfo, preferredLanguage: String?): List<SubtitleTrack> {
        val manual = info.subtitles.mapNotNull { (language, tracks) ->
            subtitleTrack(language = language, tracks = tracks, isAutomatic = false)
        }
        val automatic = preferredLanguage
            ?.takeIf { language ->
                manual.none {
                    it.language.equals(other = language, ignoreCase = true)
                }
            }
            ?.let { language ->
                info.automaticCaptions.entries
                    .firstOrNull {
                        it.key.equals(other = language, ignoreCase = true)
                    }
                    ?.let {
                        subtitleTrack(language = it.key, tracks = it.value, isAutomatic = true)
                    }
            }
        return (manual.take(n = MAX_SUBTITLE_TRACKS) + listOfNotNull(element = automatic))
    }

    private fun subtitleTrack(
        language: String,
        tracks: List<Subtitle>,
        isAutomatic: Boolean
    ): SubtitleTrack? {
        val (subtitle, mimeType) = SUBTITLE_MIME_TYPES.firstNotNullOfOrNull { (extension, mimeType) ->
            tracks.firstOrNull {
                it.ext == extension && it.url != null
            }?.let {
                it to mimeType
            }
        } ?: return null
        return SubtitleTrack(
            url = subtitle.url!!,
            language = language,
            label = subtitle.name ?: language,
            mimeType = mimeType,
            isAutomatic = isAutomatic,
        )
    }

    private fun codecRank(codec: String?, ranking: List<String>): Int {
        val value = codec.orEmpty()
        val index = ranking.indexOfFirst {
            value.startsWith(prefix = it)
        }
        return if (index >= 0) {
            index
        } else {
            ranking.size
        }
    }

    private fun selectVideo(info: MediaInfo, formats: List<Format>): ResolvedStream? {
        val hls = formats.firstOrNull {
            it.streamType == StreamType.HLS && it.manifestUrl != null
        }
        if (hls != null) {
            val url = hls.manifestUrl!!
            return ResolvedStream(
                url = url,
                mimeType = MimeTypes.APPLICATION_M3U8,
                headers = hls.httpHeaders.ifEmpty {
                    info.httpHeaders
                },
                hasVideo = true,
                formatId = hls.formatId,
                expiresAt = UrlExpiry.parse(url = url),
            )
        }
        val progressive = formats
            .filter {
                it.streamType == StreamType.PROGRESSIVE && it.hasAudio && it.hasVideo
            }
            .maxByOrNull {
                it.height ?: 0
            }
            ?.let {
                PlayableMedia.from(info = info, format = it)
            } ?: PlayableSelector.select(info = info, preferAudioOnly = false)
        return progressive?.toStream()
    }

    private fun selectAudio(
        info: MediaInfo,
        formats: List<Format>,
        audioLanguage: String?
    ): ResolvedStream? {
        if (audioLanguage != null) {
            formats
                .filter {
                    it.isAudioOnly && languageRank(format = it, preferred = audioLanguage) == 0
                }
                .sortedWith(
                    comparator = compareBy<Format> {
                        codecRank(codec = it.acodec, ranking = AUDIO_CODEC_RANK)
                    }.thenByDescending {
                        it.abr ?: it.tbr ?: 0.0
                    }
                )
                .firstNotNullOfOrNull {
                    PlayableMedia.from(info = info, format = it)
                }
                ?.let {
                    return it.toStream()
                }
        }
        return PlayableSelector.select(info = info, preferAudioOnly = true)?.toStream()
    }

    private fun PlayableMedia.toStream() = ResolvedStream(
        url = url,
        mimeType = when (streamType) {
            StreamType.HLS -> MimeTypes.APPLICATION_M3U8
            StreamType.DASH -> MimeTypes.APPLICATION_MPD
            else -> null
        },
        headers = headers,
        hasVideo = format.hasVideo,
        formatId = format.formatId,
        expiresAt = expiresAtMillis,
    )
}

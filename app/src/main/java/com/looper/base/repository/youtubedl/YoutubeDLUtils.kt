package com.looper.base.repository.youtubedl

import android.content.Context
import android.util.Log
import com.looper.base.repository.youtubedl.data.YoutubeDLFormatOption
import com.looper.base.repository.youtubedl.data.YoutubeDLResolvedAudio
import com.looper.base.repository.youtubedl.data.YoutubeDLResolvedVideo
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.youtubedl_android.mapper.VideoFormat
import com.yausername.youtubedl_android.mapper.VideoInfo
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.UUID

const val BASE_YOUTUBE_DL_PROCESS = "ytdl-"
const val YOUTUBE_DL_FOLDER = "youtube-dl/"

private val VIDEO_OUTPUT_EXTENSIONS = setOf("mp4", "webm", "mkv", "mov")
private val AUDIO_ONLY_EXTENSIONS = setOf("m4a", "mp3", "aac", "opus", "ogg", "oga", "wav", "flac")
private const val DEFAULT_VIDEO_OUTPUT_EXTENSION = "mp4"
private val YOUTUBE_VIDEO_ID_PATTERN = Regex("^[A-Za-z0-9_-]{11}$")
private val YOUTUBE_WATCH_HOSTS = setOf(
    "youtube.com",
    "www.youtube.com",
    "m.youtube.com",
    "music.youtube.com"
)
private val COOKIE_NAME_PATTERN = Regex("^[!#$%&'*+.^_`|~0-9A-Za-z-]+$")
private val YOUTUBE_DL_DEFAULT_IDENTITY_DOMAINS = setOf(
    "soundcloud.com",
    "tiktok.com",
    "facebook.com",
    "fb.watch",
    "vimeo.com",
    "pin.it",
    "instagram.com",
    "twitter.com",
    "x.com",
    "t.co",
) + setOf(
    "com", "fr", "de", "ch", "jp", "cl", "ca", "it", "co.uk", "nz", "ru", "com.au",
    "at", "pt", "co.kr", "es", "com.mx", "dk", "ph", "th", "com.uy", "co", "nl", "info",
    "kr", "ie", "vn", "com.vn", "ec", "mx", "in", "pe", "co.at", "hu", "co.in", "co.nz",
    "id", "com.ec", "com.py", "tw", "be", "uk", "com.bo", "com.pe",
).map { "pinterest.$it" }
private val youtubeDLInitMutex = Mutex()

@Volatile
private var youtubeDLInitialized = false

internal fun VideoInfo.toResolvedVideo(
    sourceUrl: String,
    cookieFilePath: String? = null,
    userAgent: String? = null,
    referer: String? = null,
): YoutubeDLResolvedVideo {
    val extractorName = (extractorKey ?: extractor)?.takeIf(String::isNotBlank) ?: "YoutubeDL"
    val isYouTube = sourceUrl.isYouTubeSourceUrl() ||
            extractorName.startsWith(prefix = "youtube", ignoreCase = true)
    val primaryFormats = formats.orEmpty().map(VideoFormat::toExtractedFormat)
    val requested = requestedFormats.orEmpty().map(VideoFormat::toExtractedFormat)
    val topLevel = toTopLevelExtractedFormat()

    Log.i(
        YOUTUBE_DL_LOG_TAG,
        "YTDL_RESOLVE info id=${id ?: "?"} extractor=$extractorName isYouTube=$isYouTube " +
                "formats=${primaryFormats.size} requestedFormats=${requested.size} " +
                "topLevelFormatId=${formatId ?: "?"} topLevelUrl=${url.toLogUrl()}",
    )

    var selectedStage = "formats"
    var candidates = mapAndLogYoutubeDLFormats(
        stage = selectedStage,
        formats = primaryFormats,
        isYouTube = isYouTube,
    )

    if (candidates.isEmpty() && requested.isNotEmpty()) {
        selectedStage = "requested_formats"
        Log.w(YOUTUBE_DL_LOG_TAG, "YTDL_RESOLVE no usable formats; trying requested_formats")
        candidates = mapAndLogYoutubeDLFormats(
            stage = selectedStage,
            formats = requested,
            isYouTube = isYouTube,
        )
    }

    if (candidates.isEmpty() && topLevel != null) {
        selectedStage = "top_level"
        Log.w(YOUTUBE_DL_LOG_TAG, "YTDL_RESOLVE no usable format list; trying top-level media URL")
        candidates = mapAndLogYoutubeDLFormats(
            stage = selectedStage,
            formats = listOf(topLevel),
            isYouTube = isYouTube,
        )
    }

    if (candidates.isEmpty()) {
        val message = "No valid downloadable video formats were extracted " +
                "(extractor=$extractorName, formats=${primaryFormats.size}, " +
                "requestedFormats=${requested.size}, topLevelUrl=${url.toLogUrl()})"
        Log.e(YOUTUBE_DL_LOG_TAG, "YTDL_RESOLVE $message")
        throw IllegalStateException(message)
    }

    Log.i(
        YOUTUBE_DL_LOG_TAG,
        "YTDL_RESOLVE mapped stage=$selectedStage options=${candidates.size} " +
                "selectors=${candidates.joinToString { "${it.id}=${it.selector}" }}",
    )

    return YoutubeDLResolvedVideo(
        id = id?.takeIf(String::isNotBlank) ?: sourceUrl,
        sourceUrl = sourceUrl,
        title = (title ?: fulltitle)?.takeIf(String::isNotBlank) ?: "Video",
        ownerName = uploader?.takeIf(String::isNotBlank),
        thumbnailUrl = thumbnail?.takeIf(String::isNotBlank),
        durationMs = duration.toLong().coerceAtLeast(0) * 1_000L,
        extractor = extractorName,
        formats = candidates,
        cookieFilePath = cookieFilePath,
        userAgent = userAgent,
        referer = referer
    )
}

internal fun VideoInfo.toResolvedAudio(
    sourceUrl: String,
    cookieFilePath: String? = null,
    userAgent: String? = null,
    referer: String? = null,
): YoutubeDLResolvedAudio = YoutubeDLResolvedAudio(
    id = id?.takeIf(String::isNotBlank) ?: sourceUrl,
    sourceUrl = sourceUrl,
    title = (title ?: fulltitle)?.takeIf(String::isNotBlank) ?: "Audio",
    ownerName = uploader?.takeIf(String::isNotBlank),
    thumbnailUrl = thumbnail?.takeIf(String::isNotBlank),
    durationMs = duration.toLong().coerceAtLeast(0) * 1_000L,
    extractor = (extractorKey ?: extractor)?.takeIf(String::isNotBlank) ?: "YoutubeDL",
    estimatedSize = fileSize.takeIf { it > 0 } ?: fileSizeApproximate.takeIf { it > 0 },
    cookieFilePath = cookieFilePath,
    userAgent = userAgent,
    referer = referer,
)

internal data class YoutubeDLExtractedFormat(
    val formatId: String?,
    val formatNote: String?,
    val extension: String?,
    val videoCodec: String?,
    val audioCodec: String?,
    val width: Int,
    val height: Int,
    val totalBitrate: Int,
    val fileSize: Long,
    val approximateFileSize: Long,
    val url: String? = null,
    val manifestUrl: String? = null,
)

private enum class AudioStreamPresence {
    PRESENT,
    ABSENT,
    UNKNOWN,
}

private fun VideoFormat.toExtractedFormat() = YoutubeDLExtractedFormat(
    formatId = formatId,
    formatNote = formatNote,
    extension = ext,
    videoCodec = vcodec,
    audioCodec = acodec,
    width = width,
    height = height,
    totalBitrate = tbr,
    fileSize = fileSize,
    approximateFileSize = fileSizeApproximate,
    url = url,
    manifestUrl = manifestUrl,
)

private fun VideoInfo.toTopLevelExtractedFormat(): YoutubeDLExtractedFormat? {
    val selectedId = formatId?.takeIf(String::isNotBlank)
    val selectedUrl = url?.takeIf(String::isNotBlank)
    val selectedManifestUrl = manifestUrl?.takeIf(String::isNotBlank)
    if (selectedId == null && selectedUrl == null && selectedManifestUrl == null) return null

    return YoutubeDLExtractedFormat(
        formatId = selectedId ?: "best",
        formatNote = resolution?.takeIf(String::isNotBlank) ?: format?.takeIf(String::isNotBlank),
        extension = ext,
        videoCodec = null,
        audioCodec = null,
        width = width,
        height = height,
        totalBitrate = 0,
        fileSize = fileSize,
        approximateFileSize = fileSizeApproximate,
        url = selectedUrl,
        manifestUrl = selectedManifestUrl,
    )
}

internal fun mapYoutubeDLFormats(
    formats: List<YoutubeDLExtractedFormat>,
    isYouTube: Boolean,
): List<YoutubeDLFormatOption> = formats
    .asSequence()
    .sortedWith(compareByDescending<YoutubeDLExtractedFormat> { it.height }.thenByDescending { it.totalBitrate })
    .filter { it.rejectionReason(isYouTube) == null }
    .distinctBy(YoutubeDLExtractedFormat::qualityKey)
    .mapNotNull { it.toFormatOption(isYouTube) }
    .toList()

private fun YoutubeDLExtractedFormat.toFormatOption(isYouTube: Boolean): YoutubeDLFormatOption? {
    if (rejectionReason(isYouTube) != null) return null
    val selectedFormatId = formatId?.takeIf(String::isNotBlank) ?: return null
    val audioPresence = audioStreamPresence()

    return YoutubeDLFormatOption(
        id = selectedFormatId,
        label = buildString {
            append(
                height.takeIf { it > 0 }?.let { "${it}p" }
                    ?: formatNote?.takeIf(String::isNotBlank)
                    ?: extension?.takeIf(String::isNotBlank)?.uppercase()?.let { "$it video" }
                    ?: "Video"
            )
            append(
                when (audioPresence) {
                    AudioStreamPresence.PRESENT -> " · video + audio"
                    AudioStreamPresence.ABSENT -> " · merge audio"
                    AudioStreamPresence.UNKNOWN -> " · video + audio (auto)"
                }
            )
        },
        selector = when {
            audioPresence == AudioStreamPresence.PRESENT -> selectedFormatId
            isYouTube -> youtubeAvcWithAacSelector(selectedFormatId)
            audioPresence == AudioStreamPresence.ABSENT ->
                "$selectedFormatId+bestaudio/best"
            else -> "$selectedFormatId+bestaudio/$selectedFormatId/best"
        },
        suggestedExtension = extension.toVideoOutputExtension(),
        estimatedSize = fileSize.takeIf { it > 0 }
            ?: approximateFileSize.takeIf { it > 0 },
    )
}

private fun YoutubeDLExtractedFormat.rejectionReason(isYouTube: Boolean): String? = when {
    formatId.isNullOrBlank() -> "missing format_id"
    !hasVideoStream() -> if (videoCodec.isExplicitlyAbsent()) {
        "audio-only (vcodec=none)"
    } else {
        "no reliable video stream evidence"
    }
    isYouTube && !videoCodec.isH264Codec() -> "YouTube video codec is not H.264/AVC"
    isYouTube &&
            (audioStreamPresence() == AudioStreamPresence.PRESENT) &&
            !audioCodec.isAacCodec() -> "YouTube embedded audio codec is not AAC"
    else -> null
}

private fun YoutubeDLExtractedFormat.hasVideoStream(): Boolean {
    if (videoCodec.isExplicitlyAbsent()) return false
    if (!videoCodec.isNullOrBlank() || (width > 0 || height > 0)) return true

    val normalizedExtension = extension?.trim()?.lowercase()
    if (normalizedExtension in AUDIO_ONLY_EXTENSIONS) return false
    if (!audioCodec.isNullOrBlank() && !audioCodec.isExplicitlyAbsent()) return false
    if (normalizedExtension in VIDEO_OUTPUT_EXTENSIONS) return true

    return !url.isNullOrBlank() || !manifestUrl.isNullOrBlank()
}

private fun YoutubeDLExtractedFormat.audioStreamPresence(): AudioStreamPresence = when {
    audioCodec.isExplicitlyAbsent() -> AudioStreamPresence.ABSENT
    audioCodec.isNullOrBlank() -> AudioStreamPresence.UNKNOWN
    else -> AudioStreamPresence.PRESENT
}

private fun String?.isExplicitlyAbsent() = this?.trim().equals("none", ignoreCase = true)

private fun YoutubeDLExtractedFormat.qualityKey(): String =
    if (height > 0 || width > 0) {
        "resolution:$width:$height:${extension.orEmpty().lowercase()}:${videoCodec.orEmpty().lowercase()}"
    } else {
        "format:${formatId.orEmpty()}"
    }

private fun mapAndLogYoutubeDLFormats(
    stage: String,
    formats: List<YoutubeDLExtractedFormat>,
    isYouTube: Boolean,
): List<YoutubeDLFormatOption> {
    Log.d(YOUTUBE_DL_LOG_TAG, "YTDL_FORMAT stage=$stage inspecting=${formats.size}")
    formats.forEachIndexed { index, format ->
        val rejection = format.rejectionReason(isYouTube)
        val option = format.toFormatOption(isYouTube)
        Log.d(
            YOUTUBE_DL_LOG_TAG,
            "YTDL_FORMAT stage=$stage index=$index id=${format.formatId ?: "?"} " +
                    "note=${format.formatNote ?: "?"} ext=${format.extension ?: "?"} " +
                    "vcodec=${format.videoCodec ?: "unknown"} acodec=${format.audioCodec ?: "unknown"} " +
                    "resolution=${format.width}x${format.height} tbr=${format.totalBitrate} " +
                    "size=${format.fileSize.takeIf { it > 0 } ?: format.approximateFileSize} " +
                    "url=${format.url.toLogUrl()} manifest=${format.manifestUrl.toLogUrl()} " +
                    "decision=${rejection?.let { "REJECT:$it" } ?: "ACCEPT"} " +
                    "selector=${option?.selector ?: "-"}",
        )
    }

    return mapYoutubeDLFormats(formats, isYouTube).also { options ->
        Log.i(
            YOUTUBE_DL_LOG_TAG,
            "YTDL_FORMAT stage=$stage accepted=${options.size}/${formats.size} " +
                    "ids=${options.joinToString { it.id }}",
        )
    }
}

private fun String?.toLogUrl(): String {
    val value = this?.takeIf(String::isNotBlank) ?: return "-"
    return runCatching {
        val uri = URI(value)
        buildString {
            if (!uri.scheme.isNullOrBlank()) append(uri.scheme).append("://")
            append(uri.host ?: "?")
            append(uri.path.orEmpty().take(180))
            if (!uri.rawQuery.isNullOrBlank()) append("?<redacted>")
        }
    }.getOrElse { value.take(200) }
}

internal fun String?.isH264Codec(): Boolean {
    val codec = this?.trim()?.lowercase().orEmpty()
    return codec.startsWith("avc1") || codec.startsWith("avc") ||
            codec.startsWith("h264") || codec.startsWith("h.264")
}

internal fun String?.isAacCodec(): Boolean {
    val codec = this?.trim()?.lowercase().orEmpty()
    return codec.startsWith("mp4a") || codec == "aac" || codec.startsWith("aac.")
}

private fun youtubeAvcWithAacSelector(formatId: String) =
    "$formatId+bestaudio[ext=m4a]/" +
            "$formatId+bestaudio[acodec^=mp4a]/" +
            "$formatId+bestaudio[acodec^=aac]/" +
            "best[vcodec^=avc1][acodec^=mp4a][ext=mp4]"

internal fun String?.toVideoOutputExtension(): String =
    this?.lowercase()?.takeIf { it in VIDEO_OUTPUT_EXTENSIONS } ?: DEFAULT_VIDEO_OUTPUT_EXTENSION

internal fun String.isYouTubeSourceUrl(): Boolean {
    val host = runCatching { URI(this).host?.lowercase() }.getOrNull() ?: return false
    return host == "youtu.be" || host == "www.youtu.be" ||
            host == "youtube.com" || host.endsWith(".youtube.com")
}

internal fun String.isSoundCloudSourceUrl(): Boolean {
    val host = runCatching { URI(this).host?.lowercase() }.getOrNull() ?: return false
    return host == "soundcloud.com" || host.endsWith(".soundcloud.com")
}

internal fun String.isTikTokSourceUrl(): Boolean {
    val host = runCatching { URI(this).host?.lowercase() }.getOrNull() ?: return false
    return host == "tiktok.com" || host.endsWith(".tiktok.com")
}

internal fun YoutubeDLRequest.applyVimeoAudioOptions(sourceUrl: String): YoutubeDLRequest {
    val host = runCatching { URI(sourceUrl).host?.lowercase() }.getOrNull()
    if (host != "vimeo.com" && host?.endsWith(".vimeo.com") != true) return this
    // Playback renditions are sufficient for MP3; skip optional account/source-file lookups.
    addOption("--extractor-args", "vimeo:original_format_policy=never")
    addOption("--socket-timeout", "20")
    addOption("--retries", "2")
    addOption("--extractor-retries", "1")
    return this
}

internal fun Throwable.vimeoPlayerFallbackUrl(sourceUrl: String): String? {
    if (this !is YoutubeDLException) return null
    val uri = runCatching { URI(sourceUrl) }.getOrNull() ?: return null
    if (uri.scheme !in setOf("http", "https") || uri.userInfo != null || uri.port != -1 ||
        uri.host?.lowercase() !in setOf("vimeo.com", "www.vimeo.com")
    ) return null
    val match = Regex("^/([0-9]+)(?:/([a-f0-9]{10}))?/?$").matchEntire(uri.rawPath.orEmpty())
        ?: return null
    val videoId = match.groupValues[1]
    val loginError = Regex(
        "^ERROR:[ \\t]+\\[vimeo\\][ \\t]+$videoId:[ \\t]+" +
                "The web client only works when logged-in(?:\\.|$)"
    )
    if (message?.lineSequence()?.any(loginError::containsMatchIn) != true) return null

    val pathHash = match.groupValues[2].takeIf(String::isNotBlank)
    val query = uri.rawQuery.orEmpty()
    val queryHashes = runCatching {
        query.split('&').mapNotNull { parameter ->
            val name = URLDecoder.decode(parameter.substringBefore('='), StandardCharsets.UTF_8.name())
            if (name != "h") return@mapNotNull null
            URLDecoder.decode(parameter.substringAfter('=', ""), StandardCharsets.UTF_8.name())
        }
    }.getOrNull() ?: return null
    if (queryHashes.size > 1 || queryHashes.any { !it.matches(Regex("[a-f0-9]{10}")) }) return null
    val queryHash = queryHashes.singleOrNull()
    if (pathHash != null && queryHash != null && pathHash != queryHash) return null

    val playerQuery = if (pathHash != null && queryHash == null) {
        listOf(query, "h=$pathHash").filter(String::isNotBlank).joinToString("&")
    } else {
        query
    }
    return "https://player.vimeo.com/video/$videoId" +
            playerQuery.takeIf(String::isNotBlank)?.let { "?$it" }.orEmpty() +
            uri.rawFragment?.let { "#$it" }.orEmpty()
}

internal fun String.youtubeDLUserAgent(userAgent: String?): String? {
    if (isYouTubeSourceUrl()) return null
    val host = runCatching { URI(this).host?.lowercase() }.getOrNull()
    // Platform extractors expect their own network identity, not a WebView's mobile markup.
    // Generic/direct media URLs still need the browser UA that produced their signed URL.
    if (YOUTUBE_DL_DEFAULT_IDENTITY_DOMAINS.any { host == it || host?.endsWith(".$it") == true }) {
        return null
    }
    return userAgent?.takeIf(String::isNotBlank)
}

internal fun String.canonicalizeYouTubeUrl(): String {
    val uri = runCatching { URI(this) }.getOrNull() ?: return this
    val host = uri.host?.lowercase() ?: return this
    val videoId = when (host) {
        "youtu.be", "www.youtu.be" -> {
            uri.path.orEmpty().trim('/').substringBefore('/')
                .takeIf(YOUTUBE_VIDEO_ID_PATTERN::matches)
        }

        in YOUTUBE_WATCH_HOSTS if uri.path.orEmpty().trimEnd('/').equals("/watch", ignoreCase = true) -> {
            uri.rawQuery.orEmpty().split('&').firstNotNullOfOrNull { parameter ->
                val separator = parameter.indexOf('=')
                if (separator <= 0) return@firstNotNullOfOrNull null
                val name = URLDecoder.decode(
                    parameter.substring(0, separator),
                    StandardCharsets.UTF_8.name()
                )
                if (name != "v") return@firstNotNullOfOrNull null
                URLDecoder.decode(parameter.substring(separator + 1), StandardCharsets.UTF_8.name())
                    .takeIf(YOUTUBE_VIDEO_ID_PATTERN::matches)
            }
        }

        in YOUTUBE_WATCH_HOSTS if uri.path.orEmpty().startsWith("/shorts/", ignoreCase = true) -> {
            uri.path.orEmpty().substringAfter("/shorts/", "").substringBefore('/')
                .takeIf(YOUTUBE_VIDEO_ID_PATTERN::matches)
        }

        else -> null
    }

    return if (videoId != null) {
        "https://www.youtube.com/watch?v=$videoId"
    } else {
        this
    }
}

internal fun YoutubeDLRequest.applyYoutubeDLCommonOptions(): YoutubeDLRequest {
    addOption("--no-update")
    addOption("--no-playlist")
    return this
}

internal fun YoutubeDLRequest.applyYoutubeDLAudioResolveOptions(infoDirectory: File? = null): YoutubeDLRequest {
    // TikTok's audio-only format may be the soundtrack, not the clip's speech/mixed audio.
    addOption("--format", if (infoDirectory != null) "best/bestaudio" else "bestaudio/best")
    addOption("--skip-download")
    // Even skip-download can probe selected media; leave that work to the download service.
    addOption("--no-check-formats")
    addOption("--playlist-items", "1")
    if (infoDirectory != null) {
        // Write metadata only, without TikTok's media-format probes or any postprocessing.
        addOption("--no-simulate")
        addOption("--write-info-json")
        addOption("--output", File(infoDirectory, "resolved.%(ext)s").absolutePath)
    }
    return this
}

internal fun YoutubeDLRequest.applyRequestContext(
    sourceUrl: String,
    cookieFilePath: String?,
    userAgent: String?,
    referer: String?,
): YoutubeDLRequest {
    if (sourceUrl.isYouTubeSourceUrl()) {
        cookieFilePath?.takeIf(String::isNotBlank)?.let { addOption("--cookies", it) }
        return this
    }

    cookieFilePath?.takeIf(String::isNotBlank)?.let { addOption("--cookies", it) }
    sourceUrl.youtubeDLUserAgent(userAgent)?.let { addOption("--user-agent", it) }
    referer?.takeIf(String::isNotBlank)?.let { addOption("--referer", it) }
    return this
}

internal fun createNetscapeCookieFile(
    context: Context,
    sourceUrl: String,
    rawCookieHeader: String
): File? {
    val host = runCatching { URI(sourceUrl).host?.lowercase() }.getOrNull()
        ?.takeIf {
            it.isNotBlank() && it.none { character ->
                character == '\t' || character == '\r' || character == '\n'
            }
        }
        ?: return null
    val entries = rawCookieHeader.split(';').mapNotNull { entry ->
        val separator = entry.indexOf('=')
        if (separator <= 0) return@mapNotNull null
        val name = entry.substring(0, separator).trim()
        val value = entry.substring(separator + 1).trim()
        if (!name.matches(COOKIE_NAME_PATTERN) ||
            value.any { it == '\t' || it == '\r' || it == '\n' || it == '\u0000' }
        ) {
            return@mapNotNull null
        }
        "$host\tFALSE\t/\tFALSE\t0\t$name\t$value"
    }
    if (entries.isEmpty()) return null

    val directory = File(context.cacheDir, "youtube-dl/cookies").apply { mkdirs() }

    return File(directory, "${UUID.randomUUID()}.txt").apply {
        writeText((listOf("# Netscape HTTP Cookie File") + entries).joinToString("\n", postfix = "\n"))
    }
}

internal suspend fun ensureYoutubeDLInitialized(context: Context) {
    val appContext = context.applicationContext
    val updatePreferences = YoutubeDLUpdatePreferences(appContext)
    if (youtubeDLInitialized && !updatePreferences.shouldCheckForUpdate()) return

    youtubeDLInitMutex.withLock {
        if (!youtubeDLInitialized) {
            YoutubeDL.init(appContext)
            FFmpeg.init(appContext)
            youtubeDLInitialized = true
        }

        if (!updatePreferences.shouldCheckForUpdate()) return@withLock

        val previousVersion = YoutubeDL.version(appContext) ?: "bundled"
        Log.d(YOUTUBE_DL_LOG_TAG, "YTDL_RUNTIME checking stable update currentVersion=$previousVersion")
        runCatching {
            YoutubeDL.updateYoutubeDL(appContext, YoutubeDL.UpdateChannel.STABLE)
        }.onSuccess { updateStatus ->
            updatePreferences.markUpdateCheckSuccessful()
            Log.i(
                YOUTUBE_DL_LOG_TAG,
                "YTDL_RUNTIME stable update status=$updateStatus " +
                        "version=$previousVersion->${YoutubeDL.version(appContext) ?: previousVersion}",
            )
        }.onFailure { error ->
            Log.w(
                YOUTUBE_DL_LOG_TAG,
                "YTDL_RUNTIME stable update failed; continuing with version=$previousVersion",
                error,
            )
        }
    }
}

private const val YOUTUBE_DL_LOG_TAG = "MTHAI"

package com.looper.base.repository.youtubedl

import com.yausername.youtubedl_android.YoutubeDLRequest
import java.io.File

internal object YoutubeDLOutputNaming {
    private val invalid = Regex("""[\\/:*?\"<>|\p{Cntrl}]""")

    fun requestedName(input: String, fallback: String): String =
        input
            .trim()
            .replace(regex = invalid, replacement = "_")
            .trim('.', ' ')
            .take(180)
            .ifBlank {
                fallback.trim()
                    .replace(regex = invalid, replacement = "_")
                    .ifBlank {
                        "video"
                    }
            }

    fun finalName(requested: String, actualExtension: String): String {
        val clean = requestedName(requested, "video")

        val extension = actualExtension.toVideoOutputExtension()

        val current = clean.substringAfterLast('.', "").lowercase()

        val baseName = if (current.matches(Regex("[A-Za-z0-9]{1,5}"))) {
            clean.substringBeforeLast('.').ifBlank { "video" }
        } else {
            clean
        }

        return "$baseName.$extension"
    }

    fun finalAudioName(requested: String): String {
        val clean = requestedName(requested, "audio")
        val current = clean.substringAfterLast('.', "")
        val baseName = if (current.matches(Regex("[A-Za-z0-9]+"))) {
            clean.substringBeforeLast('.').trimEnd('.', ' ').ifBlank { "audio" }
        } else {
            clean
        }

        return "$baseName.mp3"
    }
}

internal fun YoutubeDLRequest.applyYoutubeDLDownloadOptions(
    formatSelector: String,
    outputDirectory: File,
    isAudio: Boolean,
): YoutubeDLRequest {
    addOption("--format", formatSelector)
    if (isAudio) {
        addOption("--extract-audio")
        addOption("--audio-format", "mp3")
        addOption("--audio-quality", 0)
        addOption("--embed-thumbnail")
        addOption("--convert-thumbnails", "jpg")
        addOption("--no-keep-video")
        addOption("--no-simulate")
        addOption("--playlist-items", "1")
    } else {
        addOption("--merge-output-format", "mp4")
        addOption("--no-audio-multistreams")
    }
    // Keep the source extension until FFmpeg finishes extraction/conversion.
    addOption("--output", File(outputDirectory, "output.%(ext)s").absolutePath)
    addOption("--progress")
    addOption("--newline")
    if (!isAudio) {
        addOption(
            "--print",
            "before_dl:YTDL_SELECTED:%(format_id)s|%(vcodec)s|%(acodec)s|%(resolution)s|%(protocol)s",
        )
    }
    addOption("--print", "after_move:$YOUTUBE_DL_PATH_MARKER%(filepath)s")
    addOption("--verbose")
    return this
}

internal fun createYoutubeDLDownloadRequest(
    sourceUrl: String,
    outputDirectory: File,
): YoutubeDLRequest {
    val infoFile = File(outputDirectory, YOUTUBE_DL_RESOLVED_INFO_NAME)
    return if (infoFile.isFile) {
        check(infoFile.length() > 0L) { "Resolved download information is empty" }
        // Do not also pass the webpage URL: that would trigger another platform extraction.
        YoutubeDLRequest(emptyList<String>()).apply {
            addOption("--load-info-json", infoFile.absolutePath)
        }
    } else {
        YoutubeDLRequest(sourceUrl)
    }
}

internal fun findMarkedOutput(stdout: String, directory: File): File? {
    val marker = stdout
        .lineSequence()
        .lastOrNull {
            it.startsWith(YOUTUBE_DL_PATH_MARKER)
        }?.removePrefix(YOUTUBE_DL_PATH_MARKER)
        ?.trim()
        ?.let(::File)

    return marker?.takeIf { it.isFile && it.parentFile?.canonicalFile == directory.canonicalFile }
}

internal const val YOUTUBE_DL_PATH_MARKER = "__YTDL_FINAL__:"
internal const val YOUTUBE_DL_RESOLVED_INFO_NAME = "resolved.info.json"

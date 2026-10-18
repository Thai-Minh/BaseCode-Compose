package com.looper.base.utils

import com.example.all.downloader.manager.data.ChildInput
import com.example.all.downloader.manager.data.ExecutionType
import com.example.all.downloader.manager.data.Input
import com.example.all.downloader.manager.data.InputRole
import com.example.all.downloader.manager.data.InputTransport
import com.example.all.downloader.manager.data.MediaType
import com.example.all.downloader.manager.data.Platform
import com.looper.all.catcher.data.CatchItem
import com.looper.all.catcher.data.CatchMedia
import com.looper.all.catcher.data.CatchPlatform
import com.looper.all.catcher.data.MediaInput
import com.looper.all.catcher.data.MediaInputRole
import com.looper.all.catcher.data.MediaTransport
import com.looper.all.catcher.data.id
import com.looper.all.catcher.data.name
import com.looper.base.repository.youtubedl.YoutubeDLOutputNaming
import com.looper.base.repository.youtubedl.data.YoutubeDLResolvedAudio
import com.looper.base.repository.youtubedl.data.YoutubeDLResolvedVideo
import com.looper.base.screen.download_test.data.BrowserCatchItem
import com.looper.base.screen.download_test.data.ChildCatchItem

private const val HIGH_QUALITY = 720
private val INVALID_FILE_NAME_CHARS = Regex("""[\\/:*?"<>|]""")

fun YoutubeDLResolvedAudio.toBrowserCatchItem(): BrowserCatchItem = BrowserCatchItem(
    id = id,
    title = title,
    ownerName = ownerName,
    ownerCoverUrl = null,
    thumbnailUrl = thumbnailUrl,
    duration = durationMs,
    childMediaItems = listOf(
        ChildCatchItem(
            media = CatchMedia.AudioMedia(
                id = id,
                name = "MP3",
                inputs = emptyList(),
                ffmpegCommand = null,
                outputName = YoutubeDLOutputNaming.finalAudioName("$title.mp3")
            ),
            isFirstGroup = true,
            isLastGroup = true
        )
    ),
    platform = Platform(id = extractor.lowercase(), iconUrl = ""),
    sourceUrl = sourceUrl
)

fun YoutubeDLResolvedVideo.toBrowserCatchItem(): BrowserCatchItem = BrowserCatchItem(
    id = id,
    title = title,
    ownerName = ownerName,
    ownerCoverUrl = null,
    thumbnailUrl = thumbnailUrl,
    duration = durationMs,
    childMediaItems = formats.mapIndexed { index, option ->
        ChildCatchItem(
            media = CatchMedia.VideoMedia(
                id = option.id,
                name = option.label,
                inputs = emptyList(),
                ffmpegCommand = null,
                outputName = "$title.${option.suggestedExtension}"
            ),
            isFirstGroup = index == 0,
            isLastGroup = index == formats.lastIndex,
            isBestQuality = index == 0,
            isHighQuality = option.label.findQualityNumber() >= HIGH_QUALITY
        )
    },
    platform = Platform(id = extractor.lowercase(), iconUrl = ""),
    sourceUrl = sourceUrl
)

fun CatchItem.toBrowserCatchItem(): BrowserCatchItem {
    return BrowserCatchItem(
        id = id,
        title = title,
        ownerName = ownerName,
        ownerCoverUrl = ownerCoverUrl,
        thumbnailUrl = thumbnailUrl,
        duration = duration,
        childMediaItems = medias.toCatchMediaView(),
        platform = platform.toDownloadPlatform(),
        sourceUrl = sourceUrl,
    )
}

fun convertToInput(item: BrowserCatchItem, catchMedia: CatchMedia): Input {

    return Input(
//        id = "${item.id}_${catchMedia.name}",
        id = catchMedia.id,
        owner = item.ownerName,
        ownerAvatarUrl = item.ownerCoverUrl,
        caption = item.title,
        postUrl = item.sourceUrl,
        durationMs = item.duration,
        thumbnailUrl = item.thumbnailUrl,
        childInputs = catchMedia.inputs.toChildInputs(),
        platform = item.platform,
        mediaType = catchMedia.mediaType,
        outputFileName = catchMedia.outputFileName,
        commandLine = catchMedia.ffmpegCommand,
        executionType = when {
            catchMedia.inputs.any { it.transport == MediaTransport.HLS } -> ExecutionType.HLS
            catchMedia.ffmpegCommand != null -> ExecutionType.LOCAL_MERGE
            else -> ExecutionType.DIRECT
        },
    )
}

private fun CatchPlatform.toDownloadPlatform(): Platform {
    return Platform(id = name.lowercase(), iconUrl = iconUrl)
}

private fun List<MediaInput>.toChildInputs(): List<ChildInput> {
    return this.map {
        ChildInput(
            url = it.url,
            headers = it.headers ?: emptyMap(),
            fileName = it.fileName,
            transport = when (it.transport) {
                MediaTransport.DIRECT -> InputTransport.DIRECT
                MediaTransport.HLS -> InputTransport.HLS
            },
            role = when (it.role) {
                MediaInputRole.MUXED -> InputRole.MUXED
                MediaInputRole.VIDEO -> InputRole.VIDEO
                MediaInputRole.AUDIO -> InputRole.AUDIO
            },
        )
    }
}

private fun List<CatchMedia>.toCatchMediaView(): List<ChildCatchItem> {
    return this.groupBy { it::class }.flatMap { (_, items) ->
        val bestQuality = items.maxByOrNull { it.name.findQualityNumber() }

        items.mapIndexed { index, item ->
            ChildCatchItem(
                media = item,
                isFirstGroup = index == 0,
                isLastGroup = index == items.lastIndex,
                isExpanded = false,
                isHighQuality = item.name.findQualityNumber() >= HIGH_QUALITY,
                isBestQuality = item.id == bestQuality?.id
            )
        }
    }
}

private fun String?.findQualityNumber(): Int {
    this ?: return 0

    val quality = findQuality()?.substringBeforeLast("p")?.toIntOrNull()

    return quality ?: 0
}

private fun String.findQuality(): String? {
    val regex = Regex("""(\d+p|[HS]D)""")
    val regex1 = Regex("""(\d+)x\d+""")

    val matchResult = regex.find(this)
    val resolutionMatch = regex1.find(this)

    val value = resolutionMatch?.groupValues?.get(1)

    return matchResult?.value?.format() ?: value
}

fun CatchMedia.toOutputFileName(fileName: String): String {
    val fallbackName = outputName?.takeIf { it.isNotBlank() }
        ?: inputs.firstOrNull()?.fileName?.takeIf { it.isNotBlank() }
        ?: "${id}.${mediaType.defaultExtension}"
    val sanitizedName = fileName.sanitizeFileName().ifBlank { fallbackName.sanitizeFileName() }

    return sanitizedName.withExtensionFrom(media = this)
}

val CatchMedia.outputFileName: String
    get() = toOutputFileName(fileName = outputName ?: inputs.firstOrNull()?.fileName ?: id)

private fun String.sanitizeFileName(): String {
    return trim().replace(INVALID_FILE_NAME_CHARS, "_")
}

private fun String.withExtensionFrom(media: CatchMedia): String {
    if (hasFileExtension()) return this

    val extension = media.outputName?.fileExtension()
        ?: media.inputs.firstOrNull()?.fileName?.fileExtension()
        ?: media.mediaType.defaultExtension

    return "$this.$extension"
}

private fun String.hasFileExtension(): Boolean {
    return fileExtension() != null
}

private fun String.fileExtension(): String? {
    return substringAfterLast(delimiter = ".", missingDelimiterValue = "")
        .takeIf { it.isNotBlank() && it.length < length }
}

private val MediaType.defaultExtension: String
    get() {
        return when (this) {
            MediaType.AUDIO -> "mp3"
            MediaType.IMAGE -> "jpg"
            else -> "mp4"
        }
    }

val CatchMedia.ffmpegCommand: String?
    get() {
        return when (this) {
            is CatchMedia.AudioMedia -> this.ffmpegCommand
            is CatchMedia.ImageMedia -> this.ffmpegCommand
            is CatchMedia.VideoMedia -> this.ffmpegCommand
        }
    }

val CatchMedia.outputName: String?
    get() {
        return when (this) {
            is CatchMedia.AudioMedia -> this.outputName
            is CatchMedia.ImageMedia -> this.outputName
            is CatchMedia.VideoMedia -> this.outputName
        }
    }

val CatchMedia.name: String
    get() {
        return when (this) {
            is CatchMedia.AudioMedia -> this.name
            is CatchMedia.ImageMedia -> this.name
            is CatchMedia.VideoMedia -> this.name
        }
    }

val CatchMedia.inputs: List<MediaInput>
    get() {
        return when (this) {
            is CatchMedia.AudioMedia -> this.inputs
            is CatchMedia.ImageMedia -> this.inputs
            is CatchMedia.VideoMedia -> this.inputs
        }
    }

val CatchMedia.mediaType: MediaType
    get() {
        return when (this) {
            is CatchMedia.VideoMedia -> {
                if (ffmpegCommand != null) {
                    MediaType.VIDEO_FFMPEG
                } else {
                    MediaType.VIDEO
                }
            }

            is CatchMedia.ImageMedia -> MediaType.IMAGE
            is CatchMedia.AudioMedia -> MediaType.AUDIO
        }
    }

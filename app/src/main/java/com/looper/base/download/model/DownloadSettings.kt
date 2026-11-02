package com.looper.base.download.model

enum class AudioDownloadFormat { M4A, OPUS, BEST }

object DownloadConstants {
    val SMART_COUNT_OPTIONS: List<Int> = listOf(10, 20, 50, 100)
    const val DEFAULT_SMART_COUNT = 20
    val VIDEO_HEIGHT_OPTIONS: List<Int> = listOf(360, 480, 720, 1080, 1440, 2160)
    const val DEFAULT_VIDEO_HEIGHT = 720
}

data class DownloadSettings(
    val smartDownloadsEnabled: Boolean = false,
    val smartDownloadsCount: Int = DownloadConstants.DEFAULT_SMART_COUNT,
    val unmeteredOnly: Boolean = true,
    val autoDownloadFavorites: Boolean = false,
    val audioFormat: AudioDownloadFormat = AudioDownloadFormat.M4A,
    val videoMaxHeight: Int = DownloadConstants.DEFAULT_VIDEO_HEIGHT,
) {
    fun normalized(): DownloadSettings = copy(
        smartDownloadsCount = smartDownloadsCount.takeIf {
            it in DownloadConstants.SMART_COUNT_OPTIONS
        } ?: DownloadConstants.DEFAULT_SMART_COUNT,
        videoMaxHeight = videoMaxHeight.takeIf {
            it in DownloadConstants.VIDEO_HEIGHT_OPTIONS
        } ?: DownloadConstants.DEFAULT_VIDEO_HEIGHT,
    )
}

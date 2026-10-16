package com.looper.videodownloader.repository.youtubedl.data

data class YoutubeDLRequestContext(
    val rawCookieHeader: String? = null,
    val userAgent: String? = null,
    val referer: String? = null
)

data class YoutubeDLResolvedVideo(
    val id: String,
    val sourceUrl: String,
    val title: String,
    val ownerName: String?,
    val thumbnailUrl: String?,
    val durationMs: Long,
    val extractor: String,
    val formats: List<YoutubeDLFormatOption>,
    val cookieFilePath: String? = null,
    val userAgent: String? = null,
    val referer: String? = null
)

data class YoutubeDLResolvedAudio(
    val id: String,
    val sourceUrl: String,
    val title: String,
    val ownerName: String?,
    val thumbnailUrl: String?,
    val durationMs: Long,
    val extractor: String,
    val formatSelector: String = "bestaudio/best",
    val estimatedSize: Long? = null,
    val cookieFilePath: String? = null,
    val userAgent: String? = null,
    val referer: String? = null,
    val downloadInfoJson: String? = null,
)

data class YoutubeDLFormatOption(
    val id: String,
    val label: String,
    val selector: String,
    val suggestedExtension: String,
    val estimatedSize: Long?
)

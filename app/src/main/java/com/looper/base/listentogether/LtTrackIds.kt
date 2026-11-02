package com.looper.base.listentogether

import androidx.core.net.toUri

object LtTrackIds {

    private const val MAX_ID_LENGTH = 200
    private val YOUTUBE_ID = Regex(pattern = "^[A-Za-z0-9_-]{11}$")
    private val YOUTUBE_HOSTS =
        setOf("youtube.com", "www.youtube.com", "m.youtube.com", "music.youtube.com")

    fun fromSourceUrl(sourceUrl: String): String =
        youtubeVideoId(url = sourceUrl) ?: sourceUrl.take(n = MAX_ID_LENGTH)

    fun toSourceUrl(trackId: String): String? = when {
        YOUTUBE_ID.matches(input = trackId) -> youtubeUrl(videoId = trackId)
        trackId.startsWith(prefix = "http://") || trackId.startsWith(prefix = "https://") ->
            youtubeVideoId(url = trackId)?.let(block = ::youtubeUrl) ?: trackId

        else -> null
    }

    fun normalizeSourceUrl(sourceUrl: String): String =
        youtubeVideoId(url = sourceUrl)?.let(block = ::youtubeUrl) ?: sourceUrl

    private fun youtubeUrl(videoId: String): String = "https://www.youtube.com/watch?v=$videoId"

    private fun youtubeVideoId(url: String): String? {
        val uri = runCatching {
            url.toUri()
        }.getOrNull() ?: return null
        val host = uri.host?.lowercase() ?: return null
        val candidate = when {
            host == "youtu.be" -> uri.pathSegments.firstOrNull()
            host in YOUTUBE_HOSTS -> uri.getQueryParameter("v")
                ?: uri.pathSegments.takeIf {
                    it.size >= 2 && it[0] in setOf("shorts", "live", "embed")
                }?.get(index = 1)

            else -> null
        }
        return candidate?.takeIf {
            YOUTUBE_ID.matches(input = it)
        }
    }
}

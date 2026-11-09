package com.looper.base.lyrics.data

import com.looper.base.database.data.LyricsDecorationEntity
import com.looper.base.database.data.LyricsEntity
import com.looper.base.database.data.LyricsFormat
import com.looper.base.lyrics.model.Lyrics
import com.looper.base.lyrics.model.LyricsConstants
import com.looper.base.lyrics.model.LyricsDecoration
import com.looper.base.lyrics.model.LyricsDecorationLine
import com.looper.base.lyrics.model.LyricsLine
import kotlinx.serialization.json.Json

private const val SONGWRITER_SEPARATOR = "\n"

internal val lyricsJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = false
}

fun LyricsEntity.toDomain(): Lyrics? {
    val lines = linesJson?.let { json ->
        runCatching {
            lyricsJson.decodeFromString<List<LyricsLine>>(json)
        }.getOrNull()
    } ?: return null
    return Lyrics(
        mediaItemId = mediaItemId,
        lines = lines,
        syncLevel = syncLevel,
        provider = source,
        language = language,
        songwriters = songwriters
            ?.split(SONGWRITER_SEPARATOR)
            ?.filter {
                it.isNotBlank()
            }.orEmpty(),
        sourceUrl = sourceUrl,
        userOffsetMs = userOffsetMs,
    )
}

fun Lyrics.toEntity(
    format: LyricsFormat?,
    rawContent: String?,
    matchScore: Int?,
    fetchedAt: Long = System.currentTimeMillis(),
) = LyricsEntity(
    mediaItemId = mediaItemId,
    syncLevel = syncLevel,
    format = format,
    rawContent = rawContent,
    linesJson = lyricsJson.encodeToString(value = lines),
    parserVersion = LyricsConstants.PARSER_VERSION,
    songwriters = songwriters.takeIf {
        it.isNotEmpty()
    }?.joinToString(separator = SONGWRITER_SEPARATOR),
    language = language,
    source = provider,
    sourceUrl = sourceUrl,
    matchScore = matchScore,
    userOffsetMs = userOffsetMs,
    fetchedAt = fetchedAt,
    lastAttemptAt = fetchedAt,
    attemptCount = 1,
)

fun LyricsDecorationEntity.toDomain(expectedLineCount: Int): LyricsDecoration? {
    if (lineCount != expectedLineCount) {
        return null
    }
    val lines = runCatching {
        lyricsJson.decodeFromString<List<LyricsDecorationLine>>(string = linesJson)
    }.getOrNull()
        ?.takeIf {
            it.size == lineCount
        } ?: return null
    return LyricsDecoration(
        kind = kind,
        language = language,
        source = source,
        lines = lines,
    )
}

fun LyricsDecoration.toEntity(mediaItemId: String) = LyricsDecorationEntity(
    mediaItemId = mediaItemId,
    kind = kind,
    language = language,
    source = source,
    linesJson = lyricsJson.encodeToString(value = lines),
    lineCount = lines.size,
)

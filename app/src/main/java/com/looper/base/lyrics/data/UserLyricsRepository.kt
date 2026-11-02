package com.looper.base.lyrics.data

import com.looper.base.database.dao.LyricsDao
import com.looper.base.database.dao.MediaItemDao
import com.looper.base.database.data.LyricsFormat
import com.looper.base.database.data.LyricsProvider
import com.looper.base.database.data.LyricsSyncLevel
import com.looper.base.lyrics.model.Lyrics
import com.looper.base.lyrics.parser.LrcParseResult
import com.looper.base.lyrics.parser.LrcParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class UserLyricsRepository(
    private val lyricsDao: LyricsDao,
    private val mediaItemDao: MediaItemDao,
) {

    suspend fun validate(mediaItemId: String, text: String): LrcParseResult {
        val durationMs = mediaItemDao.get(id = mediaItemId)?.durationMs?.takeIf {
            it > 0L
        }
        return withContext(Dispatchers.Default) {
            LrcParser.parse(text = text, durationMs = durationMs)
        }
    }

    suspend fun save(mediaItemId: String, text: String): LrcParseResult {
        val result = validate(mediaItemId = mediaItemId, text = text)
        if (!result.isValid) {
            return result
        }
        val existing = lyricsDao.get(mediaItemId = mediaItemId)
        val lyrics = Lyrics(
            mediaItemId = mediaItemId,
            lines = result.lines,
            syncLevel = result.syncLevel,
            provider = LyricsProvider.USER,
            userOffsetMs = existing?.userOffsetMs ?: 0L,
        )
        lyricsDao.replace(
            lyrics = lyrics.toEntity(
                format = if (result.syncLevel == LyricsSyncLevel.NONE) {
                    LyricsFormat.PLAIN
                } else {
                    LyricsFormat.LRC
                },
                rawContent = text,
                matchScore = null,
            )
        )
        return result
    }
}

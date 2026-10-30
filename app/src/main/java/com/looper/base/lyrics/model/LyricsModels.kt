package com.looper.base.lyrics.model

import com.looper.base.database.data.LyricsDecorationKind
import com.looper.base.database.data.LyricsDecorationSource
import com.looper.base.database.data.LyricsProvider
import com.looper.base.database.data.LyricsSyncLevel
import kotlinx.serialization.Serializable

object LyricsConstants {
    const val MIN_OFFSET_MS = -2_000L
    const val MAX_OFFSET_MS = 2_000L
    const val MIN_TRACK_OFFSET_MS = -30_000L
    const val MAX_TRACK_OFFSET_MS = 30_000L

    const val MIN_TEXT_SIZE_SP = 14
    const val MAX_TEXT_SIZE_SP = 40
    const val DEFAULT_TEXT_SIZE_SP = 24

    const val INSTRUMENTAL_GAP_MS = 5_000L
    const val LONG_WORD_MS = 1_500L

    const val PARSER_VERSION = 1
}

@Serializable
enum class LyricsLineSide { START, END, CENTER }

@Serializable
data class LyricsWord(
    val startMs: Long,
    val endMs: Long,
    val text: String,
    val romaji: String? = null,
    val endsWithSpace: Boolean = true,
) {
    val durationMs: Long get() = endMs - startMs
}

@Serializable
data class LyricsLine(
    val startMs: Long,
    val endMs: Long,
    val text: String,
    val words: List<LyricsWord> = emptyList(),
    val side: LyricsLineSide = LyricsLineSide.START,
    val isBackground: Boolean = false,
    val isInstrumental: Boolean = false,
    val songPart: String? = null,
) {
    val hasWordTimings: Boolean
        get() = when {
            words.isEmpty() -> false
            words.size > 1 -> true
            else -> {
                val word = words.first()
                word.endMs > word.startMs && word.text.filterNot {
                    it.isWhitespace()
                } != text.filterNot {
                    it.isWhitespace()
                }
            }
        }
}

data class Lyrics(
    val mediaItemId: String,
    val lines: List<LyricsLine>,
    val syncLevel: LyricsSyncLevel,
    val provider: LyricsProvider?,
    val language: String? = null,
    val songwriters: List<String> = emptyList(),
    val sourceUrl: String? = null,
    val userOffsetMs: Long = 0,
) {
    val isSynced: Boolean get() = syncLevel != LyricsSyncLevel.NONE

    fun activeLineIndex(positionMs: Long, hint: Int = -1): Int {
        if (!isSynced || lines.isEmpty() || positionMs < lines.first().startMs) {
            return -1
        }
        val found = if (isActiveAt(index = hint, positionMs = positionMs)) {
            hint
        } else if (isActiveAt(index = hint + 1, positionMs = positionMs)) {
            hint + 1
        } else {
            var low = 0
            var high = lines.lastIndex
            while (low < high) {
                val mid = (low + high + 1) ushr 1
                if (lines[mid].startMs <= positionMs) low = mid else high = mid - 1
            }
            low
        }
        var index = found
        while (index > 0 && lines[index].isBackground) {
            index--
        }
        return index
    }

    private fun isActiveAt(index: Int, positionMs: Long): Boolean {
        if (index !in lines.indices || lines[index].startMs > positionMs) {
            return false
        }
        val next = lines.getOrNull(index + 1)
        return next == null || next.startMs > positionMs
    }
}

@Serializable
data class LyricsDecorationLine(
    val text: String,
    val words: List<LyricsWord> = emptyList(),
)

data class LyricsDecoration(
    val kind: LyricsDecorationKind,
    val language: String,
    val source: LyricsDecorationSource,
    val lines: List<LyricsDecorationLine>,
)

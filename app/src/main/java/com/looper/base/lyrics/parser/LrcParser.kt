package com.looper.base.lyrics.parser

import com.looper.base.database.data.LyricsSyncLevel
import com.looper.base.lyrics.model.LyricsLine
import com.looper.base.lyrics.model.LyricsWord

enum class LrcIssueType {
    INVALID_TIMESTAMP,
    MISSING_TIMESTAMP,
    LINE_OUT_OF_ORDER,
    WORD_BEFORE_LINE,
    WORD_OUT_OF_ORDER,
    BEYOND_DURATION,
    EMPTY,
}

data class LrcIssue(
    val lineNumber: Int,
    val type: LrcIssueType,
    val column: Int? = null,
)

enum class LrcTokenKind { LINE_TIMESTAMP, WORD_TIMESTAMP, METADATA, INVALID, TEXT }

data class LrcToken(
    val start: Int,
    val end: Int,
    val kind: LrcTokenKind,
)

data class LrcParseResult(
    val lines: List<LyricsLine>,
    val syncLevel: LyricsSyncLevel,
    val issues: List<LrcIssue>,
    val metadata: Map<String, String>,
) {
    val isValid: Boolean
        get() = issues.isEmpty() && lines.isNotEmpty()
}

object LrcParser {

    private const val DEFAULT_LAST_LINE_MS = 5_000L
    private val TIME_TAG = Regex(pattern = """\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")
    private val WORD_TAG = Regex(pattern = """<(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?>""")
    private val METADATA_TAG = Regex(pattern = """^\[([a-zA-Z#]+):(.*)]$""")
    private val LOOKS_LIKE_TIME = Regex(pattern = """[\[<]\d[^\]>]*[\]>]?""")

    private data class RawWord(val startMs: Long, val text: String)

    private data class RawLine(
        val lineNumber: Int,
        val startMs: Long,
        val text: String,
        val words: List<RawWord>,
    )

    fun tokenize(line: String): List<LrcToken> {
        val tokens = mutableListOf<LrcToken>()
        if (METADATA_TAG.matchEntire(input = line.trim()) != null && TIME_TAG.find(input = line) == null) {
            return listOf(LrcToken(start = 0, end = line.length, kind = LrcTokenKind.METADATA))
        }
        var index = 0
        while (index < line.length) {
            val lineTag = TIME_TAG.find(input = line, startIndex = index)?.takeIf {
                it.range.first == index
            }
            val wordTag = WORD_TAG.find(input = line, startIndex = index)?.takeIf {
                it.range.first == index
            }
            val match = lineTag ?: wordTag
            if (match != null && match.validTime()) {
                tokens += LrcToken(
                    start = match.range.first,
                    end = match.range.last + 1,
                    kind = if (lineTag != null) {
                        LrcTokenKind.LINE_TIMESTAMP
                    } else {
                        LrcTokenKind.WORD_TIMESTAMP
                    },
                )
                index = match.range.last + 1
                continue
            }
            val invalid = LOOKS_LIKE_TIME.find(input = line, startIndex = index)?.takeIf {
                it.range.first == index
            }
            if (invalid != null) {
                tokens += LrcToken(
                    start = invalid.range.first,
                    end = invalid.range.last + 1,
                    kind = LrcTokenKind.INVALID
                )
                index = invalid.range.last + 1
                continue
            }
            val nextSpecial =
                line.indexOfAny(chars = charArrayOf('[', '<'), startIndex = index + 1).let {
                    if (it < 0) {
                        line.length
                    } else {
                        it
                    }
                }
            tokens += LrcToken(start = index, end = nextSpecial, kind = LrcTokenKind.TEXT)
            index = nextSpecial
        }
        return tokens
    }

    fun parse(text: String, durationMs: Long? = null): LrcParseResult {
        val issues = mutableListOf<LrcIssue>()
        val metadata = mutableMapOf<String, String>()
        val rawLines = mutableListOf<RawLine>()
        val plainLines = mutableListOf<Pair<Int, String>>()
        var offsetMs = 0L

        text.lines().forEachIndexed { index, original ->
            val lineNumber = index + 1
            val line = original.trim()
            if (line.isEmpty()) {
                return@forEachIndexed
            }
            if (TIME_TAG.find(input = line) == null) {
                METADATA_TAG.matchEntire(input = line)?.let { match ->
                    val key = match.groupValues[1].lowercase()
                    val value = match.groupValues[2].trim()
                    metadata[key] = value
                    if (key == "offset") {
                        offsetMs = value.toLongOrNull() ?: 0L
                    }
                    return@forEachIndexed
                }
            }
            var rest = line
            val starts = mutableListOf<Long>()
            while (true) {
                val match = TIME_TAG.find(input = rest)?.takeIf {
                    it.range.first == 0
                } ?: break
                if (!match.validTime()) {
                    issues += LrcIssue(
                        lineNumber = lineNumber,
                        type = LrcIssueType.INVALID_TIMESTAMP,
                        column = original.indexOf(string = match.value)
                    )
                    break
                }
                starts += match.toMillis()
                rest = rest.substring(startIndex = match.range.last + 1)
            }
            if (starts.isEmpty()) {
                if (LOOKS_LIKE_TIME.find(input = line)?.range?.first == 0) {
                    issues += LrcIssue(
                        lineNumber = lineNumber,
                        type = LrcIssueType.INVALID_TIMESTAMP,
                        column = original.indexOf(char = line.first())
                    )
                } else {
                    plainLines += lineNumber to line
                }
                return@forEachIndexed
            }
            val words = parseWords(
                lineNumber = lineNumber,
                body = rest,
                lineStart = starts.first(),
                issues = issues
            )
            val lineText = stripWordTags(body = rest)
            starts.forEach { start ->
                val shift = start - starts.first()
                rawLines += RawLine(
                    lineNumber = lineNumber,
                    startMs = start,
                    text = lineText,
                    words = words.map {
                        it.copy(startMs = it.startMs + shift)
                    },
                )
            }
        }

        if (rawLines.isEmpty()) {
            if (plainLines.isEmpty()) {
                return LrcParseResult(
                    lines = emptyList(),
                    syncLevel = LyricsSyncLevel.NONE,
                    issues = listOf(LrcIssue(lineNumber = 1, type = LrcIssueType.EMPTY)),
                    metadata = metadata
                )
            }
            return LrcParseResult(
                lines = plainLines.map { (_, line) ->
                    LyricsLine(startMs = 0L, endMs = 0L, text = line)
                },
                syncLevel = LyricsSyncLevel.NONE,
                issues = issues,
                metadata = metadata,
            )
        }

        plainLines.forEach { (lineNumber, _) ->
            issues += LrcIssue(lineNumber = lineNumber, type = LrcIssueType.MISSING_TIMESTAMP)
        }
        rawLines.distinctBy {
            it.lineNumber
        }.zipWithNext().forEach { (previous, next) ->
            if (next.startMs < previous.startMs) {
                issues += LrcIssue(
                    lineNumber = next.lineNumber,
                    type = LrcIssueType.LINE_OUT_OF_ORDER
                )
            }
        }
        if (durationMs != null && durationMs > 0L) {
            rawLines
                .filter {
                    it.startMs - offsetMs > durationMs
                }.forEach {
                    issues += LrcIssue(
                        lineNumber = it.lineNumber,
                        type = LrcIssueType.BEYOND_DURATION
                    )
                }
        }

        val sorted = rawLines.sortedBy {
            it.startMs
        }
        val lines = sorted.mapIndexed { index, raw ->
            val start = (raw.startMs - offsetMs).coerceAtLeast(minimumValue = 0L)
            val nextStart = sorted.getOrNull(index + 1)?.startMs?.minus(other = offsetMs)
            val end = nextStart
                ?: durationMs?.takeIf {
                    it > start
                } ?: (start + DEFAULT_LAST_LINE_MS)
            LyricsLine(
                startMs = start,
                endMs = end.coerceAtLeast(minimumValue = start),
                text = raw.text,
                words = buildWords(raw = raw, offsetMs = offsetMs, lineEnd = end),
            )
        }
        return LrcParseResult(
            lines = lines,
            syncLevel = if (lines.any {
                    it.hasWordTimings
                }) {
                LyricsSyncLevel.WORD
            } else {
                LyricsSyncLevel.LINE
            },
            issues = issues.distinct().sortedBy {
                it.lineNumber
            },
            metadata = metadata,
        )
    }

    private fun parseWords(
        lineNumber: Int,
        body: String,
        lineStart: Long,
        issues: MutableList<LrcIssue>
    ): List<RawWord> {
        val tags =
            (WORD_TAG.findAll(input = body) + TIME_TAG.findAll(input = body)).sortedBy {
                it.range.first
            }.toList()
        if (tags.isEmpty()) {
            return emptyList()
        }
        val words = mutableListOf<RawWord>()
        val leading = body.substring(startIndex = 0, endIndex = tags.first().range.first)
        if (leading.isNotBlank()) {
            words += RawWord(startMs = lineStart, text = leading)
        }
        var previous = lineStart
        tags.forEachIndexed { index, tag ->
            if (!tag.validTime()) {
                issues += LrcIssue(
                    lineNumber = lineNumber,
                    type = LrcIssueType.INVALID_TIMESTAMP,
                    column = tag.range.first
                )
                return@forEachIndexed
            }
            val time = tag.toMillis()
            when {
                time < lineStart -> issues += LrcIssue(
                    lineNumber = lineNumber,
                    type = LrcIssueType.WORD_BEFORE_LINE,
                    column = tag.range.first
                )

                time < previous -> issues += LrcIssue(
                    lineNumber = lineNumber,
                    type = LrcIssueType.WORD_OUT_OF_ORDER,
                    column = tag.range.first
                )
            }
            previous = maxOf(a = previous, b = time)
            val textEnd = tags.getOrNull(index + 1)?.range?.first ?: body.length
            words += RawWord(
                startMs = time, text = body.substring(
                    startIndex = tag.range.last + 1,
                    endIndex = textEnd
                )
            )
        }
        return words
    }

    private fun buildWords(raw: RawLine, offsetMs: Long, lineEnd: Long): List<LyricsWord> {
        val words = raw.words
        return words.mapIndexedNotNull { index, word ->
            if (word.text.isBlank()) {
                return@mapIndexedNotNull null
            }
            val start = (word.startMs - offsetMs).coerceAtLeast(minimumValue = 0L)
            val end =
                (words.getOrNull(index + 1)?.startMs?.minus(other = offsetMs)
                    ?: lineEnd).coerceAtLeast(
                    minimumValue = start
                )
            LyricsWord(
                startMs = start,
                endMs = end,
                text = word.text.trim(),
                endsWithSpace = word.text.endsWith(suffix = " ") || index == words.lastIndex,
            )
        }
    }

    private fun stripWordTags(body: String): String =
        body.replace(regex = WORD_TAG, replacement = "")
            .replace(regex = TIME_TAG, replacement = "")
            .replace(regex = Regex(pattern = "\\s+"), replacement = " ")
            .trim()

    private fun MatchResult.validTime(): Boolean {
        val seconds = groupValues[2].toIntOrNull() ?: return false
        return seconds < 60 && groupValues[1].toIntOrNull() != null
    }

    private fun MatchResult.toMillis(): Long {
        val minutes = groupValues[1].toLong()
        val seconds = groupValues[2].toLong()
        val fraction = groupValues[3]
        val millis = when (fraction.length) {
            0 -> 0L
            1 -> fraction.toLong() * 100L
            2 -> fraction.toLong() * 10L
            else -> fraction.take(n = 3).toLong()
        }
        return minutes * 60_000L + seconds * 1_000L + millis
    }
}

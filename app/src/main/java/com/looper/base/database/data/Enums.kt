package com.looper.base.database.data

enum class MediaType { AUDIO, VIDEO }

enum class DownloadKind { AUDIO, VIDEO }

enum class DownloadStatus { QUEUED, RUNNING, COMPLETED, FAILED, CANCELED }

enum class LyricsSyncLevel { NONE, LINE, WORD }

enum class LyricsFormat { PLAIN, LRC, TTML, KRC, QRC, YRC, RICHSYNC_JSON, OTHER }

enum class LyricsDecorationKind { TRANSLATION, ROMANIZATION }

enum class LyricsDecorationSource { EMBEDDED, GOOGLE, LOCAL }

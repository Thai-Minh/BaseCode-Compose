package com.looper.base.database.data

enum class LyricsProvider(
    val displayName: String,
    val isRemote: Boolean = true,
    val defaultEnabled: Boolean = false,
) {
    EMBEDDED(displayName = "Embedded tags", isRemote = false, defaultEnabled = true),
    USER(displayName = "Added by you", isRemote = false),
    LRCLIB(displayName = "LRCLIB", defaultEnabled = true),
    YOUTUBE_MUSIC(displayName = "YouTube Music"),
    APPLE_MUSIC(displayName = "Apple Music"),
    YOUTUBE_CAPTIONS(displayName = "YouTube Captions (Auto)"),
    QQ(displayName = "QQ"),
    NETEASE(displayName = "NetEase"),
    KUGOU(displayName = "KuGou"),
    PETIT_LYRICS(displayName = "PetitLyrics"),
    SODA(displayName = "Soda (Luna)"),
    BETTER_LYRICS(displayName = "BetterLyrics (bLyrics)", defaultEnabled = true),
    BINI_LYRICS(displayName = "BiniLyrics (Binimum)", defaultEnabled = true),
    UNISON(displayName = "Unison"),
    SIMP_MUSIC(displayName = "SimpMusic", defaultEnabled = true),
    AMLL(displayName = "AMLL"),
    LUNA_BEAT(displayName = "LunaBeat"),
    LYRICIFY(displayName = "Lyricify"),
    MUSIXMATCH(displayName = "Musixmatch"),
    RMM_REVIVAL(displayName = "RMM Revival"),
    GENIUS(displayName = "Genius");

    companion object {
        val REMOTE: List<LyricsProvider> = entries.filter { it.isRemote }

        val DEFAULT_PRIORITY: List<LyricsProvider> = REMOTE

        fun fromName(name: String?): LyricsProvider? =
            entries.firstOrNull {
                it.name.equals(other = name, ignoreCase = true)
            }
    }
}

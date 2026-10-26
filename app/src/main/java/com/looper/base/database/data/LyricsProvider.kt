package com.looper.base.database.data

enum class LyricsProvider(val displayName: String) {
    LRCLIB(displayName = "LRCLIB"),
    YOUTUBE_MUSIC(displayName = "YouTube Music"),
    APPLE_MUSIC(displayName = "Apple Music"),
    YOUTUBE_CAPTIONS(displayName = "YouTube Captions (Auto)"),
    QQ(displayName = "QQ"),
    NETEASE(displayName = "NetEase"),
    KUGOU(displayName = "KuGou"),
    PETIT_LYRICS(displayName = "PetitLyrics"),
    SODA(displayName = "Soda (Luna)"),
    BETTER_LYRICS(displayName = "BetterLyrics (bLyrics)"),
    BINI_LYRICS(displayName = "BiniLyrics (Binimum)"),
    UNISON(displayName = "Unison"),
    SIMP_MUSIC(displayName = "SimpMusic"),
    AMLL(displayName = "AMLL"),
    LUNA_BEAT(displayName = "LunaBeat"),
    LYRICIFY(displayName = "Lyricify"),
    MUSIXMATCH(displayName = "Musixmatch"),
    RMM_REVIVAL(displayName = "RMM Revival"),
    GENIUS(displayName = "Genius");

    companion object {
        val DEFAULT_PRIORITY: List<LyricsProvider> = entries

        fun fromName(name: String?): LyricsProvider? =
            entries.firstOrNull {
                it.name.equals(name, ignoreCase = true)
            }
    }
}

package com.looper.base.library.model

enum class DynamicPlaylist { FAVORITES, MOST_PLAYED, RECENTLY_PLAYED, DOWNLOADED, CACHED, TOP_THIS_MONTH }

enum class PlaylistSort { CUSTOM, TITLE, OLDEST_ADDED, NEWEST_ADDED }

enum class PlaylistGenre { POP, ROCK, HIP_HOP, ELECTRONIC, K_POP, LOFI, CLASSICAL, INDIE, RNB }

object LibraryConstants {
    const val DYNAMIC_PLAYLIST_LIMIT = 200
    const val SEARCH_HISTORY_LIMIT = 50
    const val MAX_SELECTION = 25
}

package com.looper.base.lastfm.model

data class LastFmSettings(
    val scrobbleEnabled: Boolean = true,
    val sessionKey: String? = null,
    val username: String? = null,
) {
    val isLoggedIn: Boolean
        get() = sessionKey != null
}

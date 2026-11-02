package com.looper.base.lastfm.data

import com.looper.base.base.storage.lastFmSettings
import com.looper.base.base.storage.updateLastFmSettings
import com.looper.base.lastfm.model.LastFmSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class LastFmRepository(
    private val client: LastFmClient,
) {

    companion object {
        const val AUTH_CALLBACK = "looper://lastfm-auth"
    }

    val settings: Flow<LastFmSettings> = lastFmSettings

    val isConfigured: Boolean
        get() = client.isConfigured

    fun authUrl(): String = client.authUrl(callbackUrl = AUTH_CALLBACK)

    suspend fun completeAuth(token: String): LastFmSession {
        val session = client.getSession(token = token)
        updateLastFmSettings(
            value = lastFmSettings.first().copy(
                sessionKey = session.key,
                username = session.username,
            )
        )
        return session
    }

    suspend fun setScrobbleEnabled(enabled: Boolean) {
        updateLastFmSettings(value = lastFmSettings.first().copy(scrobbleEnabled = enabled))
    }

    suspend fun logout() {
        updateLastFmSettings(
            value = lastFmSettings.first().copy(sessionKey = null, username = null)
        )
    }
}

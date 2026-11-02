package com.looper.base.lastfm.data

import android.net.Uri
import com.looper.base.BuildConfig
import com.looper.base.utils.SimpleHttp
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException
import java.security.MessageDigest

class LastFmException(val code: Int, message: String) : IOException(message) {
    val isSessionInvalid: Boolean
        get() = code == 9
}

data class LastFmSession(
    val username: String,
    val key: String,
)

data class LastFmTrack(
    val artist: String,
    val title: String,
    val album: String?,
    val durationSeconds: Long?,
)

class LastFmClient(
    private val apiKey: String = BuildConfig.LASTFM_API_KEY,
    private val apiSecret: String = BuildConfig.LASTFM_API_SECRET,
) {

    private companion object {
        const val API_URL = "https://ws.audioscrobbler.com/2.0/"
        const val AUTH_URL = "https://www.last.fm/api/auth/"
    }

    private val json = Json {
        ignoreUnknownKeys = true
    }

    val isConfigured: Boolean
        get() = apiKey.isNotEmpty() && apiSecret.isNotEmpty()

    fun authUrl(callbackUrl: String): String =
        "$AUTH_URL?api_key=${Uri.encode(apiKey)}&cb=${Uri.encode(callbackUrl)}"

    suspend fun getSession(token: String): LastFmSession {
        val session = call(
            method = "auth.getSession",
            params = mapOf("token" to token)
        )["session"]?.jsonObject
            ?: throw IOException("Last.fm returned no session")
        return LastFmSession(
            username = session["name"]?.jsonPrimitive?.content.orEmpty(),
            key = session["key"]?.jsonPrimitive?.content
                ?: throw IOException("Last.fm returned no session key"),
        )
    }

    suspend fun updateNowPlaying(sessionKey: String, track: LastFmTrack) {
        call(
            method = "track.updateNowPlaying",
            params = trackParams(track = track, indexed = false),
            sessionKey = sessionKey
        )
    }

    suspend fun scrobble(sessionKey: String, track: LastFmTrack, timestampSeconds: Long) {
        call(
            method = "track.scrobble",
            params = trackParams(track = track, indexed = true) + mapOf(
                "timestamp[0]" to timestampSeconds.toString(),
                "chosenByUser[0]" to "1",
            ),
            sessionKey = sessionKey,
        )
    }

    suspend fun setLoved(sessionKey: String, artist: String, title: String, loved: Boolean) {
        call(
            method = if (loved) {
                "track.love"
            } else {
                "track.unlove"
            },
            params = mapOf("artist" to artist, "track" to title),
            sessionKey = sessionKey,
        )
    }

    private fun trackParams(track: LastFmTrack, indexed: Boolean): Map<String, String> {
        val suffix = if (indexed) {
            "[0]"
        } else {
            ""
        }
        return buildMap {
            put("artist$suffix", track.artist)
            put("track$suffix", track.title)
            track.album?.takeIf {
                it.isNotBlank()
            }?.let {
                put("album$suffix", it)
            }
            track.durationSeconds?.takeIf {
                it > 0
            }?.let {
                put("duration$suffix", it.toString())
            }
        }
    }

    private suspend fun call(
        method: String,
        params: Map<String, String>,
        sessionKey: String? = null,
    ): JsonObject {
        if (!isConfigured) {
            throw IOException("Last.fm API key is not configured")
        }
        val signed = buildMap {
            putAll(from = params)
            put("method", method)
            put("api_key", apiKey)
            sessionKey?.let {
                put("sk", it)
            }
        }
        val signature = md5(
            text = signed.toSortedMap().entries.joinToString(separator = "") { (key, value) ->
                key + value
            } + apiSecret
        )
        val response = SimpleHttp.postForm(
            url = API_URL,
            form = signed + mapOf("api_sig" to signature, "format" to "json"),
        )
        val body = response.body?.let {
            runCatching {
                json.parseToJsonElement(string = it).jsonObject
            }.getOrNull()
        } ?: throw IOException("Last.fm HTTP ${response.code}")
        body["error"]?.let { error ->
            throw LastFmException(
                code = error.jsonPrimitive.int,
                message = body["message"]?.jsonPrimitive?.content ?: "Last.fm error",
            )
        }
        return body
    }

    private fun md5(text: String): String =
        MessageDigest.getInstance("MD5")
            .digest(text.toByteArray(charset = Charsets.UTF_8))
            .joinToString(separator = "") {
                "%02x".format(it)
            }
}

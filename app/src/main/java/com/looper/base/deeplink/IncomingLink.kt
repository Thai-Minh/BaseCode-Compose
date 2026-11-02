package com.looper.base.deeplink

import android.app.SearchManager
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.util.Patterns
import androidx.core.net.toUri

enum class AppDestination { HOME, SEARCH, LIBRARY, LISTEN_TOGETHER }

sealed interface IncomingLink {
    data class SharedUrl(val url: String) : IncomingLink

    data class Open(val destination: AppDestination) : IncomingLink

    data class PlayFromSearch(val query: String) : IncomingLink

    data class JoinRoom(val roomCode: String) : IncomingLink

    data class LastFmAuth(val token: String) : IncomingLink
}

object IncomingLinkParser {

    const val SCHEME = "looper"
    const val HOST_OPEN = "open"
    const val HOST_ROOM = "room"
    const val HOST_LASTFM_AUTH = "lastfm-auth"

    fun openUri(destination: AppDestination): Uri =
        "$SCHEME://$HOST_OPEN/${destination.name.lowercase()}".toUri()

    fun roomUri(roomCode: String): Uri = "$SCHEME://$HOST_ROOM/$roomCode".toUri()

    fun parse(intent: Intent?): IncomingLink? {
        intent ?: return null
        return when (intent.action) {
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
                ?.let(block = ::firstUrl)
                ?.let(block = IncomingLink::SharedUrl)

            MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH -> IncomingLink.PlayFromSearch(
                query = intent.getStringExtra(SearchManager.QUERY).orEmpty().trim(),
            )

            Intent.ACTION_VIEW -> intent.data?.let(block = ::parseUri)
            else -> null
        }
    }

    fun parseUri(uri: Uri): IncomingLink? = when (uri.scheme?.lowercase()) {
        "http", "https" -> IncomingLink.SharedUrl(url = uri.toString())
        SCHEME -> when (uri.host) {
            HOST_OPEN -> uri.lastPathSegment
                ?.let { name ->
                    AppDestination.entries.firstOrNull {
                        it.name.equals(other = name, ignoreCase = true)
                    }
                }
                ?.let(block = IncomingLink::Open)

            HOST_ROOM -> uri.lastPathSegment
                ?.takeIf {
                    it.isNotBlank()
                }?.let {
                    IncomingLink.JoinRoom(roomCode = it.uppercase())
                }

            HOST_LASTFM_AUTH -> uri.getQueryParameter("token")
                ?.takeIf {
                    it.isNotBlank()
                }?.let(block = IncomingLink::LastFmAuth)

            else -> null
        }

        else -> null
    }

    private fun firstUrl(text: String): String? {
        val matcher = Patterns.WEB_URL.matcher(text)
        while (matcher.find()) {
            val candidate = matcher.group()
            if (candidate.startsWith(prefix = "http://", ignoreCase = true) || candidate.startsWith(
                    prefix = "https://",
                    ignoreCase = true
                )
            ) {
                return candidate
            }
        }
        return null
    }
}

package com.looper.base.player.source

import android.os.Bundle
import androidx.media3.common.MediaItem
import com.looper.base.database.data.MediaType

object MediaItemKeys {
    const val HTTP_HEADERS = "http_headers"
    const val MEDIA_TYPE = "media_type"
    const val SOURCE_URL = "source_url"

    fun headersBundle(headers: Map<String, String>): Bundle = Bundle().apply {
        headers.forEach { (name, value) -> putString(name, value) }
    }

    fun MediaItem.httpHeaders(): Map<String, String> {
        val bundle = requestMetadata.extras?.getBundle(HTTP_HEADERS) ?: return emptyMap()
        return bundle.keySet().associateWith { key -> bundle.getString(key).orEmpty() }
    }

    fun MediaItem.mediaType(): MediaType? =
        mediaMetadata.extras?.getString(MEDIA_TYPE)
            ?.let { name -> MediaType.entries.firstOrNull { it.name == name } }
}

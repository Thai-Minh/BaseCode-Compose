package com.looper.base.player.source

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import com.looper.base.player.source.MediaItemKeys.httpHeaders

@OptIn(UnstableApi::class)
class HeaderAwareMediaSourceFactory(
    private val context: Context,
) : MediaSource.Factory {

    private val plain = DefaultMediaSourceFactory(context)

    override fun createMediaSource(mediaItem: MediaItem): MediaSource {
        val headers = mediaItem.httpHeaders()
        if (headers.isEmpty()) return plain.createMediaSource(mediaItem)

        val http = DefaultHttpDataSource.Factory()
            .setDefaultRequestProperties(headers)
            .setAllowCrossProtocolRedirects(true)
        headers["User-Agent"]?.let(http::setUserAgent)
        val dataSourceFactory = DefaultDataSource.Factory(context, http)
        return DefaultMediaSourceFactory(dataSourceFactory).createMediaSource(mediaItem)
    }

    override fun getSupportedTypes(): IntArray = plain.supportedTypes

    override fun setDrmSessionManagerProvider(
        drmSessionManagerProvider: DrmSessionManagerProvider,
    ): MediaSource.Factory = apply { plain.setDrmSessionManagerProvider(drmSessionManagerProvider) }

    override fun setLoadErrorHandlingPolicy(
        loadErrorHandlingPolicy: LoadErrorHandlingPolicy,
    ): MediaSource.Factory = apply { plain.setLoadErrorHandlingPolicy(loadErrorHandlingPolicy) }
}

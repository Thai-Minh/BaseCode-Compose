package com.looper.base.player.source

import android.content.Context
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import kotlinx.coroutines.runBlocking
import java.io.IOException

@OptIn(UnstableApi::class)
class PlaybackMediaSourceFactory(
    private val context: Context,
    private val cache: Cache,
    private val repository: StreamRepository,
) : MediaSource.Factory {

    private val plain = DefaultMediaSourceFactory(context)
    private val httpFactory = DefaultHttpDataSource.Factory()
        .setAllowCrossProtocolRedirects(true)
    private var drmSessionManagerProvider: DrmSessionManagerProvider? = null
    private var loadErrorHandlingPolicy: LoadErrorHandlingPolicy? = null

    override fun createMediaSource(mediaItem: MediaItem): MediaSource {
        val configuration = mediaItem.localConfiguration
        val mediaItemId = MediaItemKeys.streamMediaItemId(uri = configuration?.uri)
            ?: return plain.createMediaSource(mediaItem)

        if (MediaItemKeys.streamTrack(uri = configuration?.uri) == StreamTrack.SPLIT) {
            return MergingMediaSource(
                true,
                true,
                trackSource(
                    mediaItem = mediaItem,
                    mediaItemId = mediaItemId,
                    track = StreamTrack.VIDEO
                ),
                trackSource(
                    mediaItem = mediaItem,
                    mediaItemId = mediaItemId,
                    track = StreamTrack.AUDIO
                ),
            )
        }
        val resolving = resolvingFactory(mediaItemId = mediaItemId)
        val dataSourceFactory = if (MediaItemKeys.isAdaptive(mimeType = configuration?.mimeType)) {
            resolving
        } else {
            cached(upstream = resolving)
        }
        return DefaultMediaSourceFactory(dataSourceFactory)
            .apply {
                drmSessionManagerProvider?.let {
                    setDrmSessionManagerProvider(it)
                }
                loadErrorHandlingPolicy?.let {
                    setLoadErrorHandlingPolicy(it)
                }
            }
            .createMediaSource(mediaItem)
    }

    private fun trackSource(
        mediaItem: MediaItem,
        mediaItemId: String,
        track: StreamTrack
    ): MediaSource {
        val trackItem = mediaItem.buildUpon()
            .setUri(MediaItemKeys.streamUri(mediaItemId = mediaItemId, track = track))
            .setMimeType(null)
            .setCustomCacheKey(MediaItemKeys.cacheKey(mediaItemId = mediaItemId, track = track))
            .apply {
                if (track != StreamTrack.VIDEO) {
                    setSubtitleConfigurations(emptyList())
                }
            }
            .build()
        return DefaultMediaSourceFactory(cached(upstream = resolvingFactory(mediaItemId = mediaItemId)))
            .apply {
                loadErrorHandlingPolicy?.let {
                    setLoadErrorHandlingPolicy(it)
                }
            }
            .createMediaSource(trackItem)
    }

    private fun resolvingFactory(mediaItemId: String): DataSource.Factory =
        ResolvingDataSource.Factory(
            DefaultDataSource.Factory(context, httpFactory),
            StreamResolver(mediaItemId = mediaItemId, repository = repository),
        )

    private fun cached(upstream: DataSource.Factory): DataSource.Factory = CacheDataSource.Factory()
        .setCache(cache)
        .setUpstreamDataSourceFactory(upstream)
        .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

    override fun getSupportedTypes(): IntArray = plain.supportedTypes

    override fun setDrmSessionManagerProvider(
        drmSessionManagerProvider: DrmSessionManagerProvider,
    ): MediaSource.Factory = apply {
        this.drmSessionManagerProvider = drmSessionManagerProvider
        plain.setDrmSessionManagerProvider(drmSessionManagerProvider)
    }

    override fun setLoadErrorHandlingPolicy(
        loadErrorHandlingPolicy: LoadErrorHandlingPolicy,
    ): MediaSource.Factory = apply {
        this.loadErrorHandlingPolicy = loadErrorHandlingPolicy
        plain.setLoadErrorHandlingPolicy(loadErrorHandlingPolicy)
    }

    private class StreamResolver(
        private val mediaItemId: String,
        private val repository: StreamRepository,
    ) : ResolvingDataSource.Resolver {

        @Volatile
        private var headers: Map<String, String> = emptyMap()

        override fun resolveDataSpec(dataSpec: DataSpec): DataSpec {
            if (MediaItemKeys.streamMediaItemId(uri = dataSpec.uri) == null) {
                return if (headers.isEmpty()) {
                    dataSpec
                } else {
                    dataSpec.withAdditionalHeaders(headers)
                }
            }
            val source = try {
                runBlocking {
                    repository.playbackSource(mediaItemId = mediaItemId)
                }
            } catch (e: IOException) {
                throw e
            } catch (e: Exception) {
                throw IOException(e)
            }
            return when (source) {
                is PlaybackSource.Local -> dataSpec.withUri(source.uri.toUri())
                is PlaybackSource.Remote -> {
                    headers = source.headers
                    val url = when (MediaItemKeys.streamTrack(uri = dataSpec.uri)) {
                        StreamTrack.AUDIO -> source.audioUrl ?: source.url
                        else -> source.url
                    }
                    dataSpec.withUri(url.toUri()).withAdditionalHeaders(source.headers)
                }
            }
        }
    }
}

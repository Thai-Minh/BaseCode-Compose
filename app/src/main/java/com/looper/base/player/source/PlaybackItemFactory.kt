package com.looper.base.player.source

import android.net.Uri
import androidx.core.net.toUri
import androidx.core.os.bundleOf
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.looper.base.database.data.MediaItemEntity
import java.io.File

class PlaybackItemFactory(
    private val repository: StreamRepository,
) {

    fun build(item: MediaItemEntity, source: PlaybackSource?): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(item.title)
            .setArtist(item.artist)
            .setAlbumTitle(item.album)
            .setArtworkUri(artworkUri(item = item))
            .setDurationMs(item.durationMs.takeIf {
                it > 0L
            })
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .setExtras(
                bundleOf(
                    MediaItemKeys.MEDIA_TYPE to item.mediaType.name,
                    MediaItemKeys.SOURCE_URL to item.sourceUrl,
                    MediaItemKeys.IS_LIVE to item.isLive,
                )
            )
            .build()
        val builder = MediaItem.Builder()
            .setMediaId(item.id)
            .setMediaMetadata(metadata)
            .setRequestMetadata(
                MediaItem.RequestMetadata.Builder()
                    .setMediaUri(item.sourceUrl.toUri())
                    .build()
            )
        when (source) {
            is PlaybackSource.Local -> builder
                .setUri(source.uri)
                .setMimeType(source.mimeType)

            is PlaybackSource.Remote, null -> if (source?.isSplit == true) {
                builder.setSubtitleConfigurations(subtitleConfigurations(source = source))
                builder.setUri(
                    MediaItemKeys.streamUri(
                        mediaItemId = item.id,
                        track = StreamTrack.SPLIT
                    )
                )
            } else {
                builder
                    .setUri(MediaItemKeys.streamUri(mediaItemId = item.id))
                    .setMimeType(source?.mimeType)
                source?.let {
                    builder.setSubtitleConfigurations(subtitleConfigurations(source = it))
                }
                if (!item.isLive && !MediaItemKeys.isAdaptive(mimeType = source?.mimeType)) {
                    builder.setCustomCacheKey(
                        MediaItemKeys.cacheKey(
                            mediaItemId = item.id,
                            track = StreamTrack.MAIN
                        )
                    )
                }
            }
        }
        return builder.build()
    }

    suspend fun build(mediaItemId: String, resolve: Boolean): MediaItem? =
        buildAll(
            ids = listOf(mediaItemId),
            resolveId = if (resolve) {
                mediaItemId
            } else {
                null
            },
        ).firstOrNull()

    suspend fun buildAll(ids: List<String>, resolveId: String? = null): List<MediaItem> {
        val entities = repository.getMediaItems(ids = ids)
        return entities.map { entity ->
            val source = if (entity.id == resolveId) {
                runCatching {
                    repository.playbackSource(mediaItemId = entity.id)
                }.getOrNull() ?: repository.cachedSource(item = entity)
            } else {
                repository.cachedSource(item = entity)
            }
            build(item = entity, source = source)
        }
    }

    private fun subtitleConfigurations(source: PlaybackSource.Remote): List<MediaItem.SubtitleConfiguration> =
        source.subtitles.map {
            MediaItem.SubtitleConfiguration.Builder(it.url.toUri())
                .setMimeType(it.mimeType)
                .setLanguage(it.language)
                .setLabel(it.label)
                .setId("${if (it.isAutomatic) "auto" else "manual"}:${it.language}")
                .build()
        }

    private fun artworkUri(item: MediaItemEntity): Uri? {
        val local = item.thumbnailLocalPath
        if (local != null) {
            return if (local.startsWith(prefix = "/")) {
                Uri.fromFile(File(local))
            } else {
                local.toUri()
            }
        }
        return item.thumbnailUrl?.toUri()
    }
}

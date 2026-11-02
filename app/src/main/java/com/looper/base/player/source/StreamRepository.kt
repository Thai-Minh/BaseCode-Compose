package com.looper.base.player.source

import androidx.core.net.toUri
import com.looper.base.base.storage.playerSettings
import com.looper.base.database.dao.DownloadedFileDao
import com.looper.base.database.dao.MediaItemDao
import com.looper.base.database.dao.PlatformDao
import com.looper.base.database.dao.StreamCacheDao
import com.looper.base.database.data.DownloadKind
import com.looper.base.database.data.MediaItemEntity
import com.looper.base.database.data.MediaType
import com.looper.base.database.data.StreamCacheEntity
import com.looper.base.utils.SimpleHttp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

sealed interface PlaybackSource {
    val mimeType: String?

    data class Local(val uri: String, override val mimeType: String?) : PlaybackSource

    data class Remote(
        val url: String,
        override val mimeType: String?,
        val headers: Map<String, String>,
        val expiresAt: Long,
        val audioUrl: String? = null,
        val subtitles: List<SubtitleTrack> = emptyList(),
    ) : PlaybackSource {
        val isSplit: Boolean
            get() = audioUrl != null
    }
}

class StreamRepository(
    private val resolver: MediaResolver,
    private val mediaItemDao: MediaItemDao,
    private val platformDao: PlatformDao,
    private val streamCacheDao: StreamCacheDao,
    private val downloadedFileDao: DownloadedFileDao,
) {

    private companion object {
        const val DEFAULT_TTL_MS = 30 * 60_000L
        const val EXPIRY_MARGIN_MS = 60_000L
        const val ID_QUERY_CHUNK = 900
        const val ARTWORK_PROBE_LIMIT = 8
    }

    private val json = Json {
        ignoreUnknownKeys = true
    }

    private val resolveLocks = ConcurrentHashMap<String, Mutex>()

    suspend fun importFromUrl(sourceUrl: String): MediaItemEntity {
        val resolved = resolver.resolve(sourceUrl = sourceUrl, options = resolveOptions())
        val existing = mediaItemDao.getBySourceUrl(sourceUrl = sourceUrl)
        val platformId = resolved.extractorKey
            ?.lowercase()
            ?.takeIf {
                platformDao.get(id = it) != null
            }
        val entity = MediaItemEntity(
            id = existing?.id ?: UUID.randomUUID().toString(),
            mediaType = resolved.mediaType,
            sourceUrl = sourceUrl,
            sourceId = resolved.sourceId,
            platformId = platformId,
            title = resolved.title,
            artist = resolved.artist,
            album = resolved.album,
            durationMs = resolved.durationMs,
            thumbnailUrl = reachableArtwork(candidates = resolved.artworkUrls),
            thumbnailLocalPath = existing?.thumbnailLocalPath,
            isFavorite = existing?.isFavorite ?: false,
            favoritedAt = existing?.favoritedAt,
            addedAt = existing?.addedAt ?: System.currentTimeMillis(),
            playCount = existing?.playCount ?: 0,
            loudnessDb = existing?.loudnessDb,
            isLive = resolved.isLive,
        )
        if (existing == null) {
            mediaItemDao.insert(item = entity)
        } else {
            mediaItemDao.update(item = entity)
        }
        saveStream(mediaItemId = entity.id, stream = resolved.stream)
        return entity
    }

    private suspend fun reachableArtwork(candidates: List<String>): String? =
        candidates
            .take(n = ARTWORK_PROBE_LIMIT)
            .firstOrNull {
                SimpleHttp.isReachable(url = it)
            } ?: candidates.firstOrNull()

    suspend fun ensureMediaItem(
        sourceUrl: String,
        title: String,
        artist: String?,
        album: String?,
        durationMs: Long,
        thumbnailUrl: String?,
    ): MediaItemEntity {
        mediaItemDao.getBySourceUrl(sourceUrl = sourceUrl)?.let {
            return it
        }
        val entity = MediaItemEntity(
            id = UUID.randomUUID().toString(),
            mediaType = MediaType.AUDIO,
            sourceUrl = sourceUrl,
            title = title.ifBlank {
                sourceUrl
            },
            artist = artist?.takeIf {
                it.isNotBlank()
            },
            album = album?.takeIf {
                it.isNotBlank()
            },
            durationMs = durationMs,
            thumbnailUrl = thumbnailUrl?.takeIf {
                it.isNotBlank()
            },
        )
        mediaItemDao.insert(item = entity)
        return mediaItemDao.getBySourceUrl(sourceUrl = sourceUrl) ?: entity
    }

    suspend fun getMediaItems(ids: List<String>): List<MediaItemEntity> {
        val byId = ids.distinct()
            .chunked(size = ID_QUERY_CHUNK)
            .flatMap {
                mediaItemDao.getByIds(ids = it)
            }.associateBy {
                it.id
            }
        return ids.mapNotNull {
            byId[it]
        }
    }

    suspend fun cachedSource(item: MediaItemEntity): PlaybackSource? =
        localSource(item = item) ?: streamCacheDao.get(mediaItemId = item.id)?.toSource()

    suspend fun playbackSource(mediaItemId: String): PlaybackSource {
        val item = mediaItemDao.get(id = mediaItemId)
            ?: throw MediaResolver.ResolveException("Media item $mediaItemId not found")
        localSource(item = item)?.let {
            return it
        }
        return remoteSource(item = item)
    }

    suspend fun remoteSource(item: MediaItemEntity): PlaybackSource.Remote {
        val lock = resolveLocks.getOrPut(key = item.id) {
            Mutex()
        }
        return lock.withLock {
            val cached = streamCacheDao.get(mediaItemId = item.id)
            if (cached != null && cached.expiresAt - EXPIRY_MARGIN_MS > System.currentTimeMillis()) {
                cached.toSource()
            } else {
                val stream =
                    resolver.resolve(sourceUrl = item.sourceUrl, options = resolveOptions()).stream
                saveStream(mediaItemId = item.id, stream = stream).toSource()
            }
        }
    }

    suspend fun invalidate(mediaItemId: String) {
        streamCacheDao.invalidate(mediaItemId = mediaItemId)
    }

    private suspend fun resolveOptions(): ResolveOptions {
        val settings = playerSettings.first()
        return ResolveOptions(
            maxVideoHeight = settings.videoMaxHeight,
            preferredAudioLanguage = settings.preferredAudioLanguage,
            subtitleLanguage = settings.subtitleLanguage,
        )
    }

    private suspend fun localSource(item: MediaItemEntity): PlaybackSource.Local? {
        val preferred = when (item.mediaType) {
            MediaType.VIDEO -> listOf(DownloadKind.VIDEO, DownloadKind.AUDIO)
            MediaType.AUDIO -> listOf(DownloadKind.AUDIO, DownloadKind.VIDEO)
        }
        return preferred.firstNotNullOfOrNull { kind ->
            downloadedFileDao.get(mediaItemId = item.id, kind = kind)
                ?.takeIf {
                    isReadable(localUri = it.localUri)
                }?.let {
                    PlaybackSource.Local(uri = it.localUri, mimeType = it.mimeType)
                }
        }
    }

    private fun isReadable(localUri: String): Boolean {
        val uri = localUri.toUri()
        return when (uri.scheme) {
            null, "file" -> uri.path?.let {
                File(it).canRead()
            } == true

            else -> true
        }
    }

    private suspend fun saveStream(mediaItemId: String, stream: ResolvedStream): StreamCacheEntity {
        val now = System.currentTimeMillis()
        val entity = StreamCacheEntity(
            mediaItemId = mediaItemId,
            url = stream.url,
            audioUrl = stream.audioUrl,
            mimeType = stream.mimeType,
            headersJson = stream.headers.takeIf {
                it.isNotEmpty()
            }?.let {
                json.encodeToString(value = it)
            },
            subtitlesJson = stream.subtitles.takeIf {
                it.isNotEmpty()
            }?.let {
                json.encodeToString(value = it)
            },
            hasVideo = stream.hasVideo,
            formatId = stream.formatId,
            expiresAt = stream.expiresAt ?: (now + DEFAULT_TTL_MS),
            resolvedAt = now,
        )
        streamCacheDao.upsert(stream = entity)
        return entity
    }

    private fun StreamCacheEntity.toSource() = PlaybackSource.Remote(
        url = url,
        mimeType = mimeType,
        headers = headersJson
            ?.let {
                runCatching {
                    json.decodeFromString<Map<String, String>>(it)
                }.getOrNull()
            }.orEmpty(),
        expiresAt = expiresAt,
        audioUrl = audioUrl,
        subtitles = subtitlesJson
            ?.let {
                runCatching {
                    json.decodeFromString<List<SubtitleTrack>>(it)
                }.getOrNull()
            }.orEmpty(),
    )
}

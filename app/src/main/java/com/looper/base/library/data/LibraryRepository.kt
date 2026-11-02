package com.looper.base.library.data

import android.content.Context
import android.net.Uri
import com.looper.base.base.storage.lastFmSettings
import com.looper.base.database.AppDatabase
import com.looper.base.database.data.DownloadKind
import com.looper.base.database.data.MediaItemEntity
import com.looper.base.database.data.SearchHistoryEntity
import com.looper.base.lastfm.data.LastFmClient
import com.looper.base.library.model.DynamicPlaylist
import com.looper.base.library.model.LibraryConstants
import com.looper.base.library.model.PlaylistSort
import com.looper.base.storage.StorageRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.time.LocalDate
import java.time.ZoneId

class LibraryRepository(
    private val context: Context,
    private val database: AppDatabase,
    private val storageRepository: StorageRepository,
    private val lastFmClient: LastFmClient,
) {

    private companion object {
        const val COVER_DIRECTORY = "playlist_covers"
    }

    private val mediaItemDao = database.mediaItemDao()
    private val playlistDao = database.playlistDao()
    private val searchHistoryDao = database.searchHistoryDao()
    private val scope = CoroutineScope(context = SupervisorJob() + Dispatchers.IO)

    suspend fun dynamicPlaylist(type: DynamicPlaylist): List<MediaItemEntity> {
        val limit = LibraryConstants.DYNAMIC_PLAYLIST_LIMIT
        return when (type) {
            DynamicPlaylist.FAVORITES -> mediaItemDao.observeFavorites().first()
            DynamicPlaylist.MOST_PLAYED -> mediaItemDao.getMostPlayed(limit = limit)
            DynamicPlaylist.RECENTLY_PLAYED -> mediaItemDao.getRecentlyListened(limit = limit)
            DynamicPlaylist.DOWNLOADED -> (
                    mediaItemDao.observeDownloaded(kind = DownloadKind.AUDIO.name).first() +
                            mediaItemDao.observeDownloaded(kind = DownloadKind.VIDEO.name).first()
                    ).distinctBy {
                    it.id
                }

            DynamicPlaylist.CACHED -> storageRepository.cachedMediaItems()
            DynamicPlaylist.TOP_THIS_MONTH -> {
                val zone = ZoneId.systemDefault()
                val from = LocalDate.now(zone).withDayOfMonth(1).atStartOfDay(zone).toInstant()
                    .toEpochMilli()
                val ids = database.playbackEventDao()
                    .observeTopMedia(from = from, to = System.currentTimeMillis(), limit = limit)
                    .first()
                    .map {
                        it.mediaItemId
                    }
                val byId = mediaItemDao.getByIds(ids = ids).associateBy {
                    it.id
                }
                ids.mapNotNull {
                    byId[it]
                }
            }
        }
    }

    fun observePlaylist(
        playlistId: String,
        query: String = "",
        sort: PlaylistSort = PlaylistSort.CUSTOM
    ): Flow<List<MediaItemEntity>> =
        playlistDao.observeEntries(playlistId = playlistId).map { entries ->
            val needle = query.trim().lowercase()
            val filtered = if (needle.isEmpty()) {
                entries
            } else {
                entries.filter {
                    it.item.title.lowercase().contains(other = needle) || it.item.artist.orEmpty()
                        .lowercase().contains(
                            other = needle
                        )
                }
            }
            when (sort) {
                PlaylistSort.CUSTOM -> filtered.sortedBy {
                    it.position
                }

                PlaylistSort.TITLE -> filtered.sortedBy {
                    it.item.title.lowercase()
                }

                PlaylistSort.OLDEST_ADDED -> filtered.sortedBy {
                    it.playlistAddedAt
                }

                PlaylistSort.NEWEST_ADDED -> filtered.sortedByDescending {
                    it.playlistAddedAt
                }
            }.map {
                it.item
            }
        }

    suspend fun addToPlaylist(playlistId: String, mediaItemIds: List<String>): Int =
        mediaItemIds.distinct().count {
            playlistDao.addToEnd(playlistId = playlistId, mediaItemId = it)
        }

    suspend fun removeFromPlaylist(playlistId: String, mediaItemIds: List<String>) {
        mediaItemIds.forEach {
            playlistDao.removeItem(playlistId = playlistId, mediaItemId = it)
        }
        playlistDao.touch(id = playlistId)
    }

    suspend fun setPlaylistCover(playlistId: String, image: Uri?) = withContext(Dispatchers.IO) {
        val directory = File(context.filesDir, COVER_DIRECTORY).apply {
            mkdirs()
        }
        val target = File(directory, "$playlistId.jpg")
        if (image == null) {
            target.delete()
            playlistDao.setThumbnail(id = playlistId, thumbnailPath = null)
            return@withContext
        }
        context.contentResolver.openInputStream(image)?.use { input ->
            target.outputStream().use {
                input.copyTo(out = it)
            }
        } ?: throw IOException("Cannot read $image")
        playlistDao.setThumbnail(id = playlistId, thumbnailPath = target.absolutePath)
    }

    suspend fun setFavorite(mediaItemIds: List<String>, favorite: Boolean) {
        val now = System.currentTimeMillis()
        val items = mediaItemDao.getByIds(ids = mediaItemIds)
        items.forEach {
            mediaItemDao.setFavorite(
                id = it.id,
                isFavorite = favorite,
                favoritedAt = if (favorite) {
                    now
                } else {
                    null
                }
            )
        }
        val settings = lastFmSettings.first()
        val sessionKey =
            settings.sessionKey?.takeIf {
                settings.scrobbleEnabled && lastFmClient.isConfigured
            } ?: return
        scope.launch {
            items
                .filter {
                    !it.artist.isNullOrBlank()
                }
                .forEach {
                    runCatching {
                        lastFmClient.setLoved(
                            sessionKey = sessionKey,
                            artist = it.artist!!,
                            title = it.title,
                            loved = favorite
                        )
                    }
                }
        }
    }

    fun observeSearchHistory(limit: Int = LibraryConstants.SEARCH_HISTORY_LIMIT): Flow<List<String>> =
        searchHistoryDao.observeRecent(limit = limit).map { entries ->
            entries.map {
                it.query
            }
        }

    suspend fun recordSearch(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            return
        }
        searchHistoryDao.upsert(entry = SearchHistoryEntity(query = trimmed))
        searchHistoryDao.trim(keep = LibraryConstants.SEARCH_HISTORY_LIMIT)
    }

    suspend fun deleteSearch(query: String) {
        searchHistoryDao.delete(query = query)
    }

    suspend fun clearSearchHistory() {
        searchHistoryDao.clear()
    }
}

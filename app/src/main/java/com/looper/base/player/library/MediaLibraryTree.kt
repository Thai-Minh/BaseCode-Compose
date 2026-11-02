package com.looper.base.player.library

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.looper.base.R
import com.looper.base.database.AppDatabase
import com.looper.base.database.data.DownloadKind
import com.looper.base.database.data.MediaItemEntity
import com.looper.base.database.data.MediaType
import kotlinx.coroutines.flow.first
import java.io.File

data class LibrarySelection(
    val mediaItemIds: List<String>,
    val startIndex: Int,
)

class MediaLibraryTree(
    private val context: Context,
    private val database: AppDatabase,
) {

    private companion object {
        const val ROOT = "root"
        const val FAVORITES = "favorites"
        const val DOWNLOADED = "downloaded"
        const val RECENT = "recent"
        const val PLAYLISTS = "playlists"
        const val PLAYLIST_PREFIX = "playlist/"
        const val SEARCH_PREFIX = "search/"
        const val ITEM_SEPARATOR = "::"
        const val RECENT_LIMIT = 100
        const val SEARCH_LIMIT = 50
    }

    private val mediaItemDao = database.mediaItemDao()
    private val playlistDao = database.playlistDao()

    fun root(): MediaItem = folder(id = ROOT, title = context.getString(R.string.app_name))

    suspend fun children(parentId: String): List<MediaItem> = when {
        parentId == ROOT -> listOf(
            folder(id = FAVORITES, title = context.getString(R.string.library_favorites)),
            folder(id = DOWNLOADED, title = context.getString(R.string.library_downloaded)),
            folder(id = RECENT, title = context.getString(R.string.library_recent)),
            folder(id = PLAYLISTS, title = context.getString(R.string.library_playlists)),
        )

        parentId == PLAYLISTS -> playlistDao.observeSummaries().first().map {
            folder(id = PLAYLIST_PREFIX + it.playlist.id, title = it.playlist.name)
        }

        else -> entries(parentId = parentId).map {
            playable(parentId = parentId, item = it)
        }
    }

    suspend fun item(mediaId: String): MediaItem? {
        if (mediaId == ROOT) {
            return root()
        }
        val separator = mediaId.indexOf(string = ITEM_SEPARATOR)
        if (separator >= 0) {
            val parentId = mediaId.substring(0, separator)
            val item =
                mediaItemDao.get(id = mediaId.substring(startIndex = separator + ITEM_SEPARATOR.length))
                    ?: return null
            return playable(parentId = parentId, item = item)
        }
        return children(parentId = ROOT).firstOrNull {
            it.mediaId == mediaId
        }
            ?: if (mediaId.startsWith(prefix = PLAYLIST_PREFIX)) {
                playlistDao.get(id = mediaId.removePrefix(prefix = PLAYLIST_PREFIX))?.let {
                    folder(id = mediaId, title = it.name)
                }
            } else {
                null
            }
    }

    suspend fun search(query: String): List<MediaItem> {
        val parentId = SEARCH_PREFIX + query
        return entries(parentId = parentId).map {
            playable(parentId = parentId, item = it)
        }
    }

    suspend fun searchMediaItemIds(query: String): List<String> =
        entries(parentId = SEARCH_PREFIX + query).map {
            it.id
        }

    suspend fun resolveSelection(mediaId: String): LibrarySelection? {
        val separator = mediaId.indexOf(string = ITEM_SEPARATOR)
        if (separator < 0) {
            return null
        }
        val parentId = mediaId.substring(0, separator)
        val mediaItemId = mediaId.substring(startIndex = separator + ITEM_SEPARATOR.length)
        val ids = entries(parentId = parentId).map {
            it.id
        }
        val index = ids.indexOf(mediaItemId)
        return if (index >= 0) {
            LibrarySelection(mediaItemIds = ids, startIndex = index)
        } else {
            LibrarySelection(mediaItemIds = listOf(mediaItemId), startIndex = 0)
        }
    }

    fun stripParent(mediaId: String): String {
        val separator = mediaId.indexOf(string = ITEM_SEPARATOR)
        return if (separator >= 0) {
            mediaId.substring(startIndex = separator + ITEM_SEPARATOR.length)
        } else {
            mediaId
        }
    }

    private suspend fun entries(parentId: String): List<MediaItemEntity> = when {
        parentId == FAVORITES -> mediaItemDao.observeFavorites().first()
        parentId == DOWNLOADED -> (
                mediaItemDao.observeDownloaded(kind = DownloadKind.AUDIO.name).first() +
                        mediaItemDao.observeDownloaded(kind = DownloadKind.VIDEO.name).first()
                ).distinctBy {
                it.id
            }

        parentId == RECENT -> mediaItemDao.getRecentlyListened(limit = RECENT_LIMIT)
        parentId.startsWith(prefix = PLAYLIST_PREFIX) ->
            playlistDao.observeItems(playlistId = parentId.removePrefix(prefix = PLAYLIST_PREFIX))
                .first()

        parentId.startsWith(prefix = SEARCH_PREFIX) ->
            mediaItemDao.search(query = parentId.removePrefix(prefix = SEARCH_PREFIX)).first()
                .take(n = SEARCH_LIMIT)

        else -> emptyList()
    }

    private fun folder(id: String, title: String): MediaItem = MediaItem.Builder()
        .setMediaId(id)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                .build()
        ).build()

    private fun playable(parentId: String, item: MediaItemEntity): MediaItem = MediaItem.Builder()
        .setMediaId(parentId + ITEM_SEPARATOR + item.id)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(item.title)
                .setArtist(item.artist)
                .setAlbumTitle(item.album)
                .setArtworkUri(artworkUri(item = item))
                .setDurationMs(item.durationMs.takeIf {
                    it > 0L
                })
                .setIsBrowsable(false)
                .setIsPlayable(true)
                .setMediaType(
                    if (item.mediaType == MediaType.VIDEO) {
                        MediaMetadata.MEDIA_TYPE_VIDEO
                    } else {
                        MediaMetadata.MEDIA_TYPE_MUSIC
                    }
                )
                .build()
        )
        .build()

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

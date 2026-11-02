package com.looper.base.storage

import android.content.Context
import android.os.StatFs
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.ContentMetadata
import coil3.SingletonImageLoader
import com.looper.base.database.DATABASE_NAME
import com.looper.base.database.dao.DownloadedFileDao
import com.looper.base.database.dao.MediaItemDao
import com.looper.base.database.data.MediaItemEntity
import com.looper.base.player.source.MediaItemKeys
import com.looper.base.player.source.StreamTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File

data class StorageUsage(
    val playerCacheBytes: Long,
    val downloadsBytes: Long,
    val imageCacheBytes: Long,
    val databaseBytes: Long,
    val otherAppBytes: Long,
    val freeBytes: Long,
    val totalBytes: Long,
)

@OptIn(UnstableApi::class)
class StorageRepository(
    private val context: Context,
    private val cache: Cache,
    private val mediaItemDao: MediaItemDao,
    private val downloadedFileDao: DownloadedFileDao,
) {

    private companion object {
        const val DOWNLOAD_DIRECTORY = "downloads"
    }

    suspend fun usage(): StorageUsage = withContext(Dispatchers.IO) {
        val playerCache = cache.cacheSpace
        val downloads = downloadRoots().sumOf {
            sizeOf(file = it)
        }
        val imageCache = SingletonImageLoader.get(context).diskCache?.size ?: 0L
        val database = listOf("", "-wal", "-shm").sumOf {
            File(context.getDatabasePath(DATABASE_NAME).path + it).length()
        }
        val appTotal = sizeOf(file = context.filesDir) + sizeOf(file = context.cacheDir) +
                (context.getExternalFilesDir(null)?.let(block = ::sizeOf) ?: 0L) + database
        val stat = StatFs(context.filesDir.absolutePath)
        StorageUsage(
            playerCacheBytes = playerCache,
            downloadsBytes = downloads,
            imageCacheBytes = imageCache,
            databaseBytes = database,
            otherAppBytes = (appTotal - playerCache - downloads - imageCache - database).coerceAtLeast(
                minimumValue = 0L
            ),
            freeBytes = stat.availableBytes,
            totalBytes = stat.totalBytes,
        )
    }

    suspend fun clearPlayerCache() = withContext(Dispatchers.IO) {
        cache.keys.toList().forEach {
            cache.removeResource(it)
        }
    }

    suspend fun clearImageCache() = withContext(context = Dispatchers.IO) {
        val loader = SingletonImageLoader.get(context = context)
        loader.memoryCache?.clear()
        loader.diskCache?.clear()
    }

    suspend fun cachedMediaItems(): List<MediaItemEntity> {
        val downloaded = downloadedFileDao.observeAll().first().mapTo(destination = HashSet()) {
            it.mediaItemId
        }
        val ids = withContext(context = Dispatchers.IO) {
            cache.keys
                .filter(predicate = ::isFullyCached)
                .mapNotNull(transform = ::mediaItemIdOfKey)
                .toSet()
        }.filterNot {
            it in downloaded
        }
        return mediaItemDao.getByIds(ids = ids.toList()).sortedBy {
            it.title.lowercase()
        }
    }

    suspend fun removeFromCache(mediaItemId: String) = withContext(Dispatchers.IO) {
        StreamTrack.entries.forEach {
            cache.removeResource(MediaItemKeys.cacheKey(mediaItemId = mediaItemId, track = it))
        }
    }

    private fun isFullyCached(key: String): Boolean {
        val length = ContentMetadata.getContentLength(cache.getContentMetadata(key))
        return length > 0L && cache.isCached(key, 0L, length)
    }

    private fun mediaItemIdOfKey(key: String): String? {
        val separator = key.indexOf(char = '#')
        if (separator < 0) {
            return key
        }
        return key.takeIf {
            it.endsWith(suffix = "#${StreamTrack.AUDIO.name.lowercase()}")
        }?.substring(
            startIndex = 0,
            endIndex = separator
        )
    }

    private fun downloadRoots(): List<File> = listOfNotNull(
        context.getExternalFilesDir(null)?.let {
            File(it, DOWNLOAD_DIRECTORY)
        },
        File(context.filesDir, DOWNLOAD_DIRECTORY),
    )

    private fun sizeOf(file: File): Long = when {
        !file.exists() -> 0L
        file.isFile -> file.length()
        else -> file.walkBottomUp().filter {
            it.isFile
        }.sumOf {
            it.length()
        }
    }
}

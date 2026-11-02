package com.looper.base.player.source

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

@OptIn(UnstableApi::class)
object PlayerCacheFactory {

    private const val CACHE_DIRECTORY = "media_cache"
    private const val BYTES_PER_MB = 1024L * 1024L

    fun create(context: Context, sizeMb: Int): Cache = SimpleCache(
        File(context.cacheDir, CACHE_DIRECTORY),
        LeastRecentlyUsedCacheEvictor(sizeMb * BYTES_PER_MB),
        StandaloneDatabaseProvider(context),
    )
}

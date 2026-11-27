package com.looper.base.repository.wallpaper

import android.app.WallpaperManager
import android.content.Context
import android.net.Uri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.InputStream

class WallpaperRepositoryImpl(
    private val context: Context
): IWallpaperRepository {
    private val wallpaperManager = WallpaperManager.getInstance(context)

    override suspend fun setHomeScreen(uri: Uri) =
        setWallpaper(uri, WallpaperManager.FLAG_SYSTEM)


    override suspend fun setHomeAndLockScreens(uri: Uri) =
        setWallpaper(uri, WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK)


    private suspend fun setWallpaper(uri: Uri, flags: Int): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                openInputStream(uri).use { input ->
                    wallpaperManager.setStream(input, null, true, flags)
                }
                Unit
            }.onFailure { exception ->
                if (exception is CancellationException) throw exception
            }
        }


    private fun openInputStream(uri: Uri): InputStream {
        if (uri.scheme == "file") {
            val path = requireNotNull(uri.path) { "Wallpaper file path is missing" }
            val file = File(path)
            require(file.isFile && file.length() > 0L) { "Wallpaper file is unavailable" }
            return FileInputStream(file)
        }
        return requireNotNull(context.contentResolver.openInputStream(uri)) {
            "Unable to open wallpaper URI"
        }
    }
}
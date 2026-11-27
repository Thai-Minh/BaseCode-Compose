package com.looper.base.repository.wallpaper

import android.net.Uri

interface IWallpaperRepository {
    suspend fun setHomeScreen(uri: Uri): Result<Unit>

    suspend fun setHomeAndLockScreens(uri: Uri): Result<Unit>
}
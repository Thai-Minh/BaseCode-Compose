package com.looper.base.repository.download

import android.net.Uri
import com.looper.base.repository.download.data.DownloadWallpaperInfo

interface IDownloadManager {
    suspend fun downloadWallpapers(infos: List<DownloadWallpaperInfo>): List<Uri?>

    suspend fun downloadWallpaper(info: DownloadWallpaperInfo): Result<Uri>

    suspend fun downloadWallpaper(imageUrl: String): Result<Uri>

    fun findDownloadedWallpaper(imageUrl: String): Uri?
}
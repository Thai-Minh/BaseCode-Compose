package com.looper.base.repository.download.data

data class DownloadWallpaperInfo(
    val fileName: String,
    val url: String,
    val folderName: String,
    val shouldReuseExistingFile: Boolean = true,
)

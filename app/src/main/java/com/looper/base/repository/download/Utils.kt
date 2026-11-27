package com.looper.base.repository.download

import android.content.Context
import android.net.Uri
import com.looper.base.repository.download.DownloadManagerImpl.Companion.SUPPORTED_EXTENSIONS
import com.looper.base.repository.download.DownloadManagerImpl.Companion.WALLPAPER_FOLDER
import java.io.File
import java.security.MessageDigest

fun Context.findDownloadedWallpaper(imageUrl: String?): Uri? {
    if (imageUrl.isNullOrBlank()) return null

    val file = File(getDirectory( WALLPAPER_FOLDER), imageFileName(imageUrl))

    return file.takeIf { it.isFile && it.length() > 0L }?.let(Uri::fromFile)
}

fun imageFileName(imageUrl: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(imageUrl.toByteArray())
        .joinToString("") { byte -> "%02x".format(byte) }
    val extension = imageUrl.substringBefore('?').substringAfterLast('.', "jpg").lowercase()
        .takeIf { it in SUPPORTED_EXTENSIONS } ?: "jpg"
    return "wallpaper_${digest}.$extension"
}

fun Context.getDirectory(folderName: String): File =
    File(this.filesDir, folderName).also { directory ->
        check(directory.exists() || directory.mkdirs()) { "Unable to create download directory" }
    }
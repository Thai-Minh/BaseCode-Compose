package com.looper.base.download.data

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.core.net.toUri
import com.looper.base.database.dao.DownloadedFileDao
import com.looper.base.database.dao.MediaItemDao
import com.looper.base.database.data.DownloadKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

class DownloadExporter(
    private val context: Context,
    private val mediaItemDao: MediaItemDao,
    private val downloadedFileDao: DownloadedFileDao,
) {

    private companion object {
        val INVALID_NAME_CHARS = Regex("[\\\\/:*?\"<>|\\p{Cntrl}]")
        const val MAX_NAME_LENGTH = 120
    }

    suspend fun export(mediaItemId: String, kind: DownloadKind): Uri = withContext(Dispatchers.IO) {
        val download = downloadedFileDao.get(mediaItemId = mediaItemId, kind = kind)
            ?: throw IOException("Not downloaded")
        val source = download.localUri.toUri().path?.let(block = ::File)?.takeIf {
            it.canRead()
        } ?: throw IOException("Downloaded file is missing")
        val item = mediaItemDao.get(id = mediaItemId)
        val baseName = listOfNotNull(item?.title, item?.artist)
            .joinToString(separator = " - ")
            .replace(regex = INVALID_NAME_CHARS, replacement = "_")
            .trim()
            .take(n = MAX_NAME_LENGTH)
            .ifEmpty {
                mediaItemId
            }
        val fileName = "$baseName.${source.extension}"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            exportToMediaStore(
                source = source,
                fileName = fileName,
                mimeType = download.mimeType,
                kind = kind
            )
        } else {
            exportLegacy(
                source = source,
                fileName = fileName,
                mimeType = download.mimeType,
                kind = kind
            )
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun exportToMediaStore(
        source: File,
        fileName: String,
        mimeType: String,
        kind: DownloadKind
    ): Uri {
        val (collection, directory) = when (kind) {
            DownloadKind.AUDIO -> MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) to Environment.DIRECTORY_MUSIC
            DownloadKind.VIDEO -> MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) to Environment.DIRECTORY_MOVIES
        }
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, "$directory/${folderName()}")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri =
            resolver.insert(collection, values) ?: throw IOException("Cannot create $fileName")
        try {
            resolver.openOutputStream(uri)?.use { output ->
                source.inputStream().use {
                    it.copyTo(out = output)
                }
            } ?: throw IOException("Cannot open $uri")
            resolver.update(
                uri,
                ContentValues().apply {
                    put(MediaStore.MediaColumns.IS_PENDING, 0)
                },
                null,
                null,
            )
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
        return uri
    }

    @Suppress("DEPRECATION")
    private fun exportLegacy(
        source: File,
        fileName: String,
        mimeType: String,
        kind: DownloadKind
    ): Uri {
        val root = Environment.getExternalStoragePublicDirectory(
            if (kind == DownloadKind.AUDIO) {
                Environment.DIRECTORY_MUSIC
            } else {
                Environment.DIRECTORY_MOVIES
            }
        )
        val directory = File(root, folderName()).apply {
            mkdirs()
        }
        val target = File(directory, fileName)
        source.copyTo(target = target, overwrite = true)
        MediaScannerConnection.scanFile(
            context,
            arrayOf(target.absolutePath),
            arrayOf(mimeType),
            null
        )
        return Uri.fromFile(target)
    }

    private fun folderName(): String =
        context.applicationInfo.loadLabel(context.packageManager).toString()
            .replace(regex = INVALID_NAME_CHARS, replacement = "")
            .trim()
            .ifEmpty {
                context.packageName
            }
}

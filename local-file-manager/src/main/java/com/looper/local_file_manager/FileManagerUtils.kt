package com.looper.local_file_manager

import android.Manifest.permission.READ_EXTERNAL_STORAGE
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.ContextCompat
import com.looper.local_file_manager.data.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File

val VIDEO_EXTERNAL_URI: Uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
val AUDIO_EXTERNAL_URI: Uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
val IMAGE_EXTERNAL_URI: Uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
val FILE_EXTERNAL_URI: Uri = MediaStore.Files.getContentUri("external")

private const val MEDIA_STORE_DATA: String = MediaStore.MediaColumns.DATA

private const val AUDIO_COLUMN_DATA: String = MediaStore.Audio.Media.DATA
private const val AUDIO_COLUMN_DISPLAY_NAME: String = MediaStore.Audio.Media.DISPLAY_NAME
private const val AUDIO_COLUMN_DURATION: String = MediaStore.Audio.Media.DURATION
private const val AUDIO_COLUMN_DATE_MODIFIED: String = MediaStore.Audio.Media.DATE_MODIFIED
private const val AUDIO_COLUMN_SIZE: String = MediaStore.Audio.Media.SIZE

private const val VIDEO_COLUMN_ID: String = MediaStore.Video.Media._ID
private const val VIDEO_COLUMN_DATA: String = MediaStore.Video.Media.DATA
private const val VIDEO_COLUMN_DISPLAY_NAME: String = MediaStore.Video.Media.DISPLAY_NAME
private const val VIDEO_COLUMN_DURATION: String = MediaStore.Video.Media.DURATION
private const val VIDEO_COLUMN_DATE_MODIFIED: String = MediaStore.Video.Media.DATE_MODIFIED
private const val VIDEO_COLUMN_SIZE: String = MediaStore.Video.Media.SIZE

private const val IMAGE_COLUMN_DATA: String = MediaStore.Images.Media.DATA
private const val IMAGE_COLUMN_DISPLAY_NAME: String = MediaStore.Images.Media.DISPLAY_NAME
private const val IMAGE_COLUMN_DATE_MODIFIED: String = MediaStore.Images.Media.DATE_MODIFIED
private const val IMAGE_COLUMN_SIZE: String = MediaStore.Images.Media.SIZE

suspend fun queryVideoMediaStore(
    context: Context,
): List<MediaItem>? = withContext(Dispatchers.IO) {

    if (!context.hasReadExternalStorage) return@withContext null

    val videoFiles = mutableListOf<MediaItem>()

    val cursor = context.contentResolver.query(
        VIDEO_EXTERNAL_URI, videoProjectionInfo(), null, null, null
    )

    try {
        cursor?.use {
            while (it.moveToNext() && isActive) {
                val videoFile = parseVideoFileInfo(cursor = it)
                if (videoFile != null) {
                    videoFiles.add(videoFile)
                }
            }
        }

        videoFiles
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

suspend fun queryAudioMediaStore(
    context: Context,
): List<MediaItem>? = withContext(Dispatchers.IO) {

    if (!context.hasReadExternalStorage) return@withContext null

    val audioFiles = mutableListOf<MediaItem>()

    val cursor = context.contentResolver.query(
        AUDIO_EXTERNAL_URI, audioProjectionInfo(), null, null, null
    )

    try {
        cursor?.use {
            while (it.moveToNext() && isActive) {
                val audioFile = parseAudioFileInfo(cursor = it)

                if (audioFile != null) {
                    audioFiles.add(audioFile)
                }
            }
        }

        audioFiles
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

suspend fun queryImageMediaStore(
    context: Context,
): List<MediaItem>? = withContext(Dispatchers.IO) {

    if (!context.hasReadExternalStorage) return@withContext null

    val imageFiles = mutableListOf<MediaItem>()

    val cursor = context.contentResolver.query(
        IMAGE_EXTERNAL_URI, imageProjectionInfo(), null, null, null
    )

    try {
        cursor?.use {
            while (it.moveToNext() && isActive) {
                val imageFile = parseImageFileInfo(cursor = it)

                if (imageFile != null) {
                    imageFiles.add(imageFile)
                }
            }
        }

        imageFiles
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

private fun videoProjectionInfo(): Array<String> {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        arrayOf(
            VIDEO_COLUMN_ID,
            VIDEO_COLUMN_DISPLAY_NAME,
            VIDEO_COLUMN_DURATION,
            VIDEO_COLUMN_DATE_MODIFIED,
            VIDEO_COLUMN_SIZE,
            VIDEO_COLUMN_DATA
        )
    } else {
        arrayOf(
            VIDEO_COLUMN_ID,
            VIDEO_COLUMN_DISPLAY_NAME,
            VIDEO_COLUMN_DURATION,
            VIDEO_COLUMN_DATE_MODIFIED,
            VIDEO_COLUMN_SIZE,
            MEDIA_STORE_DATA
        )
    }
}

private fun audioProjectionInfo(): Array<String> {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        arrayOf(
            AUDIO_COLUMN_DISPLAY_NAME,
            AUDIO_COLUMN_DURATION,
            AUDIO_COLUMN_DATE_MODIFIED,
            AUDIO_COLUMN_SIZE,
            AUDIO_COLUMN_DATA
        )
    } else {
        arrayOf(
            VIDEO_COLUMN_DISPLAY_NAME,
            VIDEO_COLUMN_DURATION,
            VIDEO_COLUMN_DATE_MODIFIED,
            VIDEO_COLUMN_SIZE,
            MEDIA_STORE_DATA,
        )
    }
}

private fun imageProjectionInfo(): Array<String> {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        arrayOf(
            IMAGE_COLUMN_DISPLAY_NAME,
            IMAGE_COLUMN_DATE_MODIFIED,
            IMAGE_COLUMN_SIZE,
            IMAGE_COLUMN_DATA
        )
    } else {
        arrayOf(
            IMAGE_COLUMN_DISPLAY_NAME,
            IMAGE_COLUMN_DATE_MODIFIED,
            IMAGE_COLUMN_SIZE,
            MEDIA_STORE_DATA,
        )
    }
}

private fun parseAudioFileInfo(cursor: Cursor): MediaItem? {
    return try {
        val displayName = cursor.getString(cursor.getColumnIndexOrThrow(AUDIO_COLUMN_DISPLAY_NAME))
        val duration = cursor.getLong(cursor.getColumnIndexOrThrow(AUDIO_COLUMN_DURATION))
        val dateModified = cursor.getLong(cursor.getColumnIndexOrThrow(AUDIO_COLUMN_DATE_MODIFIED))
        val size = cursor.getLong(cursor.getColumnIndexOrThrow(AUDIO_COLUMN_SIZE))

        val path = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            cursor.getString(cursor.getColumnIndexOrThrow(AUDIO_COLUMN_DATA))
        } else {
            cursor.getString(cursor.getColumnIndexOrThrow(MEDIA_STORE_DATA))
        }

        if (!File(path).exists()) return null

        MediaItem(
            path = path,
            name = displayName,
            size = size,
            lastModified = dateModified * 1000,
            duration = duration,
        )
    } catch (e: Exception) {
        Log.d("MTHAI", "parseAudioFileInfo Exception: $e")
        null
    }
}

private fun parseVideoFileInfo(cursor: Cursor): MediaItem? {
    return try {
        val id = cursor.getLong(cursor.getColumnIndexOrThrow(VIDEO_COLUMN_ID))
        val displayName = cursor.getString(cursor.getColumnIndexOrThrow(VIDEO_COLUMN_DISPLAY_NAME))
        val duration = cursor.getLong(cursor.getColumnIndexOrThrow(VIDEO_COLUMN_DURATION))
        val dateModified = cursor.getLong(cursor.getColumnIndexOrThrow(VIDEO_COLUMN_DATE_MODIFIED))
        val size = cursor.getLong(cursor.getColumnIndexOrThrow(VIDEO_COLUMN_SIZE))
        val contentUri = ContentUris.withAppendedId(VIDEO_EXTERNAL_URI, id).toString()

        val path = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            cursor.getString(cursor.getColumnIndexOrThrow(VIDEO_COLUMN_DATA))
        } else {
            cursor.getString(cursor.getColumnIndexOrThrow(MEDIA_STORE_DATA))
        }

        if (!File(path).exists()) return null

        MediaItem(
            name = displayName,
            path = path,
            size = size,
            lastModified = dateModified * 1000,
            duration = duration,
            contentUri = contentUri,
        )
    } catch (e: Exception) {
        Log.d("MTHAI", "parseVideoFileInfo Exception: $e")
        null
    }
}

private fun parseImageFileInfo(cursor: Cursor): MediaItem? {
    return try {
        val displayName = cursor.getString(cursor.getColumnIndexOrThrow(IMAGE_COLUMN_DISPLAY_NAME))
        val dateModified = cursor.getLong(cursor.getColumnIndexOrThrow(IMAGE_COLUMN_DATE_MODIFIED))
        val size = cursor.getLong(cursor.getColumnIndexOrThrow(IMAGE_COLUMN_SIZE))

        val path = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            cursor.getString(cursor.getColumnIndexOrThrow(IMAGE_COLUMN_DATA))
        } else {
            cursor.getString(cursor.getColumnIndexOrThrow(MEDIA_STORE_DATA))
        }

        if (!File(path).exists()) return null

        MediaItem(
            path = path,
            name = displayName,
            size = size,
            lastModified = dateModified * 1000,
            duration = null,
        )
    } catch (e: Exception) {
        Log.d("MTHAI", "parseImageFileInfo Exception: $e")
        null
    }
}

private val Context.hasReadExternalStorage: Boolean
    get() {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            true
        } else {
            ContextCompat.checkSelfPermission(
                applicationContext,
                READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }
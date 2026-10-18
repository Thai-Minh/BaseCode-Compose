package com.looper.local_file_manager

import android.app.Activity
import android.content.Context
import android.media.MediaMetadataRetriever
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.storage.StorageManager
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.arthenica.ffmpegkit.FFprobeKit
import com.looper.local_file_manager.data.FileType
import org.json.JSONObject
import java.io.File
import java.lang.reflect.Array
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

fun File.isSupportFile(type: FileType): Boolean {
    val supports = type.supportTypes

    return if (supports.isEmpty())
        true
    else
        supports.contains(getExtension(absolutePath))
}

fun getExtension(path: String): String {
    return if (path.contains("."))
        path.substring(path.lastIndexOf(".") + 1).lowercase(Locale.getDefault())
    else
        ""
}

suspend fun ComponentActivity.deletePath(path: String?): Boolean? {
    return if (path != null) {
        deleteFile(File(path))
    } else {
        null
    }
}

suspend fun ComponentActivity.deleteFile(file: File): Boolean {
    if (!file.exists())
        return true

    // Try the normal way
    val isSuccess = deleteUseMediaStore(file)

    if (!isSuccess && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        return false
    }

    return !file.exists() || file.delete()
}

private suspend fun ComponentActivity.deleteUseMediaStore(file: File): Boolean {
    val uri = fromFile(file)
    return if (uri != null) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            requestDeletePermission(uri)
        } else
            deleteContent(uri)
    } else {
        false
    }
}

private fun ComponentActivity.deleteContent(uri: Uri): Boolean {
    return try {
        val rowsDeleted = contentResolver.delete(uri, null, null)
        rowsDeleted > 0
    } catch (exception: SecurityException) {
        exception.printStackTrace()
        false
    }
}

private suspend fun ComponentActivity.requestDeletePermission(uri: Uri) =
    suspendCoroutine { continuation ->
        try {
            val registry = activityResultRegistry
            val sender = ActivityResultContracts.StartIntentSenderForResult()
            val request =
                registry.register(System.currentTimeMillis().toString(), sender) { result ->
                    continuation.resume(result.resultCode == Activity.RESULT_OK)
                }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val pi = MediaStore.createDeleteRequest(contentResolver, listOf(uri))
                request.launch(IntentSenderRequest.Builder(pi).build())
            }
        } catch (ex: Exception) {
            continuation.resume(false)
        }
    }

suspend fun Context.fromFile(file: File): Uri? = suspendCoroutine { continuation ->
    MediaScannerConnection.scanFile(this, arrayOf(file.path), null) { _, uri ->
        continuation.resume(uri)
    }
}

fun getStoragePath(mContext: Context): String? {
    try {
        val mStorageManager =
            mContext.getSystemService(Context.STORAGE_SERVICE) as StorageManager

        val storageVolumeClazz = Class.forName("android.os.storage.StorageVolume")
        val getVolumeList = mStorageManager.javaClass.getMethod("getVolumeList")

        val getPath = storageVolumeClazz.getMethod("getPath")
        val isRemovable = storageVolumeClazz.getMethod("isRemovable")
        val result = getVolumeList.invoke(mStorageManager) ?: return null
        val length = Array.getLength(result)

        for (i in 0 until length) {
            val storageVolumeElement = Array.get(result, i)
            val paths = getPath.invoke(storageVolumeElement) as String
            val removable = isRemovable.invoke(storageVolumeElement) as Boolean
            if (!removable) {
                return paths
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
        return null
    }

    return null
}

fun getDuration(context: Context, path: String): Long? {
    val duration = getDurationFromMediaMetadata(path = path)

    return if (duration != null) {
        duration
    } else {
        getDurationFromFFmpeg(context = context, path = path)
    }
}

private fun getDurationFromMediaMetadata(path: String): Long? {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(path)
        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
    } catch (e: Exception) {
        e.printStackTrace()
        null
    } finally {
        retriever.release()
    }
}

private fun getDurationFromFFmpeg(context: Context, path: String): Long? {
    val mediaInformation = FFprobeKit.executeWithArguments(
        context.defaultGetMediaInformationCommandArguments(
            path = path
        )
    )

    return mediaInformation.output.getDuration()
}

private fun Context.defaultGetMediaInformationCommandArguments(
    path: String
): kotlin.Array<String> {
    return arrayOf(
        "-v",
        "error",
        "-hide_banner",
        "-print_format",
        "json",
        "-show_streams",
        "-show_format",
        "-i",
        path
    )
}

private fun String.getDuration(): Long? {
    return try {
        val jsonObj = JSONObject(this)
        val dataObj = jsonObj.optJSONObject("format")
        val strDuration = dataObj?.optDouble("duration")
        ((strDuration ?: 0.0) * 1000L).toLong()
    } catch (_: Exception) {
        null
    }
}

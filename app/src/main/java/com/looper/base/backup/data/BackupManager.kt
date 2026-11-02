package com.looper.base.backup.data

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import com.looper.base.base.storage.DATA_STORE_NAME
import com.looper.base.database.AppDatabase
import com.looper.base.database.DATABASE_NAME
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

@Serializable
private data class BackupInfo(
    val formatVersion: Int,
    val databaseVersion: Int,
    val createdAt: Long,
)

class BackupManager(
    private val context: Context,
    private val database: AppDatabase,
) {

    private companion object {
        const val FORMAT_VERSION = 1
        const val ENTRY_INFO = "backup.json"
        const val ENTRY_DATABASE = "database.db"
        const val ENTRY_PREFERENCES = "preferences.preferences_pb"
        const val FILE_PREFIX = "backup_"
        const val FILE_EXTENSION = ".zip"
        const val MIME_TYPE = "application/zip"
        const val SQLITE_HEADER = "SQLite format 3"
    }

    private val json = Json {
        ignoreUnknownKeys = true
    }

    private val databaseFile: File
        get() = context.getDatabasePath(DATABASE_NAME)

    private val preferencesFile: File
        get() = File(context.filesDir, "datastore/$DATA_STORE_NAME.preferences_pb")

    suspend fun backupTo(uri: Uri) = withContext(Dispatchers.IO) {
        val output = context.contentResolver.openOutputStream(uri)
            ?: throw IOException("Cannot open $uri")
        output.use {
            backup(output = it)
        }
    }

    suspend fun restoreFrom(uri: Uri) = withContext(Dispatchers.IO) {
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IOException("Cannot open $uri")
        input.use {
            restore(input = it)
        }
    }

    suspend fun autoBackup(maxFiles: Int) = withContext(Dispatchers.IO) {
        val name = FILE_PREFIX + SimpleDateFormat(
            "yyyyMMdd_HHmmss",
            Locale.US
        ).format(Date()) + FILE_EXTENSION
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            autoBackupToDownloads(name = name, maxFiles = maxFiles)
        } else {
            val directory = legacyBackupDirectory().apply {
                mkdirs()
            }
            File(directory, name).outputStream().use {
                backup(output = it)
            }
            directory.listFiles()
                ?.filter {
                    it.name.startsWith(prefix = FILE_PREFIX) && it.name.endsWith(suffix = FILE_EXTENSION)
                }?.sortedByDescending {
                    it.lastModified()
                }?.drop(n = maxFiles)
                ?.forEach {
                    it.delete()
                }
        }
    }

    fun restartApp() {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        intent?.let {
            context.startActivity(it)
        }
        Runtime.getRuntime().exit(0)
    }

    private fun backup(output: OutputStream) {
        database.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use {
            it.moveToFirst()
        }
        ZipOutputStream(output.buffered()).use { zip ->
            val info = BackupInfo(
                formatVersion = FORMAT_VERSION,
                databaseVersion = database.openHelper.readableDatabase.version,
                createdAt = System.currentTimeMillis(),
            )
            zip.putNextEntry(ZipEntry(ENTRY_INFO))
            zip.write(json.encodeToString(value = info).toByteArray(charset = Charsets.UTF_8))
            zip.closeEntry()
            writeEntry(zip = zip, name = ENTRY_DATABASE, file = databaseFile)
            if (preferencesFile.exists()) {
                writeEntry(zip = zip, name = ENTRY_PREFERENCES, file = preferencesFile)
            }
        }
    }

    private fun restore(input: InputStream) {
        val staging = File(context.cacheDir, "restore").apply {
            deleteRecursively()
            mkdirs()
        }
        var info: BackupInfo? = null
        ZipInputStream(input.buffered()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                when (entry.name) {
                    ENTRY_INFO -> info = json.decodeFromString<BackupInfo>(
                        zip.readBytes().toString(charset = Charsets.UTF_8)
                    )

                    ENTRY_DATABASE, ENTRY_PREFERENCES -> File(staging, entry.name).outputStream()
                        .use {
                            zip.copyTo(out = it)
                        }
                }
                entry = zip.nextEntry
            }
        }
        val backupInfo = info ?: throw IOException("Not a backup file")
        if (backupInfo.formatVersion > FORMAT_VERSION) {
            throw IOException("Backup was made by a newer app version")
        }
        val stagedDatabase = File(staging, ENTRY_DATABASE)
        if (!stagedDatabase.exists() || !hasSqliteHeader(file = stagedDatabase)) {
            throw IOException("Backup has no valid database")
        }

        database.close()
        listOf("", "-wal", "-shm", "-journal").forEach {
            File(databaseFile.path + it).delete()
        }
        stagedDatabase.copyTo(target = databaseFile, overwrite = true)
        File(staging, ENTRY_PREFERENCES).takeIf {
            it.exists()
        }?.let {
            preferencesFile.parentFile?.mkdirs()
            it.copyTo(target = preferencesFile, overwrite = true)
        }
        staging.deleteRecursively()
    }

    private fun writeEntry(zip: ZipOutputStream, name: String, file: File) {
        zip.putNextEntry(ZipEntry(name))
        file.inputStream().use {
            it.copyTo(out = zip)
        }
        zip.closeEntry()
    }

    private fun hasSqliteHeader(file: File): Boolean {
        val header = ByteArray(size = SQLITE_HEADER.length)
        val read = file.inputStream().use {
            it.read(header)
        }
        return read == header.size && String(
            bytes = header,
            charset = Charsets.US_ASCII
        ) == SQLITE_HEADER
    }

    private fun folderName(): String =
        context.applicationInfo.loadLabel(context.packageManager).toString()
            .replace(regex = Regex(pattern = "[^A-Za-z0-9 _-]"), replacement = "")
            .trim()
            .ifEmpty {
                context.packageName
            }

    private fun legacyBackupDirectory(): File =
        File(context.getExternalFilesDir(null) ?: context.filesDir, "backups")

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun autoBackupToDownloads(name: String, maxFiles: Int) {
        val relativePath = "${Environment.DIRECTORY_DOWNLOADS}/${folderName()}/"
        val resolver = context.contentResolver
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, MIME_TYPE)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values) ?: throw IOException("Cannot create $name")
        try {
            resolver.openOutputStream(uri)?.use {
                backup(output = it)
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

        val stale = mutableListOf<Uri>()
        resolver.query(
            collection,
            arrayOf(MediaStore.MediaColumns._ID),
            "${MediaStore.MediaColumns.RELATIVE_PATH} = ? AND ${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?",
            arrayOf(relativePath, "$FILE_PREFIX%$FILE_EXTENSION"),
            "${MediaStore.MediaColumns.DATE_ADDED} DESC",
        )?.use { cursor ->
            var index = 0
            while (cursor.moveToNext()) {
                if (index >= maxFiles) {
                    stale += Uri.withAppendedPath(collection, cursor.getLong(0).toString())
                }
                index++
            }
        }
        stale.forEach {
            resolver.delete(it, null, null)
        }
    }
}

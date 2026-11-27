package com.looper.base.repository.download

import android.content.Context
import android.net.Uri
import com.looper.base.repository.download.data.DownloadWallpaperInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.time.Duration.Companion.milliseconds

class DownloadManagerImpl(
    private val context: Context,
) : IDownloadManager {
    private val semaphore = Semaphore(DEFAULT_MAX_CONCURRENT)

    override suspend fun downloadWallpapers(infos: List<DownloadWallpaperInfo>): List<Uri?> =
        withContext(Dispatchers.IO) {
            coroutineScope {
                infos.map { info ->
                    async {
                        semaphore.withPermit {
                            downloadSingle(info)
                        }
                    }
                }.awaitAll()
            }
        }

    override suspend fun downloadWallpaper(info: DownloadWallpaperInfo): Result<Uri> =
        downloadWallpaper(info.url)

    override suspend fun downloadWallpaper(imageUrl: String): Result<Uri> {
        if (imageUrl.isBlank()) {
            return Result.failure(
                IllegalArgumentException("Download url is blank"),
            )
        }

        findDownloadedWallpaper(imageUrl)?.let { uri ->
            return Result.success(uri)
        }

        return withContext(Dispatchers.IO) {
            runCatching {
                semaphore.withPermit {
                    findDownloadedWallpaper(imageUrl)
                        ?: downloadToFile(
                            DownloadWallpaperInfo(
                                fileName = imageFileName(imageUrl),
                                url = imageUrl,
                                folderName = WALLPAPER_FOLDER,
                                shouldReuseExistingFile = true,
                            ),
                        )
                }
            }.onFailure { exception ->
                if (exception is CancellationException) {
                    throw exception
                }
            }
        }
    }

    override fun findDownloadedWallpaper(imageUrl: String): Uri? {
        if (imageUrl.isBlank()) return null

        val file = File(context.getDirectory( WALLPAPER_FOLDER), imageFileName(imageUrl))

        return file.takeIf { it.isFile && it.length() > 0L }?.let(Uri::fromFile)
    }

    private suspend fun downloadSingle(info: DownloadWallpaperInfo): Uri? = runCatching {
        downloadToFile(info)
    }.onFailure { exception ->
        if (exception is CancellationException) throw exception
    }.getOrNull()

    private suspend fun downloadToFile(info: DownloadWallpaperInfo): Uri {
        require(info.url.isNotBlank()) { "Download url is blank" }
        val directory = context.getDirectory(info.folderName)
        val outputFile = File(directory, info.fileName)
        if (info.shouldReuseExistingFile && outputFile.isFile && outputFile.length() > 0L) {
            return Uri.fromFile(outputFile)
        }

        if (outputFile.exists()) outputFile.delete()
        val tempFile = File(directory, "${info.fileName}.part")
        if (tempFile.exists()) tempFile.delete()

        try {
            withTimeout(DOWNLOAD_TIMEOUT_MS.milliseconds) {
                val connection = URL(info.url).openConnection() as HttpURLConnection
                try {
                    connection.connectTimeout = CONNECT_TIMEOUT_MS
                    connection.readTimeout = READ_TIMEOUT_MS
                    connection.instanceFollowRedirects = true
                    connection.connect()
                    if (connection.responseCode !in 200..299) {
                        throw IllegalStateException("Server returned HTTP ${connection.responseCode}")
                    }
                    tempFile.outputStream().buffered().use { output ->
                        connection.inputStream.buffered().use { input -> input.copyTo(output) }
                    }
                } finally {
                    connection.disconnect()
                }
            }

            check(tempFile.length() > 0L) { "Downloaded file is empty" }
            if (!tempFile.renameTo(outputFile)) {
                tempFile.copyTo(outputFile, overwrite = true)
                tempFile.delete()
            }
            check(outputFile.length() > 0L) { "Unable to finalize downloaded file" }
            return Uri.fromFile(outputFile)
        } catch (exception: Exception) {
            tempFile.delete()
            outputFile.delete()
            throw exception
        }
    }

    companion object {
        private const val DEFAULT_MAX_CONCURRENT = 3
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 30_000
        private const val DOWNLOAD_TIMEOUT_MS = 60_000L
        const val WALLPAPER_FOLDER = "wallpapers"
        val SUPPORTED_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp")
    }
}

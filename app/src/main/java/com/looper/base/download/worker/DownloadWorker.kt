package com.looper.base.download.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.looper.base.R
import com.looper.base.database.AppDatabase
import com.looper.base.database.data.DownloadJobEntity
import com.looper.base.database.data.DownloadKind
import com.looper.base.database.data.DownloadStatus
import com.looper.base.database.data.DownloadedFileEntity
import io.ytdlp.android.YtDlpAndroid
import io.ytdlp.core.DownloadEvent
import io.ytdlp.core.DownloadOptions
import kotlinx.coroutines.CancellationException
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File

class DownloadWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params), KoinComponent {

    private companion object {
        const val CHANNEL_ID = "downloads"
        const val NOTIFICATION_ID = 4_201
        const val PROGRESS_WRITE_INTERVAL_MS = 750L
        const val DOWNLOAD_DIRECTORY = "downloads"
        const val TEMP_DIRECTORY = "ytdlp-tmp"
    }

    private class UserCanceledException : CancellationException("Download canceled")

    private val database: AppDatabase by inject()
    private val jobDao by lazy {
        database.downloadJobDao()
    }
    private val fileDao by lazy {
        database.downloadedFileDao()
    }
    private val mediaItemDao by lazy {
        database.mediaItemDao()
    }

    override suspend fun doWork(): Result {
        runCatching {
            setForeground(foregroundInfo(title = null, progress = 0))
        }
        while (!isStopped) {
            val job = jobDao.nextQueued() ?: break
            process(job = job)
        }
        return Result.success()
    }

    override suspend fun getForegroundInfo(): ForegroundInfo =
        foregroundInfo(title = null, progress = 0)

    private suspend fun process(job: DownloadJobEntity) {
        jobDao.updateStatus(id = job.id, status = DownloadStatus.RUNNING)
        val item = mediaItemDao.get(id = job.mediaItemId)
        if (item == null) {
            jobDao.updateStatus(
                id = job.id,
                status = DownloadStatus.FAILED,
                error = "Media item not found"
            )
            return
        }
        val baseDirectory =
            applicationContext.getExternalFilesDir(null) ?: applicationContext.filesDir
        val directory = File(baseDirectory, "$DOWNLOAD_DIRECTORY/${item.id}").apply {
            mkdirs()
        }
        val tempDirectory = File(applicationContext.cacheDir, "$TEMP_DIRECTORY/${job.id}").apply {
            mkdirs()
        }
        jobDao.updateTempDirectory(id = job.id, tempDirectory = tempDirectory.absolutePath)
        val prefix = job.targetKind.name.lowercase()
        val options = DownloadOptions(
            directory = directory,
            outputTemplate = "$prefix.%(ext)s",
            tempDirectory = tempDirectory,
            format = job.formatSelector,
            cookiesFile = job.cookieFilePath?.let(block = ::File),
            extra = {
                job.userAgent?.let(block = ::userAgent)
                job.referer?.let(block = ::referer)
                if (job.targetKind == DownloadKind.VIDEO) {
                    option(flag = "--merge-output-format", value = "mp4")
                }
            },
        )
        val outputFiles = mutableListOf<File>()
        var lastWrite = 0L
        try {
            YtDlpAndroid.init(applicationContext)
                .download(url = item.sourceUrl, options = options)
                .collect { event ->
                    when (event) {
                        is DownloadEvent.Progress -> {
                            val now = System.currentTimeMillis()
                            if (now - lastWrite >= PROGRESS_WRITE_INTERVAL_MS) {
                                lastWrite = now
                                if (jobDao.get(id = job.id)?.status == DownloadStatus.CANCELED) {
                                    throw UserCanceledException()
                                }
                                val progress = event.progress
                                val percent = progress.percentBest?.toInt() ?: 0
                                jobDao.updateProgress(
                                    id = job.id,
                                    status = DownloadStatus.RUNNING,
                                    progress = percent,
                                    etaSeconds = progress.eta?.toLong() ?: -1L,
                                    downloadedBytes = progress.downloadedBytes ?: 0L,
                                    totalBytes = progress.totalBytesBest ?: -1L,
                                )
                                runCatching {
                                    setForeground(
                                        foregroundInfo(
                                            title = item.title,
                                            progress = percent
                                        )
                                    )
                                }
                            }
                        }

                        is DownloadEvent.Finished -> outputFiles += event.files
                        else -> Unit
                    }
                }
        } catch (e: UserCanceledException) {
            directory.listFiles()
                ?.filter {
                    it.name.startsWith(prefix = "$prefix.")
                }?.forEach {
                    it.delete()
                }
            tempDirectory.deleteRecursively()
            return
        } catch (e: CancellationException) {
            jobDao.updateStatus(id = job.id, status = DownloadStatus.QUEUED)
            throw e
        } catch (e: Exception) {
            tempDirectory.deleteRecursively()
            jobDao.updateStatus(
                id = job.id,
                status = DownloadStatus.FAILED,
                error = e.message ?: e.javaClass.simpleName
            )
            return
        }
        tempDirectory.deleteRecursively()

        val output = outputFiles.lastOrNull {
            it.exists()
        } ?: directory.listFiles()
            ?.filter {
                it.name.startsWith(prefix = "$prefix.")
            }?.maxByOrNull {
                it.lastModified()
            }
        if (output == null) {
            jobDao.updateStatus(
                id = job.id,
                status = DownloadStatus.FAILED,
                error = "No output file"
            )
            return
        }
        val existing = fileDao.get(mediaItemId = item.id, kind = job.targetKind)
        existing?.localUri?.toUri()?.path
            ?.takeIf {
                it != output.absolutePath
            }?.let {
                File(it).delete()
            }
        fileDao.upsert(
            file = DownloadedFileEntity(
                id = existing?.id ?: job.id,
                mediaItemId = item.id,
                kind = job.targetKind,
                qualityLabel = job.qualityLabel,
                formatId = job.formatSelector,
                localUri = output.toUri().toString(),
                mimeType = mimeType(extension = output.extension, kind = job.targetKind),
                size = output.length(),
                origin = job.origin,
            )
        )
        jobDao.delete(id = job.id)
    }

    private fun mimeType(extension: String, kind: DownloadKind): String =
        when (extension.lowercase()) {
            "m4a", "aac" -> "audio/mp4"
            "mp3" -> "audio/mpeg"
            "opus", "ogg" -> "audio/ogg"
            "flac" -> "audio/flac"
            "webm" -> if (kind == DownloadKind.AUDIO) {
                "audio/webm"
            } else {
                "video/webm"
            }

            "mkv" -> "video/x-matroska"
            else -> if (kind == DownloadKind.AUDIO) {
                "audio/mp4"
            } else {
                "video/mp4"
            }
        }

    private fun foregroundInfo(title: String?, progress: Int): ForegroundInfo {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && manager.getNotificationChannel(
                CHANNEL_ID
            ) == null
        ) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    applicationContext.getString(R.string.download_channel_name),
                    NotificationManager.IMPORTANCE_LOW,
                )
            )
        }
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title ?: applicationContext.getString(R.string.download_in_progress))
            .setProgress(100, progress, progress <= 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }
}

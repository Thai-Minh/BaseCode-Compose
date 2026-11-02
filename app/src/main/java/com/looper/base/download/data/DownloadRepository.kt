package com.looper.base.download.data

import android.content.Context
import androidx.core.net.toUri
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.looper.base.base.storage.downloadSettings
import com.looper.base.database.dao.DownloadJobDao
import com.looper.base.database.dao.DownloadedFileDao
import com.looper.base.database.dao.MediaItemDao
import com.looper.base.database.data.DownloadJobEntity
import com.looper.base.database.data.DownloadKind
import com.looper.base.database.data.DownloadOrigin
import com.looper.base.database.data.DownloadStatus
import com.looper.base.database.data.DownloadedFileEntity
import com.looper.base.download.model.AudioDownloadFormat
import com.looper.base.download.worker.DownloadWorker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.io.File
import java.util.UUID

class DownloadRepository(
    private val context: Context,
    private val jobDao: DownloadJobDao,
    private val fileDao: DownloadedFileDao,
    private val mediaItemDao: MediaItemDao,
) {

    companion object {
        const val WORK_NAME = "downloads"
    }

    val jobs: Flow<List<DownloadJobEntity>> = jobDao.observeAll()

    val files: Flow<List<DownloadedFileEntity>> = fileDao.observeAll()

    suspend fun enqueue(
        mediaItemId: String,
        kind: DownloadKind,
        origin: DownloadOrigin = DownloadOrigin.USER,
    ): DownloadJobEntity? {
        if (fileDao.get(mediaItemId = mediaItemId, kind = kind) != null) {
            return null
        }
        activeJob(mediaItemId = mediaItemId, kind = kind)?.let {
            return it
        }
        if (mediaItemDao.get(id = mediaItemId)?.isLive == true) {
            return null
        }
        val settings = downloadSettings.first()
        val maxHeight = settings.videoMaxHeight
        val job = DownloadJobEntity(
            id = UUID.randomUUID().toString(),
            mediaItemId = mediaItemId,
            processId = UUID.randomUUID().toString(),
            targetKind = kind,
            qualityLabel = when (kind) {
                DownloadKind.AUDIO -> settings.audioFormat.name.lowercase()
                DownloadKind.VIDEO -> "${maxHeight}p"
            },
            formatSelector = when (kind) {
                DownloadKind.AUDIO -> when (settings.audioFormat) {
                    AudioDownloadFormat.M4A -> "bestaudio[ext=m4a]/bestaudio/best"
                    AudioDownloadFormat.OPUS -> "bestaudio[acodec=opus]/bestaudio/best"
                    AudioDownloadFormat.BEST -> "bestaudio/best"
                }

                DownloadKind.VIDEO -> "bestvideo[height<=$maxHeight][vcodec!^=av01]+bestaudio[ext=m4a]/best[height<=$maxHeight]/best"
            },
            origin = origin,
        )
        jobDao.upsert(job = job)
        scheduleWorker()
        return job
    }

    suspend fun enqueueIfNeverAttempted(
        mediaItemId: String,
        kind: DownloadKind,
        origin: DownloadOrigin = DownloadOrigin.USER,
    ): DownloadJobEntity? {
        val attempted = jobDao.observeForItem(mediaItemId = mediaItemId).first().any {
            it.targetKind == kind
        }
        return if (attempted) {
            null
        } else {
            enqueue(
                mediaItemId = mediaItemId,
                kind = kind,
                origin = origin
            )
        }
    }

    suspend fun cancel(jobId: String) {
        val job = jobDao.get(id = jobId) ?: return
        if (job.status == DownloadStatus.QUEUED || job.status == DownloadStatus.RUNNING) {
            jobDao.updateStatus(id = jobId, status = DownloadStatus.CANCELED)
        }
    }

    suspend fun retry(jobId: String) {
        val job = jobDao.get(id = jobId) ?: return
        if (job.status == DownloadStatus.FAILED || job.status == DownloadStatus.CANCELED) {
            jobDao.updateStatus(id = jobId, status = DownloadStatus.QUEUED)
            scheduleWorker()
        }
    }

    suspend fun deleteDownload(mediaItemId: String, kind: DownloadKind) {
        val file = fileDao.get(mediaItemId = mediaItemId, kind = kind) ?: return
        deleteLocalFile(localUri = file.localUri)
        fileDao.delete(mediaItemId = mediaItemId, kind = kind)
    }

    suspend fun resumeInterrupted() {
        val interrupted = jobDao.getInterrupted()
        interrupted
            .filter {
                it.status == DownloadStatus.RUNNING
            }.forEach {
                jobDao.updateStatus(id = it.id, status = DownloadStatus.QUEUED)
            }
        if (interrupted.isNotEmpty()) {
            scheduleWorker()
        }
    }

    suspend fun activeJob(mediaItemId: String, kind: DownloadKind): DownloadJobEntity? =
        jobDao.getInterrupted().firstOrNull {
            it.mediaItemId == mediaItemId && it.targetKind == kind
        }

    suspend fun scheduleWorker() {
        val unmeteredOnly = downloadSettings.first().unmeteredOnly
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(
                        if (unmeteredOnly) {
                            NetworkType.UNMETERED
                        } else {
                            NetworkType.CONNECTED
                        }
                    ).build()
            ).build()
        WorkManager.getInstance(context = context)
            .enqueueUniqueWork(
                uniqueWorkName = WORK_NAME,
                existingWorkPolicy = ExistingWorkPolicy.APPEND_OR_REPLACE,
                request = request
            )
    }

    private fun deleteLocalFile(localUri: String) {
        val path = localUri.toUri().path ?: return
        val file = File(path)
        file.delete()
        file.parentFile?.takeIf {
            it.listFiles().isNullOrEmpty()
        }?.delete()
    }
}

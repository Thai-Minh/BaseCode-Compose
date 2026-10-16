package com.looper.videodownloader.repository.youtubedl

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import com.looper.videodownloader.database.dao.YoutubeDLDownloadDao
import com.looper.videodownloader.database.data.YoutubeDLDownloadEntity
import com.looper.videodownloader.repository.youtubedl.data.YoutubeDLDownloadItem
import com.looper.videodownloader.repository.youtubedl.data.YoutubeDLDownloadStatus
import com.looper.videodownloader.repository.youtubedl.data.YoutubeDLFormatOption
import com.looper.videodownloader.repository.youtubedl.data.YoutubeDLResolvedAudio
import com.looper.videodownloader.repository.youtubedl.data.YoutubeDLResolvedVideo
import com.looper.videodownloader.repository.youtubedl.data.toModel
import com.looper.videodownloader.service.YoutubeDLDownloadService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

interface YoutubeDLDownloadRepository {
    val items: Flow<List<YoutubeDLDownloadItem>>
    fun getItem(id: String): Flow<YoutubeDLDownloadItem?>
    suspend fun enqueue(
        video: YoutubeDLResolvedVideo,
        option: YoutubeDLFormatOption,
        outputName: String
    ): String

    suspend fun enqueue(audio: YoutubeDLResolvedAudio, outputName: String): String

    suspend fun cancel(id: String)
    suspend fun delete(id: String)
    suspend fun removeDatabaseRecordByUri(uri: String?)
    suspend fun reconcileInterrupted()
}

class YoutubeDLDownloadRepositoryImpl(
    context: Context,
    private val dao: YoutubeDLDownloadDao
) : YoutubeDLDownloadRepository {
    private val appContext = context.applicationContext

    override val items: Flow<List<YoutubeDLDownloadItem>> =
        dao.youtubeDLDownloadEntities().map { list ->
            list.map(YoutubeDLDownloadEntity::toModel)
        }

    override fun getItem(id: String): Flow<YoutubeDLDownloadItem?> =
        dao.youtubeDLDownloadEntity(id).map {
            it?.toModel()
        }

    override suspend fun enqueue(
        video: YoutubeDLResolvedVideo,
        option: YoutubeDLFormatOption,
        outputName: String
    ): String = enqueue(
        sourceUrl = video.sourceUrl,
        title = video.title,
        ownerName = video.ownerName,
        thumbnailUrl = video.thumbnailUrl,
        durationMs = video.durationMs,
        extractor = video.extractor,
        formatSelector = option.selector,
        outputName = outputName,
        suggestedExtension = option.suggestedExtension,
        cookieFilePath = video.cookieFilePath,
        userAgent = video.userAgent,
        referer = video.referer,
    )

    override suspend fun enqueue(audio: YoutubeDLResolvedAudio, outputName: String): String = enqueue(
        sourceUrl = audio.sourceUrl,
        title = audio.title,
        ownerName = audio.ownerName,
        thumbnailUrl = audio.thumbnailUrl,
        durationMs = audio.durationMs,
        extractor = audio.extractor,
        formatSelector = audio.formatSelector,
        outputName = YoutubeDLOutputNaming.finalAudioName(
            YoutubeDLOutputNaming.requestedName(outputName, audio.title)
        ),
        suggestedExtension = "mp3",
        cookieFilePath = audio.cookieFilePath,
        userAgent = audio.userAgent,
        referer = audio.referer,
        downloadInfoJson = audio.downloadInfoJson,
    )

    private suspend fun enqueue(
        sourceUrl: String,
        title: String,
        ownerName: String?,
        thumbnailUrl: String?,
        durationMs: Long,
        extractor: String,
        formatSelector: String,
        outputName: String,
        suggestedExtension: String,
        cookieFilePath: String?,
        userAgent: String?,
        referer: String?,
        downloadInfoJson: String? = null,
    ): String {
        val id = UUID.randomUUID().toString()

        val processId = "$BASE_YOUTUBE_DL_PROCESS$id"

        val now = System.currentTimeMillis()

        val requested = YoutubeDLOutputNaming.requestedName(outputName, title)

        val temp = File(appContext.cacheDir, "$YOUTUBE_DL_FOLDER$id").absolutePath

        try {
            if (downloadInfoJson != null) {
                withContext(Dispatchers.IO) {
                    val directory = File(temp)
                    check(directory.mkdirs() || directory.isDirectory) {
                        "Unable to create download directory"
                    }
                    val staging = File(directory, "$YOUTUBE_DL_RESOLVED_INFO_NAME.tmp")
                    staging.writeText(downloadInfoJson)
                    check(staging.renameTo(File(directory, YOUTUBE_DL_RESOLVED_INFO_NAME))) {
                        "Unable to save resolved download information"
                    }
                }
            }
            dao.upsert(
                YoutubeDLDownloadEntity(
                    id = id,
                    processId = processId,
                    sourceUrl = sourceUrl,
                    title = title,
                    ownerName = ownerName,
                    thumbnailUrl = thumbnailUrl,
                    durationMs = durationMs,
                    extractor = extractor,
                    formatSelector = formatSelector,
                    requestedName = requested,
                    suggestedExtension = suggestedExtension,
                    cookieFilePath = cookieFilePath,
                    userAgent = userAgent,
                    referer = referer,
                    tempDirectory = temp,
                    finalUri = null,
                    finalDisplayName = null,
                    mimeType = null,
                    status = YoutubeDLDownloadStatus.QUEUED.name,
                    progress = 0,
                    etaSeconds = 0,
                    size = 0,
                    createdAt = now,
                    updatedAt = now,
                    error = null
                )
            )

            YoutubeDLDownloadService.start(appContext, id)

            return id
        } catch (error: Throwable) {
            withContext(NonCancellable + Dispatchers.IO) {
                runCatching { dao.delete(id) }
                File(temp).deleteRecursively()
                cookieFilePath.deleteCookieFile()
            }
            throw error
        }
    }

    override suspend fun cancel(id: String) {
        val service = Intent(appContext, YoutubeDLDownloadService::class.java)
            .setAction(YoutubeDLDownloadService.ACTION_CANCEL)
            .putExtra(YoutubeDLDownloadService.EXTRA_ID, id)

        appContext.startService(service)
    }

    override suspend fun delete(id: String) {
        val item = dao.get(id) ?: return

        item.finalUri?.let {
            runCatching {
                appContext.contentResolver.delete(
                    it.toUri(),
                    null,
                    null
                )
            }
        }

        item.tempDirectory?.let {
            File(it).deleteRecursively()
        }

        item.cookieFilePath.deleteCookieFile()

        dao.delete(id)
    }

    override suspend fun removeDatabaseRecordByUri(uri: String?) {
        if (uri == null) return

        val normalizedUri = runCatching {
            uri.toUri().normalizeScheme().toString()
        }.getOrNull() ?: return

        dao.deleteByFinalUri(normalizedUri)
    }

    override suspend fun reconcileInterrupted() {
        val now = System.currentTimeMillis()

        val interrupted = dao.getInterrupted()

        interrupted.forEach {
            dao.updateState(
                id = it.id,
                status = YoutubeDLDownloadStatus.INTERRUPTED.name,
                progress = it.progress,
                eta = 0,
                updatedAt = now,
                error = "Download was interrupted"
            )

            it.tempDirectory?.let { path ->
                File(path).deleteRecursively()
            }

            it.cookieFilePath.deleteCookieFile()
        }
    }

    private fun String?.deleteCookieFile() {
        this?.takeIf(predicate = String::isNotBlank)?.let { path ->
            runCatching {
                File(path).delete()
            }
        }
    }
}

package com.looper.base.download.data

import com.looper.base.base.storage.downloadSettings
import com.looper.base.database.dao.DownloadedFileDao
import com.looper.base.database.dao.PlaybackEventDao
import com.looper.base.database.data.DownloadKind
import com.looper.base.database.data.DownloadOrigin
import com.looper.base.database.data.PlaybackEventEntity
import kotlinx.coroutines.flow.first
import kotlin.math.pow

class SmartDownloadsManager(
    private val playbackEventDao: PlaybackEventDao,
    private val fileDao: DownloadedFileDao,
    private val downloadRepository: DownloadRepository,
) {

    private companion object {
        const val WINDOW_DAYS = 30L
        const val HALF_LIFE_DAYS = 14.0
        const val DAY_MS = 24L * 60L * 60L * 1000L
    }

    suspend fun sync() {
        val settings = downloadSettings.first()
        val smartFiles = fileDao.getByOrigin(origin = DownloadOrigin.SMART)
        if (!settings.smartDownloadsEnabled) {
            smartFiles.forEach {
                downloadRepository.deleteDownload(mediaItemId = it.mediaItemId, kind = it.kind)
            }
            return
        }
        val now = System.currentTimeMillis()
        val top = rank(
            events = playbackEventDao.getSince(from = now - WINDOW_DAYS * DAY_MS),
            now = now,
        ).take(n = settings.smartDownloadsCount).toSet()

        top.forEach {
            downloadRepository.enqueue(
                mediaItemId = it,
                kind = DownloadKind.AUDIO,
                origin = DownloadOrigin.SMART
            )
        }
        smartFiles
            .filter {
                it.mediaItemId !in top
            }
            .forEach {
                downloadRepository.deleteDownload(mediaItemId = it.mediaItemId, kind = it.kind)
            }
    }

    private fun rank(events: List<PlaybackEventEntity>, now: Long): List<String> =
        events
            .groupBy {
                it.mediaItemId
            }
            .map { (mediaItemId, plays) ->
                val score = plays.sumOf {
                    val fraction = if (it.durationMs > 0L) {
                        it.listenedMs.toDouble() / it.durationMs
                    } else {
                        0.0
                    }
                    val ageDays = (now - it.startedAt).toDouble() / DAY_MS
                    fraction * 0.5.pow(x = ageDays / HALF_LIFE_DAYS)
                }
                Triple(mediaItemId, score, plays.maxOf {
                    it.startedAt
                })
            }
            .sortedWith(
                comparator = compareByDescending<Triple<String, Double, Long>> {
                    it.second
                }.thenByDescending {
                    it.third
                }
            )
            .map {
                it.first
            }
}

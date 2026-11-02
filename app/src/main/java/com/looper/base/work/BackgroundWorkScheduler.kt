package com.looper.base.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.looper.base.backup.worker.AutoBackupWorker
import com.looper.base.base.storage.backupSettings
import com.looper.base.base.storage.downloadSettings
import com.looper.base.database.dao.MediaItemDao
import com.looper.base.database.data.DownloadKind
import com.looper.base.download.data.DownloadRepository
import com.looper.base.download.data.SmartDownloadsManager
import com.looper.base.download.worker.SmartDownloadsWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
class BackgroundWorkScheduler(
    private val context: Context,
    private val downloadRepository: DownloadRepository,
    private val smartDownloadsManager: SmartDownloadsManager,
    private val mediaItemDao: MediaItemDao,
) {

    private companion object {
        const val SMART_DOWNLOADS_WORK = "smart_downloads"
        const val AUTO_BACKUP_WORK = "auto_backup"
    }

    fun start(scope: CoroutineScope) {
        val workManager = WorkManager.getInstance(context)
        scope.launch {
            runCatching {
                downloadRepository.resumeInterrupted()
            }
        }
        scope.launch {
            downloadSettings
                .map {
                    it.smartDownloadsEnabled to it.unmeteredOnly
                }
                .distinctUntilChanged()
                .collect { (enabled, unmeteredOnly) ->
                    if (enabled) {
                        workManager.enqueueUniquePeriodicWork(
                            uniqueWorkName = SMART_DOWNLOADS_WORK,
                            existingPeriodicWorkPolicy = ExistingPeriodicWorkPolicy.UPDATE,
                            request = PeriodicWorkRequestBuilder<SmartDownloadsWorker>(
                                repeatInterval = 1,
                                repeatIntervalTimeUnit = TimeUnit.DAYS
                            ).setConstraints(
                                Constraints.Builder()
                                    .setRequiredNetworkType(
                                        if (unmeteredOnly) {
                                            NetworkType.UNMETERED
                                        } else {
                                            NetworkType.CONNECTED
                                        }
                                    )
                                    .setRequiresBatteryNotLow(requiresBatteryNotLow = true)
                                    .build()
                            ).build(),
                        )
                    } else {
                        workManager.cancelUniqueWork(uniqueWorkName = SMART_DOWNLOADS_WORK)
                        runCatching {
                            smartDownloadsManager.sync()
                        }
                    }
                }
        }
        scope.launch {
            downloadSettings
                .map {
                    it.autoDownloadFavorites
                }
                .distinctUntilChanged()
                .flatMapLatest { enabled ->
                    if (enabled) {
                        mediaItemDao.observeFavorites()
                    } else {
                        flowOf(value = emptyList())
                    }
                }
                .collect { favorites ->
                    favorites.forEach {
                        runCatching {
                            downloadRepository.enqueueIfNeverAttempted(
                                mediaItemId = it.id,
                                kind = DownloadKind.AUDIO
                            )
                        }
                    }
                }
        }
        scope.launch {
            backupSettings
                .map {
                    it.autoBackupEnabled to it.frequency
                }
                .distinctUntilChanged()
                .collect { (enabled, frequency) ->
                    if (enabled) {
                        workManager.enqueueUniquePeriodicWork(
                            uniqueWorkName = AUTO_BACKUP_WORK,
                            existingPeriodicWorkPolicy = ExistingPeriodicWorkPolicy.UPDATE,
                            request = PeriodicWorkRequestBuilder<AutoBackupWorker>(
                                repeatInterval = frequency.intervalDays,
                                repeatIntervalTimeUnit = TimeUnit.DAYS
                            )
                                .setConstraints(
                                    Constraints.Builder()
                                        .setRequiresBatteryNotLow(requiresBatteryNotLow = true)
                                        .build()
                                ).build(),
                        )
                    } else {
                        workManager.cancelUniqueWork(uniqueWorkName = AUTO_BACKUP_WORK)
                    }
                }
        }
    }
}

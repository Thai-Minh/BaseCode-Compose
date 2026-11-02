package com.looper.base.backup.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.looper.base.backup.data.BackupManager
import com.looper.base.base.storage.backupSettings
import kotlinx.coroutines.flow.first
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class AutoBackupWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext = context, params = params), KoinComponent {

    private val backupManager: BackupManager by inject()

    override suspend fun doWork(): Result {
        val settings = backupSettings.first()
        if (!settings.autoBackupEnabled) {
            return Result.success()
        }
        return runCatching {
            backupManager.autoBackup(maxFiles = settings.maxFiles)
        }.fold(
            onSuccess = {
                Result.success()
            },
            onFailure = {
                Result.retry()
            },
        )
    }
}

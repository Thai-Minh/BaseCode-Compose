package com.looper.base.download.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.looper.base.download.data.SmartDownloadsManager
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class SmartDownloadsWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext = context, params = params), KoinComponent {

    private val manager: SmartDownloadsManager by inject()

    override suspend fun doWork(): Result =
        runCatching {
            manager.sync()
        }.fold(
            onSuccess = {
                Result.success()
            },
            onFailure = {
                Result.retry()
            },
        )
}

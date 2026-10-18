package com.looper.base.repository.download

import androidx.activity.ComponentActivity
import com.example.all.downloader.manager.IDownloaderManager
import com.looper.base.repository.download.data.DownloadItem
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.Flow

open class IDownloadFileRepository(
    val downloader: IDownloaderManager
) {
    val scope = MainScope()

    open val items: Flow<List<DownloadItem>>
        get() {
            TODO()
        }

    open fun delete(activity: ComponentActivity, id: String) {

    }

    open fun delete(activity: ComponentActivity, ids: List<String>) {

    }
}
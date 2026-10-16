package com.looper.videodownloader.repository.download.downloading

import androidx.activity.ComponentActivity
import com.example.all.downloader.manager.IDownloaderManager
import com.example.all.downloader.manager.data.DownloadStatus
import com.looper.base.repository.download.IDownloadFileRepository
import com.looper.videodownloader.repository.download.data.DownloadItem
import com.looper.videodownloader.repository.download.deleteFileNormal
import com.looper.videodownloader.repository.download.updateDownloadDB
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DownloadingRepositoryImpl(
    downloader: IDownloaderManager,
) : IDownloadFileRepository(downloader) {

    private val _items = downloader.downloadInfoItems.map { downloadInfoItems ->
        downloadInfoItems.mapNotNull {
            if (it.status != DownloadStatus.COMPLETED)
                DownloadItem.DownloadingItem(it)
            else
                null
        }
    }

    override val items: Flow<List<DownloadItem>> = _items

    override fun delete(activity: ComponentActivity, ids: List<String>) {
        deleteFileNormal(activity = activity, downloader = downloader, ids = ids)
    }

    override fun delete(activity: ComponentActivity, id: String) {
        deleteFileNormal(activity = activity, downloader = downloader, id = id)
    }

    suspend fun pauseOrResume(id: String) {
        downloader.pauseOrResume(id)
    }

    suspend fun updateLockStatus(id: String) {
        updateDownloadDB(downloader = downloader, id = id, isLocked = true)
    }

    suspend fun updateLockStatus(ids: List<String>) {
        updateDownloadDB(downloader = downloader, ids = ids, isLocked = true)
    }
}
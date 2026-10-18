package com.looper.videodownloader.database.repository

import com.looper.videodownloader.database.dao.HistoryDao
import com.looper.videodownloader.database.data.HistoryEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class HistoryRepository(
    private val historyDao: HistoryDao,
) {
    val histories: Flow<List<HistoryEntity>> = historyDao.getHistories()

    fun getHistoryFlowByHistoryId(historyId: Long): Flow<HistoryEntity?> {
        return historyDao.getHistoryByHistoryId(historyId)
    }

    suspend fun addHistory(
        historyId: Long,
        lastUrl: String,
        title: String,
        iconUrl: String? = null,
    ) = withContext(Dispatchers.IO) {
        historyDao.insertHistory(
            HistoryEntity(
                historyId = historyId,
                lastUrl = lastUrl,
                iconUrl = iconUrl,
                title = title,
            )
        )
    }

    suspend fun updateHistoryDetails(
        historyId: Long,
        lastUrl: String,
        title: String,
        iconUrl: String,
        isHomePage: Boolean,
        lastVisitedAt: Long = System.currentTimeMillis()
    ) = withContext(Dispatchers.IO) {
        historyDao.updateHistoryDetails(
            historyId = historyId,
            lastUrl = lastUrl,
            title = title,
            iconUrl = iconUrl,
            isHomePage = isHomePage,
            lastVisitedAt = lastVisitedAt
        )
    }

    suspend fun updateHomePage(
        historyId: Long,
        isHomePage: Boolean
    ) = withContext(Dispatchers.IO) {
        historyDao.updateHomePage(
            historyId = historyId,
            isHomePage = isHomePage
        )
    }

    suspend fun deleteHistory(history: HistoryEntity) = withContext(Dispatchers.IO) {
        historyDao.deleteHistory(history)
    }

    suspend fun deleteHistoryById(id: Long) = withContext(Dispatchers.IO) {
        historyDao.deleteHistoryById(id)
    }

    suspend fun clearHistories() = withContext(Dispatchers.IO) {
        historyDao.clearHistories()
    }
}

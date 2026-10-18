package com.looper.videodownloader.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.looper.videodownloader.database.data.HistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {

    @Query("SELECT * FROM history_table ORDER BY lastVisitedAt DESC")
    fun getHistories(): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM history_table WHERE historyId = :historyId LIMIT 1")
    fun getHistoryByHistoryId(historyId: Long): Flow<HistoryEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(history: HistoryEntity): Long

    @Update
    suspend fun updateHistory(history: HistoryEntity)

    @Query("""
        UPDATE history_table 
        SET lastUrl = :lastUrl, title = :title, lastVisitedAt = :lastVisitedAt , iconUrl = :iconUrl, isHomePage = :isHomePage
        WHERE historyId = :historyId
    """)
    suspend fun updateHistoryDetails(historyId: Long, lastUrl: String, title: String, iconUrl: String, isHomePage: Boolean, lastVisitedAt: Long)

    @Query("UPDATE history_table SET isHomePage = :isHomePage WHERE historyId = :historyId")
    suspend fun updateHomePage(historyId: Long, isHomePage: Boolean)

    @Delete
    suspend fun deleteHistory(history: HistoryEntity)

    @Query("DELETE FROM history_table WHERE historyId = :historyId")
    suspend fun deleteHistoryById(historyId: Long)

    @Query("DELETE FROM history_table")
    suspend fun clearHistories()
}

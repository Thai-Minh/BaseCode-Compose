package com.looper.videodownloader.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.looper.videodownloader.database.data.YoutubeDLDownloadEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface YoutubeDLDownloadDao {
    @Query("SELECT * FROM youtube_dl_download ORDER BY updatedAt DESC")
    fun youtubeDLDownloadEntities(): Flow<List<YoutubeDLDownloadEntity>>

    @Query("SELECT * FROM youtube_dl_download WHERE id = :id LIMIT 1")
    fun youtubeDLDownloadEntity(id: String): Flow<YoutubeDLDownloadEntity?>

    @Query("SELECT * FROM youtube_dl_download WHERE id = :id LIMIT 1")
    suspend fun get(id: String): YoutubeDLDownloadEntity?

    @Query("SELECT * FROM youtube_dl_download WHERE status IN ('QUEUED', 'RUNNING')")
    suspend fun getInterrupted(): List<YoutubeDLDownloadEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: YoutubeDLDownloadEntity)

    @Query("UPDATE youtube_dl_download SET status = :status, progress = :progress, etaSeconds = :eta, updatedAt = :updatedAt, error = :error WHERE id = :id")
    suspend fun updateState(id: String, status: String, progress: Int, eta: Long, updatedAt: Long, error: String?)

    @Query("UPDATE youtube_dl_download SET finalUri = :uri, finalDisplayName = :name, mimeType = :mimeType, status = 'COMPLETED', progress = 100, size = :size, updatedAt = :updatedAt, error = NULL WHERE id = :id")
    suspend fun complete(id: String, uri: String, name: String, mimeType: String, size: Long, updatedAt: Long)

    @Query("DELETE FROM youtube_dl_download WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM youtube_dl_download WHERE finalUri = :uri")
    suspend fun deleteByFinalUri(uri: String): Int
}

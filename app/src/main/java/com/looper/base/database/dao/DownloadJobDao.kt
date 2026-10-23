package com.looper.base.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.looper.base.database.data.DownloadJobEntity
import com.looper.base.database.data.DownloadStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadJobDao {

    @Query("SELECT * FROM download_job ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<DownloadJobEntity>>

    @Query("SELECT * FROM download_job WHERE id = :id LIMIT 1")
    fun observe(id: String): Flow<DownloadJobEntity?>

    @Query("SELECT * FROM download_job WHERE mediaItemId = :mediaItemId")
    fun observeForItem(mediaItemId: String): Flow<List<DownloadJobEntity>>

    @Query("SELECT * FROM download_job WHERE id = :id LIMIT 1")
    suspend fun get(id: String): DownloadJobEntity?

    @Query("SELECT * FROM download_job WHERE status IN ('QUEUED', 'RUNNING') ORDER BY createdAt ASC")
    suspend fun getInterrupted(): List<DownloadJobEntity>

    @Query("SELECT * FROM download_job WHERE status = 'QUEUED' ORDER BY createdAt ASC LIMIT 1")
    suspend fun nextQueued(): DownloadJobEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(job: DownloadJobEntity)

    @Query(
        """
        UPDATE download_job
        SET status = :status, progress = :progress, etaSeconds = :etaSeconds,
            downloadedBytes = :downloadedBytes, totalBytes = :totalBytes,
            error = :error, updatedAt = :updatedAt
        WHERE id = :id
        """
    )
    suspend fun updateProgress(
        id: String,
        status: DownloadStatus,
        progress: Int,
        etaSeconds: Long,
        downloadedBytes: Long,
        totalBytes: Long,
        error: String? = null,
        updatedAt: Long = System.currentTimeMillis(),
    )

    @Query("UPDATE download_job SET status = :status, error = :error, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateStatus(
        id: String,
        status: DownloadStatus,
        error: String? = null,
        updatedAt: Long = System.currentTimeMillis(),
    )

    @Query("UPDATE download_job SET tempDirectory = :tempDirectory, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateTempDirectory(id: String, tempDirectory: String?, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM download_job WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM download_job WHERE status IN ('FAILED', 'CANCELED')")
    suspend fun clearFinished()
}

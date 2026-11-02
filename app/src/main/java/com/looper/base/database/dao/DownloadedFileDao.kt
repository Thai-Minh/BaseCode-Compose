package com.looper.base.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.looper.base.database.data.DownloadKind
import com.looper.base.database.data.DownloadOrigin
import com.looper.base.database.data.DownloadedFileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadedFileDao {

    @Query("SELECT * FROM downloaded_file ORDER BY downloadedAt DESC")
    fun observeAll(): Flow<List<DownloadedFileEntity>>

    @Query("SELECT * FROM downloaded_file WHERE mediaItemId = :mediaItemId")
    fun observeForItem(mediaItemId: String): Flow<List<DownloadedFileEntity>>

    @Query("SELECT * FROM downloaded_file WHERE mediaItemId = :mediaItemId AND kind = :kind LIMIT 1")
    suspend fun get(mediaItemId: String, kind: DownloadKind): DownloadedFileEntity?

    @Query("SELECT * FROM downloaded_file WHERE localUri = :localUri LIMIT 1")
    suspend fun getByLocalUri(localUri: String): DownloadedFileEntity?

    @Query("SELECT COALESCE(SUM(size), 0) FROM downloaded_file")
    fun observeTotalSize(): Flow<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(file: DownloadedFileEntity)

    @Query("DELETE FROM downloaded_file WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM downloaded_file WHERE mediaItemId = :mediaItemId AND kind = :kind")
    suspend fun delete(mediaItemId: String, kind: DownloadKind)

    @Query("DELETE FROM downloaded_file WHERE localUri = :localUri")
    suspend fun deleteByLocalUri(localUri: String): Int

    @Query("SELECT * FROM downloaded_file WHERE origin = :origin")
    suspend fun getByOrigin(origin: DownloadOrigin): List<DownloadedFileEntity>
}

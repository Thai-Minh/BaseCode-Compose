package com.looper.base.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.looper.base.database.data.StreamCacheEntity

@Dao
interface StreamCacheDao {

    @Query("SELECT * FROM stream_cache WHERE mediaItemId = :mediaItemId LIMIT 1")
    suspend fun get(mediaItemId: String): StreamCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(stream: StreamCacheEntity)

    @Query("UPDATE stream_cache SET expiresAt = 0 WHERE mediaItemId = :mediaItemId")
    suspend fun invalidate(mediaItemId: String)

    @Query("DELETE FROM stream_cache WHERE expiresAt < :before")
    suspend fun deleteExpired(before: Long = System.currentTimeMillis())
}

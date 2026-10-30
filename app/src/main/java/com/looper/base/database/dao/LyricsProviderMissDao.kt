package com.looper.base.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.looper.base.database.data.LyricsProvider
import com.looper.base.database.data.LyricsProviderMissEntity

@Dao
interface LyricsProviderMissDao {

    @Query("SELECT provider FROM lyrics_provider_miss WHERE mediaItemId = :mediaItemId")
    suspend fun getMissedProviders(mediaItemId: String): List<LyricsProvider>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(misses: List<LyricsProviderMissEntity>)

    @Query("DELETE FROM lyrics_provider_miss WHERE mediaItemId = :mediaItemId")
    suspend fun clear(mediaItemId: String)

    @Query("DELETE FROM lyrics_provider_miss WHERE attemptedAt < :before")
    suspend fun deleteOlderThan(before: Long)
}

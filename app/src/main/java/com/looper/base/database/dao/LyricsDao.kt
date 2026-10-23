package com.looper.base.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.looper.base.database.data.LyricsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LyricsDao {

    @Query("SELECT * FROM lyrics WHERE mediaItemId = :mediaItemId LIMIT 1")
    fun observe(mediaItemId: String): Flow<LyricsEntity?>

    @Query("SELECT * FROM lyrics WHERE mediaItemId = :mediaItemId LIMIT 1")
    suspend fun get(mediaItemId: String): LyricsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(lyrics: LyricsEntity)

    @Query(
        """
        INSERT INTO lyrics (mediaItemId, syncLevel, content, source, fetchedAt, lastAttemptAt, attemptCount)
        VALUES (:mediaItemId, 'NONE', NULL, NULL, NULL, :attemptedAt, 1)
        ON CONFLICT(mediaItemId) DO UPDATE SET
            lastAttemptAt = :attemptedAt,
            attemptCount = attemptCount + 1
        """
    )
    suspend fun recordFailedAttempt(
        mediaItemId: String,
        attemptedAt: Long = System.currentTimeMillis()
    )

    @Query("DELETE FROM lyrics WHERE mediaItemId = :mediaItemId")
    suspend fun delete(mediaItemId: String)
}

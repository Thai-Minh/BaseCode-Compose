package com.looper.base.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.looper.base.database.data.LyricsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LyricsDao {

    @Query("SELECT * FROM lyrics WHERE mediaItemId = :mediaItemId LIMIT 1")
    fun observe(mediaItemId: String): Flow<LyricsEntity?>

    @Query("SELECT * FROM lyrics WHERE mediaItemId = :mediaItemId LIMIT 1")
    suspend fun get(mediaItemId: String): LyricsEntity?

    @Query("SELECT * FROM lyrics WHERE rawContent IS NOT NULL AND parserVersion < :parserVersion")
    suspend fun getOutdated(parserVersion: Int): List<LyricsEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun replace(lyrics: LyricsEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(lyrics: LyricsEntity): Long

    @Query("UPDATE lyrics SET lastAttemptAt = :attemptedAt, attemptCount = attemptCount + 1 WHERE mediaItemId = :mediaItemId")
    suspend fun incrementAttempt(mediaItemId: String, attemptedAt: Long)

    @Transaction
    suspend fun recordFailedAttempt(
        mediaItemId: String,
        attemptedAt: Long = System.currentTimeMillis()
    ) {
        val inserted = insertIfAbsent(
            lyrics = LyricsEntity(
                mediaItemId = mediaItemId,
                lastAttemptAt = attemptedAt,
                attemptCount = 1,
            )
        )
        if (inserted == -1L) {
            incrementAttempt(mediaItemId = mediaItemId, attemptedAt = attemptedAt)
        }
    }

    @Query("UPDATE lyrics SET userOffsetMs = :offsetMs WHERE mediaItemId = :mediaItemId")
    suspend fun updateUserOffset(mediaItemId: String, offsetMs: Long)

    @Query("DELETE FROM lyrics WHERE mediaItemId = :mediaItemId")
    suspend fun delete(mediaItemId: String)
}

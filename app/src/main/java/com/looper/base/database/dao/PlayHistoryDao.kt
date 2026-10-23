package com.looper.base.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.looper.base.database.data.PlayHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlayHistoryDao {

    @Query("SELECT * FROM play_history ORDER BY playedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<PlayHistoryEntity>>

    @Query("SELECT * FROM play_history WHERE mediaItemId = :mediaItemId ORDER BY playedAt DESC LIMIT 1")
    suspend fun getLatest(mediaItemId: String): PlayHistoryEntity?

    @Insert
    suspend fun insert(entry: PlayHistoryEntity): Long

    @Query("UPDATE play_history SET lastPositionMs = :positionMs WHERE id = :id")
    suspend fun updatePosition(id: Long, positionMs: Long)

    @Query(
        """
        DELETE FROM play_history
        WHERE id NOT IN (SELECT id FROM play_history ORDER BY playedAt DESC LIMIT :keep)
        """
    )
    suspend fun trim(keep: Int = 500)

    @Query("DELETE FROM play_history WHERE mediaItemId = :mediaItemId")
    suspend fun deleteForItem(mediaItemId: String)

    @Query("DELETE FROM play_history")
    suspend fun clear()
}

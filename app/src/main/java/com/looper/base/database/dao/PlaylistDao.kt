package com.looper.base.database.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.looper.base.database.data.MediaItemEntity
import com.looper.base.database.data.PlaylistEntity
import com.looper.base.database.data.PlaylistItemEntity
import kotlinx.coroutines.flow.Flow

data class PlaylistSummary(
    @Embedded val playlist: PlaylistEntity,
    val itemCount: Int,
    val totalDurationMs: Long,
)

@Dao
interface PlaylistDao {

    @Query(
        """
        SELECT p.*,
               COUNT(pi.mediaItemId) AS itemCount,
               COALESCE(SUM(m.durationMs), 0) AS totalDurationMs
        FROM playlist p
        LEFT JOIN playlist_item pi ON pi.playlistId = p.id
        LEFT JOIN media_item m ON m.id = pi.mediaItemId
        GROUP BY p.id
        ORDER BY p.updatedAt DESC
        """
    )
    fun observeSummaries(): Flow<List<PlaylistSummary>>

    @Query("SELECT * FROM playlist WHERE id = :id LIMIT 1")
    fun observe(id: String): Flow<PlaylistEntity?>

    @Query("SELECT * FROM playlist WHERE id = :id LIMIT 1")
    suspend fun get(id: String): PlaylistEntity?

    @Query("SELECT * FROM playlist WHERE isFavorite = 1 ORDER BY updatedAt DESC")
    fun observeFavorites(): Flow<List<PlaylistEntity>>

    @Query(
        """
        SELECT m.* FROM media_item m
        INNER JOIN playlist_item pi ON pi.mediaItemId = m.id
        WHERE pi.playlistId = :playlistId
        ORDER BY pi.position ASC
        """
    )
    fun observeItems(playlistId: String): Flow<List<MediaItemEntity>>

    @Query(
        """
        SELECT m.thumbnailUrl FROM media_item m
        INNER JOIN playlist_item pi ON pi.mediaItemId = m.id
        WHERE pi.playlistId = :playlistId AND m.thumbnailUrl IS NOT NULL
        ORDER BY pi.position ASC
        LIMIT :limit
        """
    )
    fun observeThumbnails(playlistId: String, limit: Int = 4): Flow<List<String>>

    @Query("SELECT playlistId FROM playlist_item WHERE mediaItemId = :mediaItemId")
    suspend fun getPlaylistIdsContaining(mediaItemId: String): List<String>

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM playlist_item WHERE playlistId = :playlistId")
    suspend fun nextPosition(playlistId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(playlist: PlaylistEntity)

    @Update
    suspend fun update(playlist: PlaylistEntity)

    @Query("UPDATE playlist SET isFavorite = :isFavorite, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setFavorite(
        id: String,
        isFavorite: Boolean,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query("UPDATE playlist SET updatedAt = :updatedAt WHERE id = :id")
    suspend fun touch(id: String, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM playlist WHERE id = :id")
    suspend fun delete(id: String)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertItem(item: PlaylistItemEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertItems(items: List<PlaylistItemEntity>)

    @Transaction
    suspend fun addToEnd(playlistId: String, mediaItemId: String): Boolean {
        val rowId = insertItem(
            PlaylistItemEntity(
                playlistId = playlistId,
                mediaItemId = mediaItemId,
                position = nextPosition(playlistId),
            )
        )
        if (rowId != -1L) touch(playlistId)
        return rowId != -1L
    }

    @Query("UPDATE playlist_item SET position = :position WHERE playlistId = :playlistId AND mediaItemId = :mediaItemId")
    suspend fun updatePosition(playlistId: String, mediaItemId: String, position: Int)

    @Transaction
    suspend fun reorder(playlistId: String, orderedMediaItemIds: List<String>) {
        orderedMediaItemIds.forEachIndexed { index, mediaItemId ->
            updatePosition(playlistId, mediaItemId, index)
        }
        touch(playlistId)
    }

    @Query("DELETE FROM playlist_item WHERE playlistId = :playlistId AND mediaItemId = :mediaItemId")
    suspend fun removeItem(playlistId: String, mediaItemId: String)

    @Query("DELETE FROM playlist_item WHERE playlistId = :playlistId")
    suspend fun clearItems(playlistId: String)
}

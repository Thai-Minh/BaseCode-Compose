package com.looper.base.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.looper.base.database.data.MediaItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaItemDao {

    @Query("SELECT * FROM media_item ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<MediaItemEntity>>

    @Query("SELECT * FROM media_item WHERE id = :id LIMIT 1")
    fun observe(id: String): Flow<MediaItemEntity?>

    @Query("SELECT * FROM media_item WHERE id = :id LIMIT 1")
    suspend fun get(id: String): MediaItemEntity?

    @Query("SELECT * FROM media_item WHERE sourceUrl = :sourceUrl LIMIT 1")
    suspend fun getBySourceUrl(sourceUrl: String): MediaItemEntity?

    @Query("SELECT * FROM media_item WHERE platformId = :platformId AND sourceId = :sourceId LIMIT 1")
    suspend fun getBySourceId(platformId: String, sourceId: String): MediaItemEntity?

    @Query("SELECT * FROM media_item WHERE isFavorite = 1 ORDER BY favoritedAt DESC")
    fun observeFavorites(): Flow<List<MediaItemEntity>>

    @Query(
        """
        SELECT m.* FROM media_item m
        INNER JOIN downloaded_file f ON f.mediaItemId = m.id
        WHERE f.kind = :kind
        ORDER BY f.downloadedAt DESC
        """
    )
    fun observeDownloaded(kind: String): Flow<List<MediaItemEntity>>

    @Query(
        """
        SELECT m.* FROM media_item m
        INNER JOIN (
            SELECT mediaItemId, MAX(playedAt) AS lastPlayedAt
            FROM play_history GROUP BY mediaItemId
        ) h ON h.mediaItemId = m.id
        ORDER BY h.lastPlayedAt DESC
        LIMIT :limit
        """
    )
    fun observeRecentlyPlayed(limit: Int): Flow<List<MediaItemEntity>>

    @Query(
        """
        SELECT * FROM media_item
        WHERE title LIKE '%' || :query || '%' OR artist LIKE '%' || :query || '%'
        ORDER BY addedAt DESC
        """
    )
    fun search(query: String): Flow<List<MediaItemEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(item: MediaItemEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: MediaItemEntity)

    @Update
    suspend fun update(item: MediaItemEntity)

    @Query("UPDATE media_item SET isFavorite = :isFavorite, favoritedAt = :favoritedAt WHERE id = :id")
    suspend fun setFavorite(id: String, isFavorite: Boolean, favoritedAt: Long?)

    @Query("UPDATE media_item SET playCount = playCount + 1 WHERE id = :id")
    suspend fun incrementPlayCount(id: String)

    @Query("UPDATE media_item SET thumbnailLocalPath = :path WHERE id = :id")
    suspend fun setThumbnailLocalPath(id: String, path: String?)

    @Query("DELETE FROM media_item WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM media_item WHERE id IN (:ids)")
    suspend fun delete(ids: List<String>)
}

package com.looper.base.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.looper.base.database.data.PlaybackEventEntity
import kotlinx.coroutines.flow.Flow

data class MediaPlayStat(
    val mediaItemId: String,
    val playCount: Int,
    val listenedMs: Long,
)

data class ListenRow(
    val mediaItemId: String,
    val startedAt: Long,
    val durationMs: Long,
    val listenedMs: Long,
    val title: String,
    val artist: String?,
    val album: String?,
)

data class FirstListen(
    val mediaItemId: String,
    val startedAt: Long,
)

data class HourPlayStat(
    val hour: Int,
    val listenedMs: Long,
)

@Dao
interface PlaybackEventDao {

    @Insert
    suspend fun insert(event: PlaybackEventEntity): Long

    @Query(
        """
        SELECT mediaItemId, COUNT(*) AS playCount, SUM(listenedMs) AS listenedMs
        FROM playback_event
        WHERE startedAt >= :from AND startedAt < :to
        GROUP BY mediaItemId
        ORDER BY playCount DESC, listenedMs DESC
        LIMIT :limit
        """
    )
    fun observeTopMedia(from: Long, to: Long, limit: Int): Flow<List<MediaPlayStat>>

    @Query("SELECT COALESCE(SUM(listenedMs), 0) FROM playback_event WHERE startedAt >= :from AND startedAt < :to")
    fun observeTotalListened(from: Long, to: Long): Flow<Long>

    @Query(
        """
        SELECT CAST(strftime('%H', startedAt / 1000, 'unixepoch', 'localtime') AS INTEGER) AS hour,
               SUM(listenedMs) AS listenedMs
        FROM playback_event
        WHERE startedAt >= :from AND startedAt < :to
        GROUP BY hour
        ORDER BY hour ASC
        """
    )
    fun observeListenedByHour(from: Long, to: Long): Flow<List<HourPlayStat>>

    @Query(
        """
        SELECT e.mediaItemId, e.startedAt, e.durationMs, e.listenedMs, m.title, m.artist, m.album
        FROM playback_event e
        INNER JOIN media_item m ON m.id = e.mediaItemId
        WHERE e.startedAt >= :from AND e.startedAt < :to
        ORDER BY e.startedAt ASC
        """
    )
    suspend fun getRows(from: Long, to: Long): List<ListenRow>

    @Query("SELECT mediaItemId, MIN(startedAt) AS startedAt FROM playback_event GROUP BY mediaItemId")
    suspend fun firstListenPerItem(): List<FirstListen>

    @Query("SELECT * FROM playback_event WHERE startedAt >= :from")
    suspend fun getSince(from: Long): List<PlaybackEventEntity>

    @Query("DELETE FROM playback_event")
    suspend fun clear()
}

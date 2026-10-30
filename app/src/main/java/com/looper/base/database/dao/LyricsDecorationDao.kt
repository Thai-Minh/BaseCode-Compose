package com.looper.base.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.looper.base.database.data.LyricsDecorationEntity
import com.looper.base.database.data.LyricsDecorationKind
import kotlinx.coroutines.flow.Flow

@Dao
interface LyricsDecorationDao {

    @Query("SELECT * FROM lyrics_decoration WHERE mediaItemId = :mediaItemId")
    fun observe(mediaItemId: String): Flow<List<LyricsDecorationEntity>>

    @Query("SELECT * FROM lyrics_decoration WHERE mediaItemId = :mediaItemId AND kind = :kind AND language = :language LIMIT 1")
    suspend fun get(
        mediaItemId: String,
        kind: LyricsDecorationKind,
        language: String
    ): LyricsDecorationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(decorations: List<LyricsDecorationEntity>)

    @Query("DELETE FROM lyrics_decoration WHERE mediaItemId = :mediaItemId AND kind = :kind")
    suspend fun delete(mediaItemId: String, kind: LyricsDecorationKind)
}

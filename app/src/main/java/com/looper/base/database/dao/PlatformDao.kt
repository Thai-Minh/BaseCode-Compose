package com.looper.base.database.dao

import androidx.room.Dao
import androidx.room.Query
import com.looper.base.database.data.PlatformEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlatformDao {

    @Query("SELECT * FROM platform WHERE isEnabled = 1 ORDER BY orderIndex ASC")
    fun observeEnabled(): Flow<List<PlatformEntity>>

    @Query("SELECT * FROM platform WHERE id = :id LIMIT 1")
    suspend fun get(id: String): PlatformEntity?
}

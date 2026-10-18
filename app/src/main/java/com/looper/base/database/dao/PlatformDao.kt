package com.looper.videodownloader.database.dao

import androidx.room.Dao
import androidx.room.Query
import com.looper.videodownloader.database.data.PlatformEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlatformDao {

    @Query("SELECT * FROM platform_table WHERE isEnabled = 1 ORDER BY orderIndex ASC")
    fun getPlatforms(): Flow<List<PlatformEntity>>
}

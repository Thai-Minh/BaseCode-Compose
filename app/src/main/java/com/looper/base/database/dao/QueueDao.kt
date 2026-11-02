package com.looper.base.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.looper.base.database.data.QueueItemEntity

@Dao
interface QueueDao {

    @Query("SELECT * FROM queue_item ORDER BY position ASC")
    suspend fun getAll(): List<QueueItemEntity>

    @Insert
    suspend fun insertAll(items: List<QueueItemEntity>)

    @Query("DELETE FROM queue_item")
    suspend fun clear()

    @Transaction
    suspend fun replace(items: List<QueueItemEntity>) {
        clear()
        insertAll(items = items)
    }
}

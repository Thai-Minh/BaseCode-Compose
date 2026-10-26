package com.looper.base.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.looper.base.database.data.EqPresetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EqPresetDao {

    @Query("SELECT * FROM eq_preset ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<EqPresetEntity>>

    @Query("SELECT * FROM eq_preset WHERE id = :id LIMIT 1")
    suspend fun get(id: String): EqPresetEntity?

    @Query("SELECT COUNT(*) FROM eq_preset WHERE name = :name COLLATE NOCASE AND id != :excludeId")
    suspend fun countByName(name: String, excludeId: String = ""): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(preset: EqPresetEntity)

    @Query("UPDATE eq_preset SET name = :name, updatedAt = :updatedAt WHERE id = :id")
    suspend fun rename(id: String, name: String, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM eq_preset WHERE id = :id")
    suspend fun delete(id: String)
}

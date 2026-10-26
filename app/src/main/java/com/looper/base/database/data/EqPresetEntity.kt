package com.looper.base.database.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "eq_preset",
    indices = [Index(value = ["name"], unique = true)],
)
data class EqPresetEntity(
    @PrimaryKey val id: String,
    val name: String,
    val bandLevelsMb: String,
    val bassBoost: Int = 0,
    val virtualizer: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

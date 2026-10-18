package com.looper.videodownloader.database.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "platform_table")
data class PlatformEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val iconName: String,
    val url: String,
    val orderIndex: Int,
    val isEnabled: Boolean = true,
)
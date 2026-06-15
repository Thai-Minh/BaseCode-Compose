package com.looper.base.database.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
class TestEntity(
    @PrimaryKey
    val id: String
)
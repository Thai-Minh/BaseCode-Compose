package com.looper.base.database.data

import androidx.room.Entity
import androidx.room.ForeignKey

@Entity(
    tableName = "lyrics_decoration",
    primaryKeys = ["mediaItemId", "kind", "language"],
    foreignKeys = [
        ForeignKey(
            entity = LyricsEntity::class,
            parentColumns = ["mediaItemId"],
            childColumns = ["mediaItemId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class LyricsDecorationEntity(
    val mediaItemId: String,
    val kind: LyricsDecorationKind,
    val language: String,
    val source: LyricsDecorationSource,
    val linesJson: String,
    val lineCount: Int,
    val createdAt: Long = System.currentTimeMillis(),
)

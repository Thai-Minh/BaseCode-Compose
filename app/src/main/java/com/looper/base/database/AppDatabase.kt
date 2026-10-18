package com.looper.base.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.looper.videodownloader.database.dao.HistoryDao
import com.looper.videodownloader.database.dao.PlatformDao
import com.looper.videodownloader.database.dao.YoutubeDLDownloadDao
import com.looper.videodownloader.database.data.HistoryEntity
import com.looper.videodownloader.database.data.PlatformEntity
import com.looper.videodownloader.database.data.YoutubeDLDownloadEntity

private const val DATABASE_NAME = "my_app_name"

@Database(
    entities = [
        PlatformEntity::class,
        HistoryEntity::class,
        YoutubeDLDownloadEntity::class,
    ],
    version = 4,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun platformDao(): PlatformDao

    abstract fun historyDao(): HistoryDao

    abstract fun youtubeDLDownloadDao(): YoutubeDLDownloadDao
}

object DatabaseFactory {
    fun create(context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            DATABASE_NAME
        )
            .addCallback(object : RoomDatabase.Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    super.onCreate(db)
                    seedDefaultPlatforms(db)
                }
            })
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
            .fallbackToDestructiveMigration(false)
            .build()
    }

    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `history_table` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `lastUrl` TEXT NOT NULL,
                    `iconUrl` TEXT,
                    `title` TEXT NOT NULL,
                    `lastVisitedAt` INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_history_table_lastUrl` ON `history_table` (`lastUrl`)"
            )
        }
    }

    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `youtube_dl_download` (
                    `id` TEXT NOT NULL PRIMARY KEY,
                    `processId` TEXT NOT NULL,
                    `sourceUrl` TEXT NOT NULL,
                    `title` TEXT NOT NULL,
                    `ownerName` TEXT,
                    `thumbnailUrl` TEXT,
                    `durationMs` INTEGER NOT NULL,
                    `extractor` TEXT NOT NULL,
                    `formatSelector` TEXT NOT NULL,
                    `requestedName` TEXT NOT NULL,
                    `suggestedExtension` TEXT NOT NULL,
                    `tempDirectory` TEXT,
                    `finalUri` TEXT,
                    `finalDisplayName` TEXT,
                    `mimeType` TEXT,
                    `status` TEXT NOT NULL,
                    `progress` INTEGER NOT NULL,
                    `etaSeconds` INTEGER NOT NULL,
                    `size` INTEGER NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    `error` TEXT
                )
                """.trimIndent()
            )
        }
    }

    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `youtube_dl_download` ADD COLUMN `cookieFilePath` TEXT")
            db.execSQL("ALTER TABLE `youtube_dl_download` ADD COLUMN `userAgent` TEXT")
            db.execSQL("ALTER TABLE `youtube_dl_download` ADD COLUMN `referer` TEXT")
        }
    }

    private fun seedDefaultPlatforms(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            INSERT OR IGNORE INTO `platform_table` (`id`, `name`, `iconName`, `url`, `orderIndex`, `isEnabled`) VALUES
            ('facebook', 'Facebook', 'ic_home_fb', 'https://www.facebook.com/watch', 0, 1),
            ('tiktok', 'Tiktok', 'ic_home_tiktok', 'https://www.tiktok.com/', 1, 1),
            ('pinterest', 'Pinterest', 'ic_home_pinterest', 'https://www.pinterest.com/', 2, 1),
            ('vimeo', 'Vimeo', 'ic_home_vimeo', 'https://vimeo.com/', 3, 1),
            ('instagram', 'Instagram', 'ic_home_instagram', 'https://www.instagram.com/', 4, 1),
            ('x', 'X', 'ic_home_x', 'https://x.com/', 5, 1)
            """.trimIndent()
        )
    }
}

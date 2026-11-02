package com.looper.base.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.looper.base.database.dao.DownloadJobDao
import com.looper.base.database.dao.DownloadedFileDao
import com.looper.base.database.dao.EqPresetDao
import com.looper.base.database.dao.LyricsDao
import com.looper.base.database.dao.LyricsDecorationDao
import com.looper.base.database.dao.LyricsProviderMissDao
import com.looper.base.database.dao.MediaItemDao
import com.looper.base.database.dao.PlaybackEventDao
import com.looper.base.database.dao.PlatformDao
import com.looper.base.database.dao.PlayHistoryDao
import com.looper.base.database.dao.PlaylistDao
import com.looper.base.database.dao.QueueDao
import com.looper.base.database.dao.SearchHistoryDao
import com.looper.base.database.dao.StreamCacheDao
import com.looper.base.database.data.DownloadJobEntity
import com.looper.base.database.data.DownloadedFileEntity
import com.looper.base.database.data.EqPresetEntity
import com.looper.base.database.data.LyricsDecorationEntity
import com.looper.base.database.data.LyricsEntity
import com.looper.base.database.data.LyricsProviderMissEntity
import com.looper.base.database.data.MediaItemEntity
import com.looper.base.database.data.PlatformEntity
import com.looper.base.database.data.PlaybackEventEntity
import com.looper.base.database.data.PlayHistoryEntity
import com.looper.base.database.data.PlaylistEntity
import com.looper.base.database.data.PlaylistItemEntity
import com.looper.base.database.data.QueueItemEntity
import com.looper.base.database.data.SearchHistoryEntity
import com.looper.base.database.data.StreamCacheEntity

const val DATABASE_NAME = "audio_player.db"

@Database(
    entities = [
        PlatformEntity::class,
        MediaItemEntity::class,
        PlaylistEntity::class,
        PlaylistItemEntity::class,
        DownloadedFileEntity::class,
        DownloadJobEntity::class,
        PlayHistoryEntity::class,
        LyricsEntity::class,
        LyricsProviderMissEntity::class,
        LyricsDecorationEntity::class,
        StreamCacheEntity::class,
        QueueItemEntity::class,
        PlaybackEventEntity::class,
        SearchHistoryEntity::class,
        EqPresetEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun platformDao(): PlatformDao

    abstract fun mediaItemDao(): MediaItemDao

    abstract fun playlistDao(): PlaylistDao

    abstract fun downloadedFileDao(): DownloadedFileDao

    abstract fun downloadJobDao(): DownloadJobDao

    abstract fun playHistoryDao(): PlayHistoryDao

    abstract fun lyricsDao(): LyricsDao

    abstract fun lyricsProviderMissDao(): LyricsProviderMissDao

    abstract fun lyricsDecorationDao(): LyricsDecorationDao

    abstract fun streamCacheDao(): StreamCacheDao

    abstract fun queueDao(): QueueDao

    abstract fun playbackEventDao(): PlaybackEventDao

    abstract fun searchHistoryDao(): SearchHistoryDao

    abstract fun eqPresetDao(): EqPresetDao
}

object DatabaseFactory {
    fun create(context: Context): AppDatabase {
        return Room.databaseBuilder(
            context = context,
            klass = AppDatabase::class.java,
            name = DATABASE_NAME
        )
            .addCallback(object : RoomDatabase.Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    super.onCreate(db)
                    seedDefaultPlatforms(db)
                }
            })
            .addMigrations(*Migrations.ALL)
            .fallbackToDestructiveMigration(dropAllTables = false)
            .build()
    }

    private fun seedDefaultPlatforms(db: SupportSQLiteDatabase) {
        db.execSQL(
            sql = """
                        INSERT OR IGNORE INTO `platform` (`id`, `name`, `iconName`, `url`, `orderIndex`, `isEnabled`) VALUES
                        ('youtube',    'YouTube',    'ic_platform_youtube',    'https://www.youtube.com/',    0, 1),
                        ('soundcloud', 'SoundCloud', 'ic_platform_soundcloud', 'https://soundcloud.com/',     1, 1),
                        ('tiktok',     'TikTok',     'ic_platform_tiktok',     'https://www.tiktok.com/',     2, 1),
                        ('facebook',   'Facebook',   'ic_platform_facebook',   'https://www.facebook.com/',   3, 1),
                        ('instagram',  'Instagram',  'ic_platform_instagram',  'https://www.instagram.com/',  4, 1),
                        ('twitter',    'X',          'ic_platform_x',          'https://x.com/',              5, 1),
                        ('vimeo',      'Vimeo',      'ic_platform_vimeo',      'https://vimeo.com/',          6, 1),
                        ('dailymotion','Dailymotion','ic_platform_dailymotion','https://www.dailymotion.com/',7, 1)
                        """.trimIndent()
        )
    }
}

object Migrations {

    val ALL: Array<Migration> = arrayOf()

    // val MIGRATION_1_2 = object : Migration(1, 2) {
    //     override fun migrate(db: SupportSQLiteDatabase) {
    //         db.execSQL("ALTER TABLE `media_item` ADD COLUMN `width` INTEGER")
    //     }
    // }
}

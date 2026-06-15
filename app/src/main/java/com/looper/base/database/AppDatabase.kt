package com.looper.base.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.looper.base.database.dao.TestDao
import com.looper.base.database.data.TestEntity

private const val DATABASE_NAME = "my_app_name"

@Database(
    entities = [
        TestEntity::class
    ],
    version = 1
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun teamDao(): TestDao
}

object DatabaseFactory {
    fun create(context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            DATABASE_NAME
        )
//            .addMigrations(Migrations.MIGRATION_1_2)
            .fallbackToDestructiveMigration(false)
            .build()
    }
}

object Migrations {

    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {

        }
    }

}
package com.brycewg.pinme.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        PreferenceEntity::class,
        ExtractEntity::class,
        MarketItemEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun pinMeDao(): PinMeDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `market_item` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `title` TEXT NOT NULL,
                        `contentDesc` TEXT NOT NULL,
                        `emoji` TEXT NOT NULL,
                        `capsuleColor` TEXT NOT NULL,
                        `durationMinutes` INTEGER NOT NULL,
                        `isEnabled` INTEGER NOT NULL DEFAULT 1,
                        `createdAtMillis` INTEGER NOT NULL
                    )
                """.trimIndent())
            }
        }
    }
}


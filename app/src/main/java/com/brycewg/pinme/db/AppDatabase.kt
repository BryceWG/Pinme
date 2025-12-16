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
    version = 5,
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

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 添加 isPreset 和 presetKey 字段
                db.execSQL("ALTER TABLE `market_item` ADD COLUMN `isPreset` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `market_item` ADD COLUMN `presetKey` TEXT DEFAULT NULL")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 给 extract 表添加 emoji 字段，存储 LLM 生成的 emoji
                db.execSQL("ALTER TABLE `extract` ADD COLUMN `emoji` TEXT DEFAULT NULL")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 给 extract 表添加 qrCodeBase64 字段，存储检测到的二维码图片
                db.execSQL("ALTER TABLE `extract` ADD COLUMN `qrCodeBase64` TEXT DEFAULT NULL")
            }
        }
    }
}


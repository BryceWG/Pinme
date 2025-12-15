package com.brycewg.pinme.db

import android.content.Context
import androidx.room.Room

object DatabaseProvider {
    private val lock = Any()
    lateinit var db: AppDatabase
        private set

    fun isInitialized(): Boolean = ::db.isInitialized

    fun init(context: Context) {
        synchronized(lock) {
            if (!::db.isInitialized) {
                db = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "pinme.db"
                )
                    .addMigrations(AppDatabase.MIGRATION_1_2)
                    .build()
            }
        }
    }

    fun dao(): PinMeDao = db.pinMeDao()
}


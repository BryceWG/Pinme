package com.brycewg.pinme.db

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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
                    .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3)
                    .addCallback(object : RoomDatabase.Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            // 新数据库创建时插入预置类型
                            CoroutineScope(Dispatchers.IO).launch {
                                insertPresetMarketItems()
                            }
                        }

                        override fun onOpen(db: SupportSQLiteDatabase) {
                            super.onOpen(db)
                            // 每次打开数据库时检查并插入缺失的预置类型
                            CoroutineScope(Dispatchers.IO).launch {
                                insertPresetMarketItems()
                            }
                        }
                    })
                    .build()
            }
        }
    }

    fun dao(): PinMeDao = db.pinMeDao()

    private suspend fun insertPresetMarketItems() {
        val dao = db.pinMeDao()
        PresetMarketTypes.ALL.forEach { preset ->
            val existing = dao.getMarketItemByPresetKey(preset.presetKey!!)
            if (existing == null) {
                dao.insertMarketItem(preset)
            }
        }
    }
}

/**
 * 预置市场类型定义
 */
object PresetMarketTypes {
    val PICKUP_CODE = MarketItemEntity(
        title = "取件码",
        contentDesc = "快递取件码",
        emoji = "📦",
        capsuleColor = "#FFC107",
        durationMinutes = 30,
        isEnabled = true,
        isPreset = true,
        presetKey = "pickup_code"
    )

    val MEAL_CODE = MarketItemEntity(
        title = "取餐码",
        contentDesc = "餐饮取餐号/排队号",
        emoji = "🍔",
        capsuleColor = "#FF5722",
        durationMinutes = 15,
        isEnabled = true,
        isPreset = true,
        presetKey = "meal_code"
    )

    val TRAIN_TICKET = MarketItemEntity(
        title = "火车票",
        contentDesc = "车次、座位、检票口信息",
        emoji = "🚄",
        capsuleColor = "#2196F3",
        durationMinutes = 120,
        isEnabled = true,
        isPreset = true,
        presetKey = "train_ticket"
    )

    val VERIFICATION_CODE = MarketItemEntity(
        title = "验证码",
        contentDesc = "短信/邮件验证码",
        emoji = "🔐",
        capsuleColor = "#4CAF50",
        durationMinutes = 5,
        isEnabled = true,
        isPreset = true,
        presetKey = "verification_code"
    )

    val QR_CODE = MarketItemEntity(
        title = "二维码",
        contentDesc = "截图中的二维码类型（如票券二维码、支付二维码等）",
        emoji = "📱",
        capsuleColor = "#9C27B0",
        durationMinutes = 10,
        isEnabled = true,
        isPreset = true,
        presetKey = "qr_code"
    )

    val NO_MATCH = MarketItemEntity(
        title = "无匹配",
        contentDesc = "屏幕内容摘要（无特定类型匹配时）",
        emoji = "📋",
        capsuleColor = "#607D8B",
        durationMinutes = 10,
        isEnabled = true,
        isPreset = true,
        presetKey = "no_match"
    )

    val ALL = listOf(
        PICKUP_CODE,
        MEAL_CODE,
        TRAIN_TICKET,
        VERIFICATION_CODE,
        QR_CODE,
        NO_MATCH
    )
}


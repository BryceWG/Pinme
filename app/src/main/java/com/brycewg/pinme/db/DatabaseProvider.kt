package com.brycewg.pinme.db

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object DatabaseProvider {
    private val lock = Any()
    private val insertMutex = Mutex()
    private var presetItemsInserted = false
    lateinit var db: AppDatabase
        private set

    fun isInitialized(): Boolean = ::db.isInitialized

    fun init(context: Context) {
        synchronized(lock) {
            if (!::db.isInitialized) {
                db =
                    Room
                        .databaseBuilder(
                            context.applicationContext,
                            AppDatabase::class.java,
                            "pinme.db",
                        ).addMigrations(
                            AppDatabase.MIGRATION_1_2,
                            AppDatabase.MIGRATION_2_3,
                            AppDatabase.MIGRATION_3_4,
                            AppDatabase.MIGRATION_4_5,
                            AppDatabase.MIGRATION_5_6,
                            AppDatabase.MIGRATION_6_7,
                        ).addCallback(
                            object : RoomDatabase.Callback() {
                                override fun onOpen(db: SupportSQLiteDatabase) {
                                    super.onOpen(db)
                                    // 每次打开数据库时检查并插入缺失的预置类型
                                    // 注意：onCreate 后必定会调用 onOpen，所以只需在 onOpen 中处理
                                    CoroutineScope(Dispatchers.IO).launch {
                                        insertPresetMarketItems()
                                    }
                                }
                            },
                        ).build()
            }
        }
    }

    fun dao(): PinMeDao = db.pinMeDao()

    private suspend fun insertPresetMarketItems() {
        // 使用 Mutex 确保只执行一次，避免并发问题
        insertMutex.withLock {
            if (presetItemsInserted) return
            val dao = db.pinMeDao()
            // 清理已废弃的二维码预设（二维码检测由独立管线处理，不再作为 AI 识别类型）
            dao.deleteMarketItemByPresetKey("qr_code")
            PresetMarketTypes.ALL.forEach { preset ->
                // 使用带事务的方法确保检查和插入的原子性
                dao.insertPresetMarketItemIfNotExists(preset)
            }
            presetItemsInserted = true
        }
    }
}

/**
 * 预置市场类型定义
 */
object PresetMarketTypes {
    val PICKUP_CODE =
        MarketItemEntity(
            title = "取件码",
            contentDesc = "取件码+驿站/快递柜名称（如：5-8-2-1 菜鸟驿站）",
            outputExample = "5-8-2-1\n菜鸟驿站",
            emoji = "📦",
            capsuleColor = "#FFC107",
            durationMinutes = 30,
            isEnabled = true,
            isPreset = true,
            presetKey = "pickup_code",
        )

    val MEAL_CODE =
        MarketItemEntity(
            title = "取餐码",
            contentDesc = "餐饮取餐号/排队号",
            outputExample = "A128\nB032",
            emoji = "🍔",
            capsuleColor = "#FF5722",
            durationMinutes = 15,
            isEnabled = true,
            isPreset = true,
            presetKey = "meal_code",
        )

    val TRAIN_TICKET =
        MarketItemEntity(
            title = "火车票",
            contentDesc = "出发时间+车次+座位+检票口（如：14:30 G1234 07车12F B2检票口）",
            outputExample = "14:30 G1234 07车12F B2检票口",
            emoji = "🚄",
            capsuleColor = "#2196F3",
            durationMinutes = 120,
            isEnabled = true,
            isPreset = true,
            presetKey = "train_ticket",
        )

    val VERIFICATION_CODE =
        MarketItemEntity(
            title = "验证码",
            contentDesc = "短信/邮件验证码",
            outputExample = "847291",
            emoji = "🔐",
            capsuleColor = "#4CAF50",
            durationMinutes = 5,
            isEnabled = true,
            isPreset = true,
            presetKey = "verification_code",
        )

    val NO_MATCH =
        MarketItemEntity(
            title = "无匹配",
            contentDesc = "屏幕内容摘要（无特定类型匹配时）",
            outputExample = "微信支付成功 ￥128.00\n航班CA1234 准点\n无有效信息",
            emoji = "📋",
            capsuleColor = "#607D8B",
            durationMinutes = 10,
            isEnabled = true,
            isPreset = true,
            presetKey = "no_match",
        )

    val ALL =
        listOf(
            PICKUP_CODE,
            MEAL_CODE,
            TRAIN_TICKET,
            VERIFICATION_CODE,
            NO_MATCH,
        )
}

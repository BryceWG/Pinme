package com.brycewg.pinme.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
abstract class PinMeDao {
    @Query("SELECT value FROM preference WHERE prefKey = :prefKey LIMIT 1")
    abstract fun getPreferenceFlow(prefKey: String): Flow<String?>

    @Query("SELECT value FROM preference WHERE prefKey = :prefKey LIMIT 1")
    abstract suspend fun getPreference(prefKey: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertPreference(preference: PreferenceEntity)

    @Transaction
    open suspend fun setPreference(key: String, value: String) {
        insertPreference(PreferenceEntity(key, value))
    }

    @Insert
    abstract suspend fun insertExtract(extract: ExtractEntity): Long

    @Query("UPDATE extract SET qrCodeBase64 = :qrCodeBase64 WHERE id = :id")
    abstract suspend fun updateExtractQrCode(id: Long, qrCodeBase64: String)

    @Query("SELECT * FROM extract ORDER BY createdAtMillis DESC LIMIT :limit")
    abstract fun getLatestExtractsFlow(limit: Int): Flow<List<ExtractEntity>>

    @Query("SELECT * FROM extract ORDER BY createdAtMillis DESC LIMIT :limit")
    abstract suspend fun getLatestExtractsOnce(limit: Int): List<ExtractEntity>

    @Query("DELETE FROM extract")
    abstract suspend fun deleteAllExtracts()

    @Query("DELETE FROM extract WHERE id = :id")
    abstract suspend fun deleteExtractById(id: Long)

    @Query("SELECT COUNT(*) FROM extract")
    abstract suspend fun getExtractCount(): Int

    @Query("SELECT * FROM extract ORDER BY createdAtMillis DESC LIMIT :limit OFFSET :offset")
    abstract suspend fun getExtractsWithOffset(limit: Int, offset: Int): List<ExtractEntity>

    @Query("DELETE FROM extract WHERE id IN (SELECT id FROM extract ORDER BY createdAtMillis ASC LIMIT :deleteCount)")
    abstract suspend fun deleteOldestExtracts(deleteCount: Int)

    @Transaction
    open suspend fun trimExtractsToLimit(maxCount: Int) {
        val currentCount = getExtractCount()
        if (currentCount > maxCount) {
            deleteOldestExtracts(currentCount - maxCount)
        }
    }

    // Market Item operations
    @Insert
    abstract suspend fun insertMarketItem(item: MarketItemEntity): Long

    @Update
    abstract suspend fun updateMarketItem(item: MarketItemEntity)

    @Delete
    abstract suspend fun deleteMarketItem(item: MarketItemEntity)

    @Query("SELECT * FROM market_item ORDER BY createdAtMillis DESC")
    abstract fun getAllMarketItemsFlow(): Flow<List<MarketItemEntity>>

    @Query("SELECT * FROM market_item WHERE isEnabled = 1 ORDER BY createdAtMillis DESC")
    abstract suspend fun getEnabledMarketItems(): List<MarketItemEntity>

    @Query("SELECT * FROM market_item WHERE id = :id LIMIT 1")
    abstract suspend fun getMarketItemById(id: Long): MarketItemEntity?

    @Query("DELETE FROM market_item WHERE id = :id")
    abstract suspend fun deleteMarketItemById(id: Long)

    @Query("SELECT * FROM market_item WHERE presetKey = :presetKey LIMIT 1")
    abstract suspend fun getMarketItemByPresetKey(presetKey: String): MarketItemEntity?

    @Query("SELECT * FROM market_item WHERE isPreset = 1 ORDER BY createdAtMillis ASC")
    abstract fun getPresetMarketItemsFlow(): Flow<List<MarketItemEntity>>

    @Query("SELECT * FROM market_item WHERE isPreset = 0 ORDER BY createdAtMillis DESC")
    abstract fun getCustomMarketItemsFlow(): Flow<List<MarketItemEntity>>

    /**
     * 在事务中插入预置市场项（如果不存在）
     * 使用事务确保检查和插入的原子性，避免重复插入
     */
    @Transaction
    open suspend fun insertPresetMarketItemIfNotExists(item: MarketItemEntity) {
        val existing = getMarketItemByPresetKey(item.presetKey!!)
        if (existing == null) {
            insertMarketItem(item)
        }
    }
}


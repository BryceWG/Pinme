package com.brycewg.pinme.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "preference")
data class PreferenceEntity(
    @PrimaryKey val prefKey: String,
    val value: String
)

@Entity(tableName = "extract")
data class ExtractEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val content: String,
    val emoji: String? = null,        // LLM 生成的 emoji，更精准地表达内容
    val qrCodeBase64: String? = null, // 二维码图片的 Base64 编码（JPEG 格式）
    val source: String = "screen",
    val rawModelOutput: String = "",
    val createdAtMillis: Long
)

@Entity(tableName = "market_item")
data class MarketItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,           // 标题，如"取件码"
    val contentDesc: String,     // 内容描述，如"取件码号"
    val outputExample: String = "", // 输出示例（可多行）
    val emoji: String,           // 显示的emoji，如"📦"
    val capsuleColor: String,    // 胶囊颜色，如"#FFC107"
    val durationMinutes: Int,    // 显示时长（分钟）
    val isEnabled: Boolean = true,
    val isPreset: Boolean = false,  // 是否为预置类型（预置类型不可删除）
    val presetKey: String? = null,  // 预置类型的唯一标识，用于避免重复插入
    val createdAtMillis: Long = System.currentTimeMillis()
)





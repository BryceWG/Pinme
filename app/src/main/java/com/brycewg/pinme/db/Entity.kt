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
    val source: String = "screen",
    val rawModelOutput: String = "",
    val createdAtMillis: Long
)

@Entity(tableName = "market_item")
data class MarketItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,           // 标题，如"取件码"
    val contentDesc: String,     // 内容描述，如"取件码号"
    val emoji: String,           // 显示的emoji，如"📦"
    val capsuleColor: String,    // 胶囊颜色，如"#FFC107"
    val durationMinutes: Int,    // 显示时长（分钟）
    val isEnabled: Boolean = true,
    val createdAtMillis: Long = System.currentTimeMillis()
)


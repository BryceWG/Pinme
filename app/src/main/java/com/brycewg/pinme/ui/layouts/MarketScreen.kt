package com.brycewg.pinme.ui.layouts

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.brycewg.pinme.db.DatabaseProvider
import com.brycewg.pinme.db.MarketItemEntity
import kotlinx.coroutines.launch

// 非线性时间刻度：1-10间隔1, 11-30间隔2, 31-60间隔5, 61-180间隔10, 最后为永久(-1)
private val timeSteps: List<Int> = buildList {
    // 1-10 分钟，间隔 1
    for (i in 1..10) add(i)
    // 11-30 分钟，间隔 2
    for (i in 12..30 step 2) add(i)
    // 31-60 分钟，间隔 5
    for (i in 35..60 step 5) add(i)
    // 61-180 分钟，间隔 10
    for (i in 70..180 step 10) add(i)
    // 永久
    add(-1)
}

// 将分钟数转换为滑块位置
private fun minutesToSliderPosition(minutes: Int): Float {
    val index = if (minutes == -1) {
        timeSteps.size - 1
    } else {
        timeSteps.indexOfFirst { it >= minutes && it != -1 }.takeIf { it >= 0 } ?: (timeSteps.size - 2)
    }
    return index.toFloat()
}

// 将滑块位置转换为分钟数
private fun sliderPositionToMinutes(position: Float): Int {
    val index = position.toInt().coerceIn(0, timeSteps.size - 1)
    return timeSteps[index]
}

// 格式化显示时间
private fun formatDuration(minutes: Int): String {
    return when {
        minutes == -1 -> "永久"
        minutes >= 60 -> {
            val hours = minutes / 60
            val mins = minutes % 60
            if (mins == 0) "${hours}小时" else "${hours}小时${mins}分钟"
        }
        else -> "${minutes}分钟"
    }
}

// 预设颜色列表
private val presetColors = listOf(
    "#FFC107" to "黄色",    // 取件码
    "#4CAF50" to "绿色",    // 成功/验证码
    "#2196F3" to "蓝色",    // 信息
    "#FF5722" to "橙色",    // 警告
    "#E91E63" to "粉色",    // 优惠
    "#9C27B0" to "紫色",    // 特殊
    "#00BCD4" to "青色",    // 交通
    "#795548" to "棕色",    // 包裹
    "#607D8B" to "灰色",    // 默认
    "#F44336" to "红色",    // 紧急
)


@Composable
fun MarketScreen() {
    val context = LocalContext.current
    val dao = DatabaseProvider.dao()
    val scope = rememberCoroutineScope()

    val presetItems by dao.getPresetMarketItemsFlow().collectAsState(initial = emptyList())
    val customItems by dao.getCustomMarketItemsFlow().collectAsState(initial = emptyList())
    var showAddDialog by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<MarketItemEntity?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        // 顶部说明区域
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "管理识别类型，定制提取内容与通知样式。",
                style = MaterialTheme.typography.bodyMedium
            )

            Button(
                onClick = { showAddDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("添加自定义类型")
            }
        }

        // 列表区域
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 预置类型区域
            if (presetItems.isNotEmpty()) {
                item {
                    Text(
                        text = "预置类型",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
                items(presetItems, key = { it.id }) { item ->
                    MarketItemCard(
                        item = item,
                        onEdit = { editingItem = item },
                        onDelete = null, // 预置类型不能删除
                        onToggleEnabled = { enabled ->
                            scope.launch {
                                dao.updateMarketItem(item.copy(isEnabled = enabled))
                            }
                        }
                    )
                }
            }

            // 自定义类型区域
            item {
                Text(
                    text = "自定义类型",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                )
            }

            if (customItems.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("暂无自定义类型", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "点击上方按钮添加自定义识别类型。",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            } else {
                items(customItems, key = { it.id }) { item ->
                    MarketItemCard(
                        item = item,
                        onEdit = { editingItem = item },
                        onDelete = {
                            scope.launch {
                                dao.deleteMarketItem(item)
                                Toast.makeText(context, "已删除", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onToggleEnabled = { enabled ->
                            scope.launch {
                                dao.updateMarketItem(item.copy(isEnabled = enabled))
                            }
                        }
                    )
                }
            }

            // 底部空白
            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }

    // 添加对话框
    if (showAddDialog) {
        MarketItemDialog(
            item = null,
            onDismiss = { showAddDialog = false },
            onSave = { newItem ->
                scope.launch {
                    dao.insertMarketItem(newItem)
                    Toast.makeText(context, "已添加", Toast.LENGTH_SHORT).show()
                }
                showAddDialog = false
            }
        )
    }

    // 编辑对话框
    if (editingItem != null) {
        MarketItemDialog(
            item = editingItem,
            onDismiss = { editingItem = null },
            onSave = { updatedItem ->
                scope.launch {
                    dao.updateMarketItem(updatedItem)
                    Toast.makeText(context, "已保存", Toast.LENGTH_SHORT).show()
                }
                editingItem = null
            }
        )
    }
}

@Composable
private fun MarketItemCard(
    item: MarketItemEntity,
    onEdit: () -> Unit,
    onDelete: (() -> Unit)?,  // 为 null 时不显示删除按钮（预置类型）
    onToggleEnabled: (Boolean) -> Unit
) {
    val bgColor = try {
        Color(android.graphics.Color.parseColor(item.capsuleColor))
    } catch (e: Exception) {
        MaterialTheme.colorScheme.primary
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Emoji 和标题
                Text(
                    text = item.emoji,
                    fontSize = 28.sp
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.titleMedium
                        )
                        if (item.isPreset) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "预置",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier
                                    .background(
                                        MaterialTheme.colorScheme.secondaryContainer,
                                        RoundedCornerShape(4.dp)
                                    )
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = item.contentDesc,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // 操作按钮
                IconButton(onClick = onEdit) {
                    Icon(Icons.Rounded.Edit, contentDescription = "编辑")
                }
                if (onDelete != null) {
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Rounded.Delete,
                            contentDescription = "删除",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            // 属性展示行
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 颜色预览
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(bgColor)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "胶囊颜色",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // 时长
                Text(
                    text = formatDuration(item.durationMinutes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.weight(1f))

                // 启用开关
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (item.isEnabled) "已启用" else "已禁用",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (item.isEnabled)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Switch(
                        checked = item.isEnabled,
                        onCheckedChange = onToggleEnabled
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MarketItemDialog(
    item: MarketItemEntity?,
    onDismiss: () -> Unit,
    onSave: (MarketItemEntity) -> Unit
) {
    val isEditing = item != null
    val textFieldShape = RoundedCornerShape(16.dp)

    var title by remember { mutableStateOf(item?.title ?: "") }
    var contentDesc by remember { mutableStateOf(item?.contentDesc ?: "") }
    var emoji by remember { mutableStateOf(item?.emoji ?: "📦") }
    var capsuleColor by remember { mutableStateOf(item?.capsuleColor ?: "#FFC107") }
    var sliderPosition by remember { 
        mutableFloatStateOf(minutesToSliderPosition(item?.durationMinutes ?: 10)) 
    }
    val currentMinutes = sliderPositionToMinutes(sliderPosition)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEditing) "编辑识别类型" else "添加识别类型") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // 标题输入
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("标题") },
                    placeholder = { Text("如：取件码") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = textFieldShape,
                    singleLine = true
                )

                // 内容描述
                OutlinedTextField(
                    value = contentDesc,
                    onValueChange = { contentDesc = it },
                    label = { Text("内容描述") },
                    placeholder = { Text("如：取件码号") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = textFieldShape,
                    singleLine = true
                )

                // 图标输入
                OutlinedTextField(
                    value = emoji,
                    onValueChange = { if (it.length <= 2) emoji = it },
                    label = { Text("图标") },
                    placeholder = { Text("如：📦") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = textFieldShape,
                    singleLine = true
                )

                // 颜色选择
                Text("胶囊颜色", style = MaterialTheme.typography.bodyMedium)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    presetColors.forEach { (color, name) ->
                        val colorValue = try {
                            Color(android.graphics.Color.parseColor(color))
                        } catch (e: Exception) {
                            Color.Gray
                        }
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(colorValue)
                                .border(
                                    width = if (capsuleColor == color) 3.dp else 1.dp,
                                    color = if (capsuleColor == color)
                                        MaterialTheme.colorScheme.onSurface
                                    else
                                        MaterialTheme.colorScheme.outline,
                                    shape = CircleShape
                                )
                                .clickable { capsuleColor = color }
                        )
                    }
                }

                // 时长设置
                Text(
                    text = "显示时长: ${formatDuration(currentMinutes)}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Slider(
                    value = sliderPosition,
                    onValueChange = { sliderPosition = it },
                    valueRange = 0f..(timeSteps.size - 1).toFloat(),
                    steps = timeSteps.size - 2,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = if (currentMinutes == -1) "通知将永久显示，直到手动关闭" else "通知将在指定时间后自动消失",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isBlank()) return@Button
                    val newItem = MarketItemEntity(
                        id = item?.id ?: 0,
                        title = title.trim(),
                        contentDesc = contentDesc.trim().ifBlank { title.trim() },
                        emoji = emoji.ifBlank { "📦" },
                        capsuleColor = capsuleColor,
                        durationMinutes = currentMinutes,
                        isEnabled = item?.isEnabled ?: true,
                        createdAtMillis = item?.createdAtMillis ?: System.currentTimeMillis()
                    )
                    onSave(newItem)
                },
                enabled = title.isNotBlank()
            ) {
                Text(if (isEditing) "保存" else "添加")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

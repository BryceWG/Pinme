package com.brycewg.pinme.ui.layouts

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Restore
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.getSystemService
import com.brycewg.pinme.widget.PinMeWidget
import com.brycewg.pinme.db.DatabaseProvider
import com.brycewg.pinme.db.PresetMarketTypes
import com.brycewg.pinme.db.MarketItemEntity
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TextFieldDefaults
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.abs

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
    "#FFC107" to "黄色",
    "#4CAF50" to "绿色",
    "#2196F3" to "蓝色",
    "#FF5722" to "橙色",
    "#E91E63" to "粉色",
    "#9C27B0" to "紫色",
)

private fun normalizeHexColor(input: String): String? {
    val trimmed = input.trim().uppercase()
    val hex = if (trimmed.startsWith("#")) trimmed.drop(1) else trimmed
    if (hex.length != 6 && hex.length != 8) return null
    if (!hex.all { it in '0'..'9' || it in 'A'..'F' }) return null
    return "#$hex"
}

private fun sanitizeHexInput(input: String): String {
    val cleaned = input.uppercase().filterIndexed { index, c ->
        when {
            c == '#' -> index == 0
            c in '0'..'9' || c in 'A'..'F' -> true
            else -> false
        }
    }
    return cleaned.take(9)
}

private const val MARKET_PRESET_SHARE_PREFIX = "PINME_MARKET_PRESET_V1:"

@Serializable
private data class MarketPresetShare(
    val title: String,
    val contentDesc: String,
    val outputExample: String = "",
    val emoji: String,
    val capsuleColor: String,
    val durationMinutes: Int,
    val isEnabled: Boolean = true
)

private val presetShareJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

private fun normalizeDuration(minutes: Int): Int {
    if (minutes == -1) return -1
    val validSteps = timeSteps.filter { it != -1 }
    if (validSteps.isEmpty()) return minutes
    return validSteps.minBy { abs(it - minutes) }
}

private fun buildPresetShareCode(item: MarketItemEntity): String {
    val payload = MarketPresetShare(
        title = item.title.trim(),
        contentDesc = item.contentDesc.trim(),
        outputExample = item.outputExample.trim(),
        emoji = item.emoji.trim(),
        capsuleColor = item.capsuleColor.trim(),
        durationMinutes = item.durationMinutes,
        isEnabled = item.isEnabled
    )
    return MARKET_PRESET_SHARE_PREFIX + presetShareJson.encodeToString(
        MarketPresetShare.serializer(),
        payload
    )
}

private fun extractPresetSharePayload(rawText: String): String {
    val trimmed = rawText.trim()
    val index = trimmed.indexOf(MARKET_PRESET_SHARE_PREFIX)
    return if (index >= 0) {
        trimmed.substring(index + MARKET_PRESET_SHARE_PREFIX.length).trim()
    } else {
        trimmed
    }
}

private fun parsePresetShareCode(rawText: String): List<MarketPresetShare> {
    val payload = extractPresetSharePayload(rawText)
    if (payload.isBlank()) {
        throw IllegalArgumentException("分享码为空")
    }
    val trimmed = payload.trim()
    return if (trimmed.startsWith("[")) {
        presetShareJson.decodeFromString(
            ListSerializer(MarketPresetShare.serializer()),
            trimmed
        )
    } else {
        listOf(
            presetShareJson.decodeFromString(
                MarketPresetShare.serializer(),
                trimmed
            )
        )
    }
}

private fun readClipboardText(context: Context): String? {
    val clipboard = context.getSystemService<ClipboardManager>() ?: return null
    val clip = clipboard.primaryClip ?: return null
    if (clip.itemCount == 0) return null
    return clip.getItemAt(0).coerceToText(context)?.toString()
}

private fun copyToClipboard(context: Context, label: String, text: String, toastMessage: String) {
    val clipboard = context.getSystemService<ClipboardManager>() ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(context, toastMessage, Toast.LENGTH_SHORT).show()
}


@Composable
fun MarketScreen() {
    val context = LocalContext.current
    val dao = DatabaseProvider.dao()
    val scope = rememberCoroutineScope()

    val presetItems by dao.getPresetMarketItemsFlow().collectAsState(initial = emptyList())
    val customItems by dao.getCustomMarketItemsFlow().collectAsState(initial = emptyList())
    var showAddDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    // 编辑目标与可见性分离，保证淡出动画期间数据可用
    var editTarget by remember { mutableStateOf<MarketItemEntity?>(null) }
    var showEditDialog by remember { mutableStateOf(false) }
    var importText by remember { mutableStateOf("") }
    var importError by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 顶部说明与操作（随列表一起滚动）
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "管理识别类型，定制提取内容与通知样式。",
                    style = MiuixTheme.textStyles.body2
                )

                Button(
                    onClick = { showAddDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColorsPrimary()
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("添加自定义类型")
                }

                Button(
                    onClick = { showImportDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("导入预设分享码")
                }
            }
        }

        // 预置类型区域
            if (presetItems.isNotEmpty()) {
                item {
                    SmallTitle(
                        text = "预置类型",
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
                items(presetItems, key = { it.id }) { item ->
                    MarketItemCard(
                        item = item,
                        onEdit = {
                            editTarget = item
                            showEditDialog = true
                        },
                        onShare = {
                            val shareText = buildPresetShareCode(item)
                            copyToClipboard(
                                context,
                                "PinMe 预设分享码",
                                shareText,
                                "分享码已复制"
                            )
                        },
                        onDelete = null, // 预置类型不能删除
                        onToggleEnabled = { enabled ->
                            scope.launch {
                                dao.updateMarketItem(item.copy(isEnabled = enabled))
                                PinMeWidget.updateWidgetContent(context.applicationContext)
                            }
                        },
                        onResetPreset = {
                            scope.launch {
                                val defaultItem = PresetMarketTypes.ALL.firstOrNull { it.presetKey == item.presetKey }
                                if (defaultItem != null) {
                                    dao.resetPresetMarketItems(listOf(defaultItem))
                                    PinMeWidget.updateWidgetContent(context.applicationContext)
                                    Toast.makeText(context, "已恢复预置配置", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    )
                }
            }

            // 自定义类型区域
            item {
                SmallTitle(
                    text = "自定义类型",
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                )
            }

            if (customItems.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("暂无自定义类型", style = MiuixTheme.textStyles.main)
                            Text(
                                "点击上方按钮添加自定义识别类型。",
                                style = MiuixTheme.textStyles.body2
                            )
                        }
                    }
                }
            } else {
                items(customItems, key = { it.id }) { item ->
                    MarketItemCard(
                        item = item,
                        onEdit = {
                            editTarget = item
                            showEditDialog = true
                        },
                        onShare = {
                            val shareText = buildPresetShareCode(item)
                            copyToClipboard(
                                context,
                                "PinMe 预设分享码",
                                shareText,
                                "分享码已复制"
                            )
                        },
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

    // 添加对话框
    MarketItemDialog(
        item = null,
        show = showAddDialog,
        onDismiss = { showAddDialog = false },
        onSave = { newItem ->
            scope.launch {
                dao.insertMarketItem(newItem)
                Toast.makeText(context, "已添加", Toast.LENGTH_SHORT).show()
            }
            showAddDialog = false
        }
    )

    // 编辑对话框
    MarketItemDialog(
        item = editTarget,
        show = showEditDialog,
        onDismiss = { showEditDialog = false },
        onDismissFinished = { editTarget = null },
        onSave = { updatedItem ->
            scope.launch {
                dao.updateMarketItem(updatedItem)
                Toast.makeText(context, "已保存", Toast.LENGTH_SHORT).show()
            }
            showEditDialog = false
        }
    )

    ImportPresetDialog(
        show = showImportDialog,
        shareText = importText,
        errorMessage = importError,
        onShareTextChange = {
            importText = it
            importError = null
        },
        onPasteFromClipboard = {
            val clipboardText = readClipboardText(context)
            if (clipboardText.isNullOrBlank()) {
                importError = "剪贴板为空或没有文本"
            } else {
                importText = clipboardText.trim()
                importError = null
            }
        },
        onConfirm = {
            scope.launch {
                val parsed = runCatching { parsePresetShareCode(importText) }.getOrElse {
                    importError = "分享码格式不正确"
                    return@launch
                }
                if (parsed.isEmpty()) {
                    importError = "分享码里没有可导入的预设"
                    return@launch
                }

                val existingByTitle = (customItems + presetItems)
                    .associateBy { it.title }
                    .toMutableMap()
                var addedCount = 0
                var updatedCount = 0
                var skippedCount = 0

                parsed.forEach { share ->
                    val title = share.title.trim()
                    val normalizedColor = normalizeHexColor(share.capsuleColor)
                    if (title.isBlank() || normalizedColor == null) {
                        skippedCount++
                        return@forEach
                    }

                    val contentDesc = share.contentDesc.trim().ifBlank { title }
                    val outputExample = share.outputExample.trim()
                    val emoji = share.emoji.trim().ifBlank { "??" }.take(2)
                    val durationMinutes = normalizeDuration(share.durationMinutes)

                    val existing = existingByTitle[title]
                    if (existing != null) {
                        val updatedItem = existing.copy(
                            title = title,
                            contentDesc = contentDesc,
                            outputExample = outputExample,
                            emoji = emoji,
                            capsuleColor = normalizedColor,
                            durationMinutes = durationMinutes,
                            isEnabled = share.isEnabled
                        )
                        dao.updateMarketItem(updatedItem)
                        existingByTitle[title] = updatedItem
                        updatedCount++
                    } else {
                        val newItem = MarketItemEntity(
                            title = title,
                            contentDesc = contentDesc,
                            outputExample = outputExample,
                            emoji = emoji,
                            capsuleColor = normalizedColor,
                            durationMinutes = durationMinutes,
                            isEnabled = share.isEnabled,
                            isPreset = false,
                            presetKey = null,
                            createdAtMillis = System.currentTimeMillis()
                        )
                        dao.insertMarketItem(newItem)
                        existingByTitle[title] = newItem
                        addedCount++
                    }
                }

                if (addedCount + updatedCount == 0) {
                    importError = "分享码里没有可导入的预设"
                    return@launch
                }

                val message = buildString {
                    if (addedCount > 0) {
                        append("已导入")
                        append(addedCount)
                        append("项")
                    }
                    if (updatedCount > 0) {
                        if (isNotEmpty()) append("，")
                        append("已更新")
                        append(updatedCount)
                        append("项")
                    }
                    if (skippedCount > 0) {
                        if (isNotEmpty()) append("，")
                        append("跳过")
                        append(skippedCount)
                        append("项")
                    }
                }
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                showImportDialog = false
                importText = ""
                importError = null
            }
        },
        onDismiss = { showImportDialog = false }
    )
}

@Composable
private fun MarketItemCard(
    item: MarketItemEntity,
    onEdit: () -> Unit,
    onShare: (() -> Unit)?,
    onDelete: (() -> Unit)?,  // 为 null 时不显示删除按钮（预置类型）
    onToggleEnabled: (Boolean) -> Unit,
    onResetPreset: (() -> Unit)? = null
) {
    val bgColor = try {
        Color(android.graphics.Color.parseColor(item.capsuleColor))
    } catch (e: Exception) {
        MiuixTheme.colorScheme.primary
    }

    Card(
        modifier = Modifier.fillMaxWidth()
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
                            style = MiuixTheme.textStyles.main
                        )
                        if (item.isPreset) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "预置",
                                style = MiuixTheme.textStyles.footnote2,
                                color = MiuixTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier
                                    .background(
                                        MiuixTheme.colorScheme.secondaryContainer,
                                        RoundedCornerShape(4.dp)
                                    )
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = item.contentDesc,
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                }

                // 操作按钮（预置项与自定义项均为行内直展；预置项无删除）
                if (item.isPreset && onResetPreset != null) {
                    IconButton(onClick = onResetPreset) {
                        Icon(Icons.Rounded.Restore, contentDescription = "恢复默认")
                    }
                }
                if (onShare != null) {
                    IconButton(onClick = onShare) {
                        Icon(Icons.Rounded.ContentCopy, contentDescription = "复制分享码")
                    }
                }
                IconButton(onClick = onEdit) {
                    Icon(Icons.Rounded.Edit, contentDescription = "编辑")
                }
                if (onDelete != null) {
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Rounded.Delete,
                            contentDescription = "删除",
                            tint = MiuixTheme.colorScheme.error
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
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                }

                // 时长
                Text(
                    text = formatDuration(item.durationMinutes),
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )

                Spacer(modifier = Modifier.weight(1f))

                // 启用开关
                Switch(
                    checked = item.isEnabled,
                    onCheckedChange = onToggleEnabled
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MarketItemDialog(
    item: MarketItemEntity?,
    show: Boolean,
    onDismiss: () -> Unit,
    onDismissFinished: () -> Unit = {},
    onSave: (MarketItemEntity) -> Unit
) {
    val isEditing = item != null
    val scrollState = rememberScrollState()
    val maxDialogHeight = (LocalConfiguration.current.screenHeightDp * 0.75f).dp
    val stateKey = item?.id ?: -1L

    var title by remember(show, stateKey) { mutableStateOf(item?.title ?: "") }
    var contentDesc by remember(show, stateKey) { mutableStateOf(item?.contentDesc ?: "") }
    var outputExample by remember(show, stateKey) { mutableStateOf(item?.outputExample ?: "") }
    var emoji by remember(show, stateKey) { mutableStateOf(item?.emoji ?: "📦") }
    var capsuleColor by remember(show, stateKey) { mutableStateOf(item?.capsuleColor ?: "#FFC107") }
    var colorInput by remember(show, stateKey) { mutableStateOf(item?.capsuleColor ?: "#FFC107") }
    var sliderPosition by remember(show, stateKey) {
        mutableFloatStateOf(minutesToSliderPosition(item?.durationMinutes ?: 10))
    }
    val currentMinutes = sliderPositionToMinutes(sliderPosition)
    val normalizedColor = normalizeHexColor(colorInput)
    val isColorValid = normalizedColor != null
    val previewColor = try {
        Color(android.graphics.Color.parseColor(capsuleColor))
    } catch (e: Exception) {
        MiuixTheme.colorScheme.primary
    }

    OverlayDialog(
        show = show,
        title = if (isEditing) "编辑识别类型" else "添加识别类型",
        onDismissRequest = onDismiss,
        onDismissFinished = onDismissFinished,
        content = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxDialogHeight)
                    .verticalScroll(scrollState)
            ) {
                // 标题输入
                TextField(
                    value = title,
                    onValueChange = { title = it },
                    label = "标题",
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // 内容描述
                TextField(
                    value = contentDesc,
                    onValueChange = { contentDesc = it },
                    label = "内容描述",
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // 输出示例
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextField(
                        value = outputExample,
                        onValueChange = { outputExample = it },
                        label = "输出示例",
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 1,
                        maxLines = 6
                    )
                    Text(
                        "每行一个示例",
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                }

                // 图标输入
                TextField(
                    value = emoji,
                    onValueChange = { if (it.length <= 2) emoji = it },
                    label = "图标",
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // 颜色选择
                Text("胶囊颜色", style = MiuixTheme.textStyles.body2)
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
                                        MiuixTheme.colorScheme.onSurface
                                    else
                                        MiuixTheme.colorScheme.outline,
                                    shape = CircleShape
                                )
                                .clickable {
                                    capsuleColor = color
                                    colorInput = color
                                }
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(previewColor)
                            .border(
                                width = 1.dp,
                                color = if (isColorValid)
                                    MiuixTheme.colorScheme.outline
                                else
                                    MiuixTheme.colorScheme.error,
                                shape = CircleShape
                            )
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        TextField(
                            value = colorInput,
                            onValueChange = { raw ->
                                val cleaned = sanitizeHexInput(raw)
                                colorInput = cleaned
                                val normalized = normalizeHexColor(cleaned)
                                if (normalized != null) {
                                    capsuleColor = normalized
                                }
                            },
                            label = "自定义颜色（十六进制）",
                            singleLine = true,
                            colors = if (!isColorValid && colorInput.isNotBlank()) {
                                TextFieldDefaults.textFieldColors(
                                    labelColor = MiuixTheme.colorScheme.error,
                                    borderColor = MiuixTheme.colorScheme.error
                                )
                            } else {
                                TextFieldDefaults.textFieldColors()
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            if (isColorValid) "支持 #RRGGBB 或 #AARRGGBB"
                            else "请输入 6 或 8 位十六进制颜色",
                            style = MiuixTheme.textStyles.footnote1,
                            color = if (!isColorValid && colorInput.isNotBlank())
                                MiuixTheme.colorScheme.error
                            else
                                MiuixTheme.colorScheme.onSurfaceVariantSummary
                        )
                    }
                }

                // 时长设置
                Text(
                    text = "显示时长: ${formatDuration(currentMinutes)}",
                    style = MiuixTheme.textStyles.body2
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
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )

                // 操作按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(text = "取消", onClick = onDismiss)
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = {
                            if (title.isBlank() || !isColorValid) return@Button
                            val newItem = MarketItemEntity(
                                id = item?.id ?: 0,
                                title = title.trim(),
                                contentDesc = contentDesc.trim().ifBlank { title.trim() },
                                outputExample = outputExample.trim(),
                                emoji = emoji.ifBlank { "📦" },
                                capsuleColor = capsuleColor,
                                durationMinutes = currentMinutes,
                                isEnabled = item?.isEnabled ?: true,
                                isPreset = item?.isPreset ?: false,
                                presetKey = item?.presetKey,
                                createdAtMillis = item?.createdAtMillis ?: System.currentTimeMillis()
                            )
                            onSave(newItem)
                        },
                        enabled = title.isNotBlank() && isColorValid,
                        colors = ButtonDefaults.buttonColorsPrimary()
                    ) {
                        Text(if (isEditing) "保存" else "添加")
                    }
                }
            }
        }
    )
}

@Composable
private fun ImportPresetDialog(
    show: Boolean,
    shareText: String,
    errorMessage: String?,
    onShareTextChange: (String) -> Unit,
    onPasteFromClipboard: () -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    OverlayDialog(
        show = show,
        title = "导入预设",
        onDismissRequest = onDismiss,
        content = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextField(
                        value = shareText,
                        onValueChange = onShareTextChange,
                        label = "分享码",
                        minLines = 4,
                        maxLines = 8,
                        colors = if (errorMessage != null) {
                            TextFieldDefaults.textFieldColors(
                                labelColor = MiuixTheme.colorScheme.error,
                                borderColor = MiuixTheme.colorScheme.error
                            )
                        } else {
                            TextFieldDefaults.textFieldColors()
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (errorMessage != null) {
                        Text(
                            errorMessage,
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.error
                        )
                    } else {
                        Text(
                            "粘贴以 $MARKET_PRESET_SHARE_PREFIX 开头的分享码",
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                        )
                    }
                }

                TextButton(text = "从剪贴板粘贴", onClick = onPasteFromClipboard)

                // 操作按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(text = "取消", onClick = onDismiss)
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = onConfirm,
                        enabled = shareText.isNotBlank(),
                        colors = ButtonDefaults.buttonColorsPrimary()
                    ) {
                        Text("导入")
                    }
                }
            }
        }
    )
}

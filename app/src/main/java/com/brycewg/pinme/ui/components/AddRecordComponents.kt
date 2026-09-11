package com.brycewg.pinme.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.brycewg.pinme.db.DatabaseProvider
import com.brycewg.pinme.db.ExtractEntity
import com.brycewg.pinme.db.MarketItemEntity
import com.brycewg.pinme.extract.ExtractWorkflow
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.FloatingActionButton
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TextFieldDefaults
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 添加记录的操作回调
 */
data class AddRecordActions(
    val onManualAdd: (title: String, content: String, emoji: String?) -> Unit,
    val onPickImage: () -> Unit,
    val onTakePhoto: () -> Unit,
)

/**
 * CompositionLocal 用于在 Composable 树中传递 AddRecordActions
 */
val LocalAddRecordActions = staticCompositionLocalOf<AddRecordActions?> { null }

/**
 * 可展开的悬浮操作按钮
 */
@Composable
fun ExpandableFAB(
    expanded: Boolean,
    onExpandChange: (Boolean) -> Unit,
    onManualClick: () -> Unit,
    onImageClick: () -> Unit,
    onCameraClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 135f else 0f,
        label = "fab_rotation",
    )

    // FAB 按钮组
    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier,
    ) {
        // 子按钮（展开时显示）
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
        ) {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // 相机按钮
                FABMenuItem(
                    icon = Icons.Rounded.CameraAlt,
                    label = "拍照",
                    onClick = onCameraClick,
                )

                // 图库按钮
                FABMenuItem(
                    icon = Icons.Rounded.Image,
                    label = "图库",
                    onClick = onImageClick,
                )

                // 手动添加按钮
                FABMenuItem(
                    icon = Icons.Rounded.Edit,
                    label = "手动",
                    onClick = onManualClick,
                )
            }
        }

        // 主按钮
        FloatingActionButton(
            onClick = { onExpandChange(!expanded) },
            containerColor = MiuixTheme.colorScheme.primary,
        ) {
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = if (expanded) "关闭" else "添加",
                tint = MiuixTheme.colorScheme.onPrimary,
                modifier = Modifier.rotate(rotation),
            )
        }
    }
}

@Composable
private fun FABMenuItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End,
    ) {
        // 标签
        Text(
            text = label,
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurface,
            modifier =
                Modifier
                    .background(
                        MiuixTheme.colorScheme.surfaceContainer,
                        RoundedCornerShape(8.dp),
                    ).padding(horizontal = 12.dp, vertical = 6.dp),
        )

        Spacer(modifier = Modifier.width(12.dp))

        // 小 FAB
        IconButton(
            onClick = onClick,
            backgroundColor = MiuixTheme.colorScheme.secondaryContainer,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = MiuixTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/**
 * 手动添加记录对话框
 */
@Composable
fun ManualAddDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (title: String, content: String, emoji: String?) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dao = DatabaseProvider.dao()
    val marketItems by dao.getAllMarketItemsFlow().collectAsState(initial = emptyList())
    val scrollState = rememberScrollState()
    val maxDialogHeight = (LocalConfiguration.current.screenHeightDp * 0.75f).dp

    // show 变化时重置表单
    var selectedPreset by remember(show) { mutableStateOf<MarketItemEntity?>(null) }
    var templateListExpanded by remember(show) { mutableStateOf(false) }

    var title by remember(show) { mutableStateOf("") }
    var content by remember(show) { mutableStateOf("") }
    var emoji by remember(show) { mutableStateOf("") }

    // 文本提取相关状态
    var extractInput by remember(show) { mutableStateOf("") }
    var isExtracting by remember(show) { mutableStateOf(false) }
    var extractError by remember(show) { mutableStateOf<String?>(null) }

    OverlayDialog(
        show = show,
        title = "添加记录",
        onDismissRequest = onDismiss,
        content = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = maxDialogHeight)
                        .verticalScroll(scrollState),
            ) {
                // 模板选择器（必选，点击展开模板列表）
                BasicComponent(
                    title = "模板",
                    summary = selectedPreset?.let { "${it.emoji} ${it.title}" } ?: "请选择模板",
                    endActions = {
                        Icon(
                            imageVector = if (templateListExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
                        )
                    },
                    onClick = { templateListExpanded = !templateListExpanded },
                )
                AnimatedVisibility(
                    visible = templateListExpanded,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    Card {
                        marketItems.forEach { preset ->
                            BasicComponent(
                                title = "${preset.emoji} ${preset.title}",
                                onClick = {
                                    selectedPreset = preset
                                    // 自动填充
                                    title = preset.title
                                    emoji = preset.emoji
                                    templateListExpanded = false
                                },
                            )
                        }
                    }
                }

                // 标题输入
                TextField(
                    value = title,
                    onValueChange = { title = it },
                    label = "标题",
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )

                // 内容输入
                TextField(
                    value = content,
                    onValueChange = { content = it },
                    label = "内容",
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4,
                )

                // Emoji 输入
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextField(
                        value = emoji,
                        onValueChange = { if (it.length <= 4) emoji = it },
                        label = "图标（可选）",
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    Text(
                        "留空使用默认图标",
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }

                // 智能提取输入框
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextField(
                        value = extractInput,
                        onValueChange = {
                            extractInput = it
                            extractError = null
                        },
                        label = "智能提取（可选）",
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 4,
                        colors =
                            if (extractError != null) {
                                TextFieldDefaults.textFieldColors(
                                    labelColor = MiuixTheme.colorScheme.error,
                                    borderColor = MiuixTheme.colorScheme.error,
                                )
                            } else {
                                TextFieldDefaults.textFieldColors()
                            },
                        trailingIcon = {
                            if (isExtracting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                IconButton(
                                    onClick = {
                                        if (extractInput.isNotBlank()) {
                                            scope.launch {
                                                isExtracting = true
                                                extractError = null
                                                try {
                                                    val result = ExtractWorkflow(context).extractFromText(extractInput)
                                                    title = result.title
                                                    content = result.content
                                                    emoji = result.emoji ?: ""
                                                    // 清空输入框表示已处理
                                                    extractInput = ""
                                                } catch (e: Exception) {
                                                    extractError = e.message ?: "提取失败"
                                                } finally {
                                                    isExtracting = false
                                                }
                                            }
                                        }
                                    },
                                    enabled = extractInput.isNotBlank(),
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.Send,
                                        contentDescription = "提取",
                                        tint =
                                            if (extractInput.isNotBlank()) {
                                                MiuixTheme.colorScheme.primary
                                            } else {
                                                MiuixTheme.colorScheme.onSurfaceVariantActions
                                            },
                                    )
                                }
                            }
                        },
                    )
                    if (extractError != null) {
                        Text(
                            extractError!!,
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.error,
                        )
                    } else {
                        Text(
                            "输入后点击发送按钮自动填充上方字段",
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                }

                // 操作按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(text = "取消", onClick = onDismiss)
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = {
                            if (selectedPreset != null && title.isNotBlank() && content.isNotBlank()) {
                                onConfirm(
                                    title.trim(),
                                    content.trim(),
                                    emoji.trim().takeIf { it.isNotBlank() },
                                )
                            }
                        },
                        enabled = selectedPreset != null && title.isNotBlank() && content.isNotBlank() && !isExtracting,
                        colors = ButtonDefaults.buttonColorsPrimary(),
                    ) {
                        Text("添加")
                    }
                }
            }
        },
    )
}

/**
 * 编辑记录对话框
 */
@Composable
fun EditRecordDialog(
    item: ExtractEntity?,
    show: Boolean,
    onDismiss: () -> Unit,
    onDismissFinished: () -> Unit = {},
    onConfirm: (title: String, content: String, emoji: String?) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    if (item == null) return

    val scrollState = rememberScrollState()
    val maxDialogHeight = (LocalConfiguration.current.screenHeightDp * 0.75f).dp

    var title by remember(item.id) { mutableStateOf(item.title) }
    var content by remember(item.id) { mutableStateOf(item.content) }
    var emoji by remember(item.id) { mutableStateOf(item.emoji ?: "") }

    // 文本提取相关状态
    var extractInput by remember(item.id) { mutableStateOf("") }
    var isExtracting by remember(item.id) { mutableStateOf(false) }
    var extractError by remember(item.id) { mutableStateOf<String?>(null) }

    OverlayDialog(
        show = show,
        title = "编辑记录",
        onDismissRequest = onDismiss,
        onDismissFinished = onDismissFinished,
        content = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = maxDialogHeight)
                        .verticalScroll(scrollState),
            ) {
                // 标题输入
                TextField(
                    value = title,
                    onValueChange = { title = it },
                    label = "标题",
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )

                // 内容输入
                TextField(
                    value = content,
                    onValueChange = { content = it },
                    label = "内容",
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4,
                )

                // Emoji 输入
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextField(
                        value = emoji,
                        onValueChange = { if (it.length <= 4) emoji = it },
                        label = "图标（可选）",
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    Text(
                        "留空使用默认图标",
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }

                // 智能提取输入框
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextField(
                        value = extractInput,
                        onValueChange = {
                            extractInput = it
                            extractError = null
                        },
                        label = "智能提取（可选）",
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 4,
                        colors =
                            if (extractError != null) {
                                TextFieldDefaults.textFieldColors(
                                    labelColor = MiuixTheme.colorScheme.error,
                                    borderColor = MiuixTheme.colorScheme.error,
                                )
                            } else {
                                TextFieldDefaults.textFieldColors()
                            },
                        trailingIcon = {
                            if (isExtracting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                IconButton(
                                    onClick = {
                                        if (extractInput.isNotBlank()) {
                                            scope.launch {
                                                isExtracting = true
                                                extractError = null
                                                try {
                                                    val result = ExtractWorkflow(context).extractFromText(extractInput)
                                                    title = result.title
                                                    content = result.content
                                                    emoji = result.emoji ?: ""
                                                    // 清空输入框表示已处理
                                                    extractInput = ""
                                                } catch (e: Exception) {
                                                    extractError = e.message ?: "提取失败"
                                                } finally {
                                                    isExtracting = false
                                                }
                                            }
                                        }
                                    },
                                    enabled = extractInput.isNotBlank(),
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.Send,
                                        contentDescription = "提取",
                                        tint =
                                            if (extractInput.isNotBlank()) {
                                                MiuixTheme.colorScheme.primary
                                            } else {
                                                MiuixTheme.colorScheme.onSurfaceVariantActions
                                            },
                                    )
                                }
                            }
                        },
                    )
                    if (extractError != null) {
                        Text(
                            extractError!!,
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.error,
                        )
                    } else {
                        Text(
                            "输入后点击发送按钮自动填充上方字段",
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                }

                // 操作按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(text = "取消", onClick = onDismiss)
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = {
                            if (title.isNotBlank() && content.isNotBlank()) {
                                onConfirm(
                                    title.trim(),
                                    content.trim(),
                                    emoji.trim().takeIf { it.isNotBlank() },
                                )
                            }
                        },
                        enabled = title.isNotBlank() && content.isNotBlank() && !isExtracting,
                        colors = ButtonDefaults.buttonColorsPrimary(),
                    ) {
                        Text("保存")
                    }
                }
            }
        },
    )
}

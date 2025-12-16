package com.brycewg.pinme.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.brycewg.pinme.db.DatabaseProvider
import com.brycewg.pinme.db.ExtractEntity
import com.brycewg.pinme.db.MarketItemEntity
import com.brycewg.pinme.extract.ExtractWorkflow
import kotlinx.coroutines.launch

/**
 * 添加记录的操作回调
 */
data class AddRecordActions(
    val onManualAdd: (title: String, content: String, emoji: String?) -> Unit,
    val onPickImage: () -> Unit,
    val onTakePhoto: () -> Unit
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
    modifier: Modifier = Modifier
) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 135f else 0f,
        label = "fab_rotation"
    )

    // FAB 按钮组
    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
    ) {
        // 子按钮（展开时显示）
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut()
        ) {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 相机按钮
                FABMenuItem(
                    icon = Icons.Rounded.CameraAlt,
                    label = "拍照",
                    onClick = onCameraClick
                )

                // 图库按钮
                FABMenuItem(
                    icon = Icons.Rounded.Image,
                    label = "图库",
                    onClick = onImageClick
                )

                // 手动添加按钮
                FABMenuItem(
                    icon = Icons.Rounded.Edit,
                    label = "手动",
                    onClick = onManualClick
                )
            }
        }

        // 主按钮
        FloatingActionButton(
            onClick = { onExpandChange(!expanded) },
            containerColor = MaterialTheme.colorScheme.primary
        ) {
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = if (expanded) "关闭" else "添加",
                modifier = Modifier.rotate(rotation)
            )
        }
    }
}

@Composable
private fun FABMenuItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End
    ) {
        // 标签
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .background(
                    MaterialTheme.colorScheme.surface,
                    RoundedCornerShape(8.dp)
                )
                .padding(horizontal = 12.dp, vertical = 6.dp)
        )

        Spacer(modifier = Modifier.width(12.dp))

        // 小 FAB
        SmallFloatingActionButton(
            onClick = onClick,
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            shape = CircleShape
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * 手动添加记录对话框
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualAddDialog(
    onDismiss: () -> Unit,
    onConfirm: (title: String, content: String, emoji: String?) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dao = DatabaseProvider.dao()
    val marketItems by dao.getAllMarketItemsFlow().collectAsState(initial = emptyList())

    val textFieldShape = RoundedCornerShape(16.dp)

    var selectedPreset by remember { mutableStateOf<MarketItemEntity?>(null) }
    var presetExpanded by remember { mutableStateOf(false) }

    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var emoji by remember { mutableStateOf("") }

    // 文本提取相关状态
    var extractInput by remember { mutableStateOf("") }
    var isExtracting by remember { mutableStateOf(false) }
    var extractError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加记录") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // 模板选择器（必选）
                ExposedDropdownMenuBox(
                    expanded = presetExpanded,
                    onExpandedChange = { presetExpanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedPreset?.let { "${it.emoji} ${it.title}" } ?: "",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("模板") },
                        placeholder = { Text("请选择模板") },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = presetExpanded)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                        shape = textFieldShape,
                        singleLine = true
                    )

                    ExposedDropdownMenu(
                        expanded = presetExpanded,
                        onDismissRequest = { presetExpanded = false }
                    ) {
                        marketItems.forEach { item ->
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(item.emoji)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(item.title)
                                    }
                                },
                                onClick = {
                                    selectedPreset = item
                                    // 自动填充
                                    title = item.title
                                    emoji = item.emoji
                                    presetExpanded = false
                                }
                            )
                        }
                    }
                }

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

                // 内容输入
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("内容") },
                    placeholder = { Text("如：12-3-4567") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = textFieldShape,
                    minLines = 2,
                    maxLines = 4
                )

                // Emoji 输入
                OutlinedTextField(
                    value = emoji,
                    onValueChange = { if (it.length <= 4) emoji = it },
                    label = { Text("图标（可选）") },
                    placeholder = { Text("如：📦") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = textFieldShape,
                    singleLine = true,
                    supportingText = { Text("留空使用默认图标") }
                )

                Spacer(modifier = Modifier.height(4.dp))

                // 智能提取输入框
                OutlinedTextField(
                    value = extractInput,
                    onValueChange = {
                        extractInput = it
                        extractError = null
                    },
                    label = { Text("智能提取（可选）") },
                    placeholder = { Text("粘贴文本，AI 自动识别关键信息") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = textFieldShape,
                    minLines = 2,
                    maxLines = 4,
                    trailingIcon = {
                        if (isExtracting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
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
                                enabled = extractInput.isNotBlank()
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.Send,
                                    contentDescription = "提取",
                                    tint = if (extractInput.isNotBlank())
                                        MaterialTheme.colorScheme.primary
                                    else
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    isError = extractError != null,
                    supportingText = {
                        if (extractError != null) {
                            Text(extractError!!, color = MaterialTheme.colorScheme.error)
                        } else {
                            Text("输入后点击发送按钮自动填充上方字段")
                        }
                    }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (selectedPreset != null && title.isNotBlank() && content.isNotBlank()) {
                        onConfirm(
                            title.trim(),
                            content.trim(),
                            emoji.trim().takeIf { it.isNotBlank() }
                        )
                    }
                },
                enabled = selectedPreset != null && title.isNotBlank() && content.isNotBlank() && !isExtracting
            ) {
                Text("添加")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

/**
 * 编辑记录对话框
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditRecordDialog(
    item: ExtractEntity,
    onDismiss: () -> Unit,
    onConfirm: (title: String, content: String, emoji: String?) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val textFieldShape = RoundedCornerShape(16.dp)

    var title by remember { mutableStateOf(item.title) }
    var content by remember { mutableStateOf(item.content) }
    var emoji by remember { mutableStateOf(item.emoji ?: "") }

    // 文本提取相关状态
    var extractInput by remember { mutableStateOf("") }
    var isExtracting by remember { mutableStateOf(false) }
    var extractError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑记录") },
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

                // 内容输入
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("内容") },
                    placeholder = { Text("如：12-3-4567") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = textFieldShape,
                    minLines = 2,
                    maxLines = 4
                )

                // Emoji 输入
                OutlinedTextField(
                    value = emoji,
                    onValueChange = { if (it.length <= 4) emoji = it },
                    label = { Text("图标（可选）") },
                    placeholder = { Text("如：📦") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = textFieldShape,
                    singleLine = true,
                    supportingText = { Text("留空使用默认图标") }
                )

                Spacer(modifier = Modifier.height(4.dp))

                // 智能提取输入框
                OutlinedTextField(
                    value = extractInput,
                    onValueChange = {
                        extractInput = it
                        extractError = null
                    },
                    label = { Text("智能提取（可选）") },
                    placeholder = { Text("粘贴文本，AI 自动识别关键信息") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = textFieldShape,
                    minLines = 2,
                    maxLines = 4,
                    trailingIcon = {
                        if (isExtracting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
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
                                enabled = extractInput.isNotBlank()
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.Send,
                                    contentDescription = "提取",
                                    tint = if (extractInput.isNotBlank())
                                        MaterialTheme.colorScheme.primary
                                    else
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    isError = extractError != null,
                    supportingText = {
                        if (extractError != null) {
                            Text(extractError!!, color = MaterialTheme.colorScheme.error)
                        } else {
                            Text("输入后点击发送按钮自动填充上方字段")
                        }
                    }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank() && content.isNotBlank()) {
                        onConfirm(
                            title.trim(),
                            content.trim(),
                            emoji.trim().takeIf { it.isNotBlank() }
                        )
                    }
                },
                enabled = title.isNotBlank() && content.isNotBlank() && !isExtracting
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

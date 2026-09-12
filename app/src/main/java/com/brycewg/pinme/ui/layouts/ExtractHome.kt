package com.brycewg.pinme.ui.layouts

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.BitmapFactory
import android.text.format.DateFormat
import android.util.Base64
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.AnchoredDraggableDefaults
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.getSystemService
import com.brycewg.pinme.Constants
import com.brycewg.pinme.db.DatabaseProvider
import com.brycewg.pinme.db.ExtractEntity
import com.brycewg.pinme.extract.ExtractErrorStore
import com.brycewg.pinme.notification.UnifiedNotificationManager
import com.brycewg.pinme.ui.components.EditRecordDialog
import com.brycewg.pinme.ui.components.ExpandableFAB
import com.brycewg.pinme.ui.components.LocalAddRecordActions
import com.brycewg.pinme.ui.components.ManualAddDialog
import com.brycewg.pinme.widget.PinMeWidget
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import kotlin.math.roundToInt

private const val PAGE_SIZE = 5

@Composable
fun ExtractHome() {
    val context = LocalContext.current
    val dao = DatabaseProvider.dao()
    val scope = rememberCoroutineScope()
    val actions = LocalAddRecordActions.current

    // FAB 和对话框状态
    var fabExpanded by remember { mutableStateOf(false) }
    var showManualDialog by remember { mutableStateOf(false) }
    // 编辑目标与可见性分离，保证淡出动画期间数据可用
    var editTarget by remember { mutableStateOf<ExtractEntity?>(null) }
    var showEditDialog by remember { mutableStateOf(false) }

    val marketItems by dao.getAllMarketItemsFlow().collectAsState(initial = emptyList())
    val lastExtractError by dao
        .getPreferenceFlow(Constants.PREF_LAST_EXTRACT_ERROR)
        .collectAsState(initial = null)
    val extractErrorText = lastExtractError?.trim().orEmpty()

    // 分页状态
    val extracts = remember { mutableStateListOf<ExtractEntity>() }
    var totalCount by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(false) }
    var currentPage by remember { mutableIntStateOf(0) }

    val hasMore by remember {
        derivedStateOf { extracts.size < totalCount }
    }

    // 加载更多数据的函数
    val loadMore: suspend () -> Unit = {
        if (!isLoading && hasMore) {
            isLoading = true
            val newItems = dao.getExtractsWithOffset(PAGE_SIZE, currentPage * PAGE_SIZE)
            if (newItems.isNotEmpty()) {
                extracts.addAll(newItems)
                currentPage++
            }
            isLoading = false
        }
    }

    // 刷新数据的函数（重新加载第一页）
    val refresh: suspend () -> Unit = {
        isLoading = true
        totalCount = dao.getExtractCount()
        val newItems = dao.getExtractsWithOffset(PAGE_SIZE, 0)
        extracts.clear()
        extracts.addAll(newItems)
        currentPage = 1
        isLoading = false
    }

    // 初始加载
    LaunchedEffect(Unit) {
        refresh()
    }

    // 监听数据库变化以刷新列表
    LaunchedEffect(Unit) {
        dao.getLatestExtractsFlow(1).collectLatest {
            // 数据库有变化时刷新
            refresh()
        }
    }

    val listState = rememberLazyListState()

    // 监听滚动到底部
    LaunchedEffect(listState) {
        snapshotFlow {
            val layoutInfo = listState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisibleItem >= totalItems - 2 && totalItems > 0
        }.distinctUntilChanged()
            .collectLatest { shouldLoadMore ->
                if (shouldLoadMore && hasMore && !isLoading) {
                    loadMore()
                }
            }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (extractErrorText.isNotEmpty()) {
            ExtractErrorCard(
                errorText = extractErrorText,
                onClick = {
                    copyToClipboard(context, extractErrorText)
                    scope.launch { ExtractErrorStore.clear(context) }
                },
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
            )
        }

        Box(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
        ) {
            LazyColumn(
                state = listState,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (extracts.isEmpty() && !isLoading) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("暂无记录", style = MiuixTheme.textStyles.main)
                                Text(
                                    "点一下磁贴或点击右下角按钮添加。",
                                    style = MiuixTheme.textStyles.body2,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                            }
                        }
                    }
                } else {
                    items(extracts.toList(), key = { it.id }) { item ->
                        // 根据 title 匹配市场类型
                        val matchedMarketItem = marketItems.find { it.title == item.title }
                        // 优先使用 LLM 生成的 emoji，回退到类型预设的 emoji
                        val emoji = item.emoji ?: matchedMarketItem?.emoji
                        ExtractCard(
                            item = item,
                            emoji = emoji,
                            capsuleColor = matchedMarketItem?.capsuleColor,
                            onEdit = {
                                editTarget = item
                                showEditDialog = true
                            },
                            onArchive = {
                                val archived = !item.isArchived
                                val index = extracts.indexOfFirst { it.id == item.id }
                                if (index >= 0) {
                                    extracts[index] = extracts[index].copy(isArchived = archived)
                                }
                                scope.launch {
                                    dao.updateExtractArchived(item.id, archived)
                                    PinMeWidget.updateWidgetContent(context.applicationContext)
                                }
                                Toast.makeText(
                                    context,
                                    if (archived) "已归档" else "已取消归档",
                                    Toast.LENGTH_SHORT,
                                ).show()
                            },
                            onDelete = {
                                // 如果删除的记录正在显示为通知则取消
                                UnifiedNotificationManager(context).cancelExtractNotificationIfExists(item.id)
                                scope.launch {
                                    dao.deleteExtractById(item.id)
                                    PinMeWidget.updateWidgetContent(context.applicationContext)
                                }
                                Toast.makeText(context, "已删除", Toast.LENGTH_SHORT).show()
                            },
                        )
                    }

                    // 加载更多指示器
                    if (hasMore) {
                        item {
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (isLoading) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                } else {
                                    TextButton(
                                        text = "加载更多",
                                        onClick = { scope.launch { loadMore() } },
                                    )
                                }
                            }
                        }
                    }
                }

                // 底部留出 FAB 的空间
                item {
                    Box(modifier = Modifier.padding(bottom = 80.dp))
                }
            }

            ExpandableFAB(
                expanded = fabExpanded,
                onExpandChange = { fabExpanded = it },
                onManualClick = {
                    fabExpanded = false
                    showManualDialog = true
                },
                onImageClick = {
                    fabExpanded = false
                    actions?.onPickImage?.invoke()
                },
                onCameraClick = {
                    fabExpanded = false
                    actions?.onTakePhoto?.invoke()
                },
                modifier =
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp),
            )
        }
    }

    // 手动添加对话框
    ManualAddDialog(
        show = showManualDialog,
        onDismiss = { showManualDialog = false },
        onConfirm = { title, content, emoji ->
            actions?.onManualAdd?.invoke(title, content, emoji)
            showManualDialog = false
        },
    )

    // 编辑对话框
    EditRecordDialog(
        item = editTarget,
        show = showEditDialog,
        onDismiss = { showEditDialog = false },
        onDismissFinished = { editTarget = null },
        onConfirm = { title, content, emoji ->
            val item = editTarget
            if (item != null) {
                scope.launch {
                    dao.updateExtract(item.id, title, content, emoji)
                    PinMeWidget.updateWidgetContent(context.applicationContext)
                }
                Toast.makeText(context, "已保存", Toast.LENGTH_SHORT).show()
            }
            showEditDialog = false
        },
    )
}

@Composable
private fun ExtractErrorCard(
    errorText: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        insideMargin = PaddingValues(16.dp),
        colors =
            CardDefaults.defaultColors(
                color = MiuixTheme.colorScheme.errorContainer,
                contentColor = MiuixTheme.colorScheme.onErrorContainer,
            ),
        pressFeedbackType = PressFeedbackType.Sink,
        showIndication = true,
        onClick = onClick,
    ) {
        Text("识别失败", style = MiuixTheme.textStyles.main)
        Text(
            text = errorText,
            style = MiuixTheme.textStyles.body2,
            modifier = Modifier.padding(top = 6.dp),
            maxLines = 8,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "点击复制并关闭",
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

private enum class ExtractCardSwipeState {
    Closed,
    Open,
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun ExtractCard(
    item: ExtractEntity,
    emoji: String?,
    capsuleColor: String?,
    onEdit: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    val time = DateFormat.format("MM-dd HH:mm", item.createdAtMillis).toString()
    val pinTimeText = DateFormat.format("HH:mm", item.createdAtMillis).toString()
    val density = LocalDensity.current
    val revealWidthPx = with(density) { 72.dp.toPx() }

    val swipeState =
        remember(revealWidthPx) {
            AnchoredDraggableState(
                initialValue = ExtractCardSwipeState.Closed,
                anchors =
                    DraggableAnchors {
                        ExtractCardSwipeState.Closed at 0f
                        ExtractCardSwipeState.Open at revealWidthPx
                    },
            )
        }
    val swipeFlingBehavior =
        AnchoredDraggableDefaults.flingBehavior(
            state = swipeState,
            positionalThreshold = { distance: Float -> distance * 0.5f },
        )

    Box(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier =
                Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(CardDefaults.CornerRadius))
                    .background(MiuixTheme.colorScheme.errorContainer)
                    .padding(start = 12.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "删除",
                    tint = MiuixTheme.colorScheme.onErrorContainer,
                )
            }
        }

        Card(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .offset { IntOffset(swipeState.offset.roundToInt(), 0) }
                    .anchoredDraggable(
                        state = swipeState,
                        orientation = Orientation.Horizontal,
                        flingBehavior = swipeFlingBehavior,
                    ),
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 左侧 emoji 图标
                if (emoji != null) {
                    Text(
                        text = emoji,
                        fontSize = 32.sp,
                        modifier = Modifier.padding(end = 12.dp),
                    )
                }

                // 右侧内容区域
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                item.title,
                                style =
                                    if (item.isArchived) {
                                        MiuixTheme.textStyles.main.copy(
                                            textDecoration = TextDecoration.LineThrough,
                                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                        )
                                    } else {
                                        MiuixTheme.textStyles.main
                                    },
                            )
                            Text(
                                time,
                                style = MiuixTheme.textStyles.footnote1,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        IconButton(onClick = onEdit) {
                            Icon(
                                imageVector = Icons.Rounded.Edit,
                                contentDescription = "编辑",
                            )
                        }
                        IconButton(onClick = onArchive) {
                            Icon(
                                imageVector =
                                    if (item.isArchived) {
                                        Icons.Rounded.Unarchive
                                    } else {
                                        Icons.Rounded.Archive
                                    },
                                contentDescription = if (item.isArchived) "取消归档" else "归档",
                            )
                        }
                        IconButton(onClick = {
                            val notificationManager = UnifiedNotificationManager(context)
                            val isLive = notificationManager.isLiveCapsuleCustomizationAvailable()
                            // 解码二维码图片（如果有）
                            val qrBitmap =
                                item.qrCodeBase64?.let { base64 ->
                                    try {
                                        val bytes = Base64.decode(base64, Base64.NO_WRAP)
                                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                    } catch (e: Exception) {
                                        null
                                    }
                                }
                            notificationManager.showExtractNotification(
                                title = item.title,
                                content = item.content,
                                timeText = pinTimeText,
                                capsuleColor = capsuleColor,
                                emoji = emoji,
                                qrBitmap = qrBitmap,
                                extractId = item.id,
                                sourcePackage = item.sourcePackage,
                            )
                            val toastText = if (isLive) "已挂到实况通知" else "已发送通知"
                            Toast.makeText(context, toastText, Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(
                                imageVector = Icons.Rounded.PushPin,
                                contentDescription = "Pin到通知",
                            )
                        }
                        IconButton(onClick = { copyToClipboard(context, item.content) }) {
                            Icon(
                                imageVector = Icons.Rounded.ContentCopy,
                                contentDescription = "复制",
                            )
                        }
                    }

                    Text(
                        item.content,
                        style =
                            if (item.isArchived) {
                                MiuixTheme.textStyles.paragraph.copy(
                                    textDecoration = TextDecoration.LineThrough,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                            } else {
                                MiuixTheme.textStyles.paragraph
                            },
                    )
                }
            }
        }
    }
}

private fun copyToClipboard(
    context: Context,
    text: String,
) {
    val clipboard = context.getSystemService<ClipboardManager>() ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText("PinMe", text))
    Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
}

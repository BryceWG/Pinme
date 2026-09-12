package com.brycewg.pinme.ui.layouts

import android.Manifest
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Build
import android.util.Base64
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.brycewg.pinme.BuildConfig
import com.brycewg.pinme.Constants
import com.brycewg.pinme.Constants.LlmProvider
import com.brycewg.pinme.R
import com.brycewg.pinme.capture.AccessibilityCaptureService
import com.brycewg.pinme.capture.CaptureActivity
import com.brycewg.pinme.capture.RootCaptureService
import com.brycewg.pinme.db.DatabaseProvider
import com.brycewg.pinme.notification.UnifiedNotificationManager
import com.brycewg.pinme.ui.PinMeTextField as TextField
import com.brycewg.pinme.vllm.VllmClient
import com.brycewg.pinme.vllm.getLlmScopedPreference
import com.brycewg.pinme.vllm.migrateLegacyLlmPreferencesToScoped
import com.brycewg.pinme.vllm.setLlmScopedPreference
import com.brycewg.pinme.vllm.toStoredValue
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SliderPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.io.ByteArrayOutputStream

@OptIn(FlowPreview::class)
@Composable
fun AppSettings(onShowTutorial: () -> Unit = {}) {
    val context = LocalContext.current
    val dao = DatabaseProvider.dao()
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val nextKeyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
    val nextKeyboardActions =
        KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })

    var selectedProvider by remember { mutableStateOf(LlmProvider.ZHIPU) }
    var apiKey by remember { mutableStateOf("") }
    var apiKeyVisible by remember(selectedProvider) { mutableStateOf(false) }
    var model by remember { mutableStateOf("") }
    var temperature by remember { mutableFloatStateOf(0.1f) }
    var customBaseUrl by remember { mutableStateOf("") }
    var extraParams by remember { mutableStateOf("") }
    var isTesting by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var isHydratingProviderPrefs by remember { mutableStateOf(true) }
    var maxHistoryCount by remember { mutableStateOf(Constants.DEFAULT_MAX_HISTORY_COUNT) }

    // LLM 请求超时时间（秒，全局生效）
    var timeoutSeconds by remember { mutableStateOf(Constants.DEFAULT_LLM_TIMEOUT_SECONDS) }

    // 自定义系统指令（仅第一句作为角色描述）
    var customSystemInstruction by remember { mutableStateOf(Constants.DEFAULT_SYSTEM_INSTRUCTION) }

    // 无障碍截图模式相关状态
    var useAccessibilityCapture by remember { mutableStateOf(false) }
    var accessibilityServiceEnabled by remember { mutableStateOf(false) }
    var showAccessibilityDialog by remember { mutableStateOf(false) }

    // Root 截图模式相关状态（与无障碍互斥）
    var useRootCapture by remember { mutableStateOf(false) }
    var showRootDialog by remember { mutableStateOf(false) }

    // 隐藏多任务卡片
    var excludeFromRecents by remember { mutableStateOf(false) }

    // 截图触发时 Toast 提示
    var captureToastEnabled by remember { mutableStateOf(true) }

    // 实况通知标题跳转来源应用
    var sourceAppJumpEnabled by remember { mutableStateOf(false) }

    data class LlmPrefsDraft(
        val provider: LlmProvider,
        val apiKey: String,
        val model: String,
        val temperature: Float,
        val customBaseUrl: String,
        val extraParams: String,
    )

    var lastSavedDraft by remember { mutableStateOf<LlmPrefsDraft?>(null) }

    // 立即保存当前配置的函数（用于退出时和测试前）
    val saveCurrentPrefsImmediately: suspend () -> Unit = {
        if (!isHydratingProviderPrefs) {
            val draft =
                LlmPrefsDraft(
                    provider = selectedProvider,
                    apiKey = apiKey,
                    model = model,
                    temperature = temperature,
                    customBaseUrl = customBaseUrl,
                    extraParams = extraParams,
                )
            if (draft != lastSavedDraft) {
                dao.setLlmScopedPreference(Constants.PREF_LLM_API_KEY, draft.provider, draft.apiKey)
                dao.setLlmScopedPreference(
                    Constants.PREF_LLM_MODEL,
                    draft.provider,
                    draft.model.trim().ifBlank { draft.provider.defaultModel },
                )
                dao.setLlmScopedPreference(
                    Constants.PREF_LLM_TEMPERATURE,
                    draft.provider,
                    draft.temperature.toString(),
                )
                dao.setLlmScopedPreference(
                    Constants.PREF_LLM_CUSTOM_BASE_URL,
                    draft.provider,
                    draft.customBaseUrl.trim(),
                )
                dao.setLlmScopedPreference(
                    Constants.PREF_LLM_EXTRA_PARAMS,
                    draft.provider,
                    draft.extraParams.trim(),
                )
                lastSavedDraft = draft
            }
        }
    }

    // 在离开页面时强制保存配置
    DisposableEffect(Unit) {
        onDispose {
            if (!isHydratingProviderPrefs) {
                runBlocking {
                    saveCurrentPrefsImmediately()
                }
            }
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    accessibilityServiceEnabled = AccessibilityCaptureService.isServiceEnabled(context)
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val latestContext by rememberUpdatedState(context)
    val latestDao by rememberUpdatedState(dao)
    val sendTestLiveNotification: () -> Unit = {
        val timeText =
            android.text.format.DateFormat
                .format("HH:mm", System.currentTimeMillis())
                .toString()
        // 使用特殊的测试通知 ID（负数，避免与真实 extractId 冲突）
        val testExtractId = -System.currentTimeMillis()
        UnifiedNotificationManager(latestContext).showExtractNotification(
            title = "测试实况通知",
            content = "如果你看到了这条通知，说明通知发送正常。",
            timeText = timeText,
            extractId = testExtractId,
        )
        Toast.makeText(latestContext, "已发送测试通知", Toast.LENGTH_SHORT).show()
    }

    val postNotificationPermissionLauncher =
        androidx.activity.compose.rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
        ) { granted ->
            if (granted) {
                sendTestLiveNotification()
            } else {
                Toast.makeText(latestContext, "未授予通知权限，无法发送测试通知", Toast.LENGTH_SHORT).show()
            }
        }

    // 标记是否已完成首次初始化
    var hasInitialized by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val provider = LlmProvider.fromStoredValue(dao.getPreference(Constants.PREF_LLM_PROVIDER))
        dao.migrateLegacyLlmPreferencesToScoped(
            provider = provider,
            baseKeys =
                listOf(
                    Constants.PREF_LLM_API_KEY,
                    Constants.PREF_LLM_MODEL,
                    Constants.PREF_LLM_TEMPERATURE,
                    Constants.PREF_LLM_CUSTOM_BASE_URL,
                ),
        )
        selectedProvider = provider

        // 首次加载：直接在这里加载配置
        isHydratingProviderPrefs = true
        apiKey = dao.getLlmScopedPreference(Constants.PREF_LLM_API_KEY, provider) ?: ""
        model = dao.getLlmScopedPreference(Constants.PREF_LLM_MODEL, provider)
            ?: provider.defaultModel
        temperature = dao
            .getLlmScopedPreference(Constants.PREF_LLM_TEMPERATURE, provider)
            ?.toFloatOrNull()
            ?: 0.1f
        customBaseUrl = dao.getLlmScopedPreference(Constants.PREF_LLM_CUSTOM_BASE_URL, provider)
            ?: ""
        extraParams = dao.getLlmScopedPreference(Constants.PREF_LLM_EXTRA_PARAMS, provider) ?: ""
        lastSavedDraft =
            LlmPrefsDraft(
                provider = provider,
                apiKey = apiKey,
                model = model,
                temperature = temperature,
                customBaseUrl = customBaseUrl,
                extraParams = extraParams,
            )
        isHydratingProviderPrefs = false
        hasInitialized = true

        // 加载历史记录数量限制
        maxHistoryCount = dao
            .getPreference(Constants.PREF_MAX_HISTORY_COUNT)
            ?.toIntOrNull()
            ?.coerceIn(Constants.MIN_HISTORY_COUNT, Constants.MAX_HISTORY_COUNT)
            ?: Constants.DEFAULT_MAX_HISTORY_COUNT

        // 加载 LLM 请求超时时间
        timeoutSeconds = dao
            .getPreference(Constants.PREF_LLM_TIMEOUT_SECONDS)
            ?.toIntOrNull()
            ?.coerceIn(Constants.MIN_LLM_TIMEOUT_SECONDS, Constants.MAX_LLM_TIMEOUT_SECONDS)
            ?: Constants.DEFAULT_LLM_TIMEOUT_SECONDS

        // 加载自定义系统指令
        customSystemInstruction = dao
            .getPreference(Constants.PREF_CUSTOM_SYSTEM_INSTRUCTION)
            ?.takeIf { it.isNotBlank() }
            ?: Constants.DEFAULT_SYSTEM_INSTRUCTION

        // 加载无障碍截图模式设置
        useAccessibilityCapture = dao.getPreference(Constants.PREF_USE_ACCESSIBILITY_CAPTURE) == "true"
        accessibilityServiceEnabled = AccessibilityCaptureService.isServiceEnabled(context)

        // 加载 Root 截图模式设置（与无障碍互斥）
        useRootCapture = dao.getPreference(Constants.PREF_USE_ROOT_CAPTURE) == "true"
        if (useRootCapture && useAccessibilityCapture) {
            useAccessibilityCapture = false
            dao.setPreference(Constants.PREF_USE_ACCESSIBILITY_CAPTURE, "false")
        }

        // 加载隐藏多任务卡片设置
        excludeFromRecents = dao.getPreference(Constants.PREF_EXCLUDE_FROM_RECENTS) == "true"

        // 加载截图 Toast 提示设置
        captureToastEnabled = dao.getPreference(Constants.PREF_CAPTURE_TOAST_ENABLED) != "false"

        // 加载来源应用跳转设置
        sourceAppJumpEnabled = dao.getPreference(Constants.PREF_SOURCE_APP_JUMP_ENABLED) == "true"
        accessibilityServiceEnabled = AccessibilityCaptureService.isServiceEnabled(context)
    }

    LaunchedEffect(selectedProvider, hasInitialized) {
        // 只在初始化完成后且 provider 切换时才执行
        if (!hasInitialized) return@LaunchedEffect
        isHydratingProviderPrefs = true
        apiKey = dao.getLlmScopedPreference(Constants.PREF_LLM_API_KEY, selectedProvider) ?: ""
        model = dao.getLlmScopedPreference(Constants.PREF_LLM_MODEL, selectedProvider)
            ?: selectedProvider.defaultModel
        temperature = dao
            .getLlmScopedPreference(Constants.PREF_LLM_TEMPERATURE, selectedProvider)
            ?.toFloatOrNull()
            ?: 0.1f
        customBaseUrl = dao.getLlmScopedPreference(Constants.PREF_LLM_CUSTOM_BASE_URL, selectedProvider)
            ?: ""
        extraParams = dao.getLlmScopedPreference(Constants.PREF_LLM_EXTRA_PARAMS, selectedProvider)
            ?: ""
        lastSavedDraft =
            LlmPrefsDraft(
                provider = selectedProvider,
                apiKey = apiKey,
                model = model,
                temperature = temperature,
                customBaseUrl = customBaseUrl,
                extraParams = extraParams,
            )
        isHydratingProviderPrefs = false
    }

    LaunchedEffect(Unit) {
        snapshotFlow {
            isHydratingProviderPrefs to
                LlmPrefsDraft(
                    provider = selectedProvider,
                    apiKey = apiKey,
                    model = model,
                    temperature = temperature,
                    customBaseUrl = customBaseUrl,
                    extraParams = extraParams,
                )
        }.filter { (hydrating, _) -> !hydrating }
            .map { (_, draft) -> draft }
            .distinctUntilChanged()
            .debounce(500)
            .collectLatest { draft ->
                if (draft == lastSavedDraft) return@collectLatest

                latestDao.setLlmScopedPreference(Constants.PREF_LLM_API_KEY, draft.provider, draft.apiKey)
                latestDao.setLlmScopedPreference(
                    Constants.PREF_LLM_MODEL,
                    draft.provider,
                    draft.model.trim().ifBlank { draft.provider.defaultModel },
                )
                latestDao.setLlmScopedPreference(
                    Constants.PREF_LLM_TEMPERATURE,
                    draft.provider,
                    draft.temperature.toString(),
                )
                latestDao.setLlmScopedPreference(
                    Constants.PREF_LLM_CUSTOM_BASE_URL,
                    draft.provider,
                    draft.customBaseUrl.trim(),
                )
                latestDao.setLlmScopedPreference(
                    Constants.PREF_LLM_EXTRA_PARAMS,
                    draft.provider,
                    draft.extraParams.trim(),
                )
                lastSavedDraft = draft
            }
    }

    // Root 权限引导对话框
    OverlayDialog(
        show = showRootDialog,
        title = "需要 Root 权限",
        summary = "Root 截图模式需要设备已 root（如 Magisk）。首次触发截图时会弹出超级用户授权，请允许 PinMe 获取 root 权限。",
        onDismissRequest = { showRootDialog = false },
        content = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(text = "取消", onClick = { showRootDialog = false })
                TextButton(text = "知道了", onClick = { showRootDialog = false })
            }
        },
    )

    // 无障碍权限引导对话框
    OverlayDialog(
        show = showAccessibilityDialog,
        title = "需要无障碍权限",
        summary = "无障碍相关功能需要开启无障碍服务。请在设置中找到 PinMe 并开启无障碍权限。",
        onDismissRequest = { showAccessibilityDialog = false },
        content = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(text = "取消", onClick = { showAccessibilityDialog = false })
                TextButton(
                    text = "前往设置",
                    onClick = {
                        showAccessibilityDialog = false
                        AccessibilityCaptureService.openAccessibilitySettings(context)
                    },
                )
            }
        },
    )

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 12.dp),
    ) {
        // ==================== LLM 配置 ====================
        SmallTitle(text = "LLM 配置")
        Card(modifier = Modifier.padding(horizontal = 12.dp)) {
            Column(
                modifier = Modifier.padding(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OverlayDropdownPreference(
                    items = LlmProvider.entries.map { it.displayName },
                    selectedIndex = LlmProvider.entries.indexOf(selectedProvider).coerceAtLeast(0),
                    title = "LLM 供应商",
                    onSelectedIndexChange = { index ->
                        val provider = LlmProvider.entries.getOrNull(index) ?: return@OverlayDropdownPreference
                        isHydratingProviderPrefs = true
                        selectedProvider = provider
                        scope.launch {
                            dao.setPreference(
                                Constants.PREF_LLM_PROVIDER,
                                provider.toStoredValue(),
                            )
                        }
                    },
                )

                Column(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (selectedProvider == LlmProvider.CUSTOM) {
                            TextField(
                                value = customBaseUrl,
                                onValueChange = { customBaseUrl = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = "Base URL（以/v1结尾）",
                                singleLine = true,
                                keyboardOptions = nextKeyboardOptions,
                                keyboardActions = nextKeyboardActions,
                            )
                    }

                    TextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = "API Key",
                        singleLine = true,
                        visualTransformation =
                            if (apiKeyVisible) {
                                VisualTransformation.None
                            } else {
                                PasswordVisualTransformation()
                            },
                        keyboardOptions = nextKeyboardOptions,
                        keyboardActions = nextKeyboardActions,
                        trailingIcon = {
                            IconButton(
                                onClick = { apiKeyVisible = !apiKeyVisible },
                                modifier =
                                    Modifier
                                        .padding(end = 12.dp)
                                        .focusProperties { canFocus = false },
                            ) {
                                Icon(
                                    imageVector =
                                        if (apiKeyVisible) {
                                            Icons.Rounded.VisibilityOff
                                        } else {
                                            Icons.Rounded.Visibility
                                        },
                                    tint =
                                        if (apiKeyVisible) {
                                            MiuixTheme.colorScheme.primary
                                        } else {
                                            MiuixTheme.colorScheme.onSecondaryContainer
                                        },
                                    contentDescription =
                                        if (apiKeyVisible) {
                                            "隐藏 API Key"
                                        } else {
                                            "显示 API Key"
                                        },
                                )
                            }
                        },
                    )

                    TextField(
                        value = model,
                        onValueChange = { model = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = "视觉模型 ID",
                        singleLine = true,
                        keyboardOptions = nextKeyboardOptions,
                        keyboardActions = nextKeyboardActions,
                    )
                }

                SliderPreference(
                    value = temperature,
                    onValueChange = { temperature = it },
                    title = "温度",
                    valueText = "%.2f".format(temperature),
                    valueRange = 0f..2f,
                    steps = 19,
                    summary = "较低温度输出更确定，较高温度输出更多样",
                )

                val extraParamsIsValid =
                    extraParams.isBlank() || runCatching { JSONObject(extraParams) }.isSuccess
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    TextField(
                        value = extraParams,
                        onValueChange = { extraParams = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = "自定义推理参数",
                        minLines = 2,
                        maxLines = 4,
                    )
                    Text(
                        text =
                            if (extraParamsIsValid) {
                                "JSON 对象，追加到请求体，如 {\"top_p\": 0.9, \"max_tokens\": 1024}"
                            } else {
                                "格式错误：请输入合法的 JSON 对象"
                            },
                        style = MiuixTheme.textStyles.footnote1,
                        color =
                            if (extraParamsIsValid) {
                                MiuixTheme.colorScheme.onSurfaceVariantSummary
                            } else {
                                MiuixTheme.colorScheme.error
                            },
                    )
                }

                Button(
                    onClick = {
                        scope.launch {
                            isTesting = true
                            testResult = null
                            try {
                                saveCurrentPrefsImmediately()

                                val baseUrl =
                                    when (selectedProvider) {
                                        LlmProvider.CUSTOM -> {
                                            customBaseUrl.trim().takeIf { it.isNotBlank() }
                                                ?: throw IllegalStateException("请填写 Base URL")
                                        }

                                        else -> {
                                            selectedProvider.baseUrl
                                        }
                                    }
                                val testModel =
                                    model.trim().takeIf { it.isNotBlank() }
                                        ?: selectedProvider.defaultModel.takeIf { it.isNotBlank() }
                                        ?: throw IllegalStateException("请填写模型 ID")
                                val testImageBase64 = loadAppIconBase64(context)

                                val response =
                                    VllmClient(timeoutSeconds = timeoutSeconds.toLong()).testConnection(
                                        baseUrl = baseUrl,
                                        apiKey = apiKey.takeIf { it.isNotBlank() },
                                        model = testModel,
                                        imageBase64 = testImageBase64,
                                        extraParamsJson = extraParams.trim().takeIf { it.isNotBlank() },
                                    )
                                testResult = "连接成功: $response"
                                Toast.makeText(context, "测试成功", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                testResult = "连接失败: ${e.message}"
                                Toast.makeText(context, "测试失败", Toast.LENGTH_SHORT).show()
                            } finally {
                                isTesting = false
                            }
                        }
                    },
                    enabled = !isTesting,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) {
                    Text(if (isTesting) "测试中…" else "测试连接")
                }

                if (testResult != null) {
                    Text(
                        text = testResult!!,
                        style = MiuixTheme.textStyles.footnote1,
                        color =
                            if (testResult!!.startsWith("连接成功")) {
                                MiuixTheme.colorScheme.primary
                            } else {
                                MiuixTheme.colorScheme.error
                            },
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }

                Column(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    TextField(
                        value = customSystemInstruction,
                        onValueChange = { newValue ->
                            customSystemInstruction = newValue
                            scope.launch {
                                val trimmed = newValue.trim()
                                if (trimmed.isBlank() || trimmed == Constants.DEFAULT_SYSTEM_INSTRUCTION) {
                                    dao.setPreference(Constants.PREF_CUSTOM_SYSTEM_INSTRUCTION, "")
                                } else {
                                    dao.setPreference(Constants.PREF_CUSTOM_SYSTEM_INSTRUCTION, trimmed)
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = "系统指令（角色描述）",
                        minLines = 2,
                        maxLines = 4,
                    )
                    Text(
                        "可自定义顶层系统提示词，不填则使用默认",
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }

                // 恢复默认按钮，占据整行
                Button(
                    onClick = {
                        customSystemInstruction = Constants.DEFAULT_SYSTEM_INSTRUCTION
                        scope.launch {
                            dao.setPreference(Constants.PREF_CUSTOM_SYSTEM_INSTRUCTION, "")
                        }
                    },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                ) {
                    Text("恢复默认")
                }
            }
        }

        // ==================== 截图模式 ====================
        SmallTitle(text = "截图模式")
        Card(modifier = Modifier.padding(horizontal = 12.dp)) {
            Column(
                modifier = Modifier.padding(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SwitchPreference(
                    title = "Root 截图",
                    summary = "通过 su 静默截图（需要设备已 root）",
                    checked = useRootCapture,
                    onCheckedChange = { enabled ->
                        if (enabled) {
                            useRootCapture = true
                            useAccessibilityCapture = false
                            showRootDialog = true
                            scope.launch {
                                dao.setPreference(Constants.PREF_USE_ROOT_CAPTURE, "true")
                                dao.setPreference(Constants.PREF_USE_ACCESSIBILITY_CAPTURE, "false")
                            }
                        } else {
                            useRootCapture = false
                            scope.launch {
                                dao.setPreference(Constants.PREF_USE_ROOT_CAPTURE, "false")
                            }
                        }
                    },
                )

                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    if (useRootCapture) {
                        Text(
                            text =
                                if (RootCaptureService.isSuAvailable()) {
                                    "已检测到 su，首次使用会弹出 Root 授权"
                                } else {
                                    "未检测到 su，将自动使用传统截图方式"
                                },
                            style = MiuixTheme.textStyles.footnote1,
                            color =
                                if (RootCaptureService.isSuAvailable()) {
                                    MiuixTheme.colorScheme.primary
                                } else {
                                    MiuixTheme.colorScheme.error
                                },
                        )
                    }
                }

                SwitchPreference(
                    title = "无障碍截图模式",
                    summary = "开启后静默截图，无需每次授权",
                    checked = useAccessibilityCapture,
                    onCheckedChange = { enabled ->
                        if (enabled) {
                            accessibilityServiceEnabled = AccessibilityCaptureService.isServiceEnabled(context)
                            if (!accessibilityServiceEnabled) {
                                showAccessibilityDialog = true
                            } else {
                                useAccessibilityCapture = true
                                useRootCapture = false
                                showRootDialog = false
                                scope.launch {
                                    dao.setPreference(Constants.PREF_USE_ACCESSIBILITY_CAPTURE, "true")
                                    dao.setPreference(Constants.PREF_USE_ROOT_CAPTURE, "false")
                                }
                            }
                        } else {
                            useAccessibilityCapture = false
                            scope.launch {
                                dao.setPreference(Constants.PREF_USE_ACCESSIBILITY_CAPTURE, "false")
                            }
                        }
                    },
                )

                Column(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (useAccessibilityCapture) {
                        val currentServiceEnabled = AccessibilityCaptureService.isServiceEnabled(context)
                        if (!currentServiceEnabled) {
                            Text(
                                text = "无障碍服务未开启，将自动使用传统截图方式",
                                style = MiuixTheme.textStyles.footnote1,
                                color = MiuixTheme.colorScheme.error,
                            )
                            Button(
                                onClick = { AccessibilityCaptureService.openAccessibilitySettings(context) },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("前往无障碍设置")
                            }
                        } else {
                            Text(
                                text = "无障碍服务已开启，可静默截图",
                                style = MiuixTheme.textStyles.footnote1,
                                color = MiuixTheme.colorScheme.primary,
                            )
                        }
                    }

                    Text(
                        text = "提示：若未开启无障碍/Root 截图模式，首次点击磁贴需要授予截屏权限。",
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }
        }

        // ==================== 通知与快捷方式 ====================
        SmallTitle(text = "通知与快捷方式")
        Card(modifier = Modifier.padding(horizontal = 12.dp)) {
            val rootCaptureReady = useRootCapture && RootCaptureService.isSuAvailable()
            val canReadSourceFromScreenshot = accessibilityServiceEnabled || rootCaptureReady

            Column(
                modifier = Modifier.padding(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SwitchPreference(
                    title = "实况通知标题跳转来源应用",
                    summary = "分享来源可自动记录；截图来源需开启无障碍或 Root 截图",
                    checked = sourceAppJumpEnabled,
                    onCheckedChange = { enabled ->
                        sourceAppJumpEnabled = enabled
                        scope.launch {
                            dao.setPreference(Constants.PREF_SOURCE_APP_JUMP_ENABLED, enabled.toString())
                        }
                    },
                )

                if (sourceAppJumpEnabled && !canReadSourceFromScreenshot) {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = "当前未满足截图来源记录条件（无障碍或 Root 截图），截图记录无法获取来源应用（分享仍可）",
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.error,
                        )
                        Button(
                            onClick = {
                                AccessibilityCaptureService.openAccessibilitySettings(context)
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("前往无障碍设置（可选）")
                        }
                    }
                }

                Column(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Button(
                        onClick = {
                            if (Build.VERSION.SDK_INT >= 33) {
                                val granted =
                                    ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.POST_NOTIFICATIONS,
                                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                if (!granted) {
                                    postNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    return@Button
                                }
                            }
                            sendTestLiveNotification()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColorsPrimary(),
                    ) {
                        Text("发送测试实况通知")
                    }

                    Button(
                        onClick = {
                            if (!ShortcutManagerCompat.isRequestPinShortcutSupported(context)) {
                                Toast.makeText(context, "当前启动器不支持创建快捷方式", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            val shortcutIntent =
                                Intent(context, CaptureActivity::class.java).apply {
                                    action = Intent.ACTION_VIEW
                                }
                            val shortcutInfo =
                                ShortcutInfoCompat
                                    .Builder(context, "quick_capture_shortcut")
                                    .setShortLabel("截图识别")
                                    .setLongLabel("PinMe 截图识别")
                                    .setIcon(IconCompat.createWithResource(context, R.mipmap.ic_launcher))
                                    .setIntent(shortcutIntent)
                                    .build()
                            val success = ShortcutManagerCompat.requestPinShortcut(context, shortcutInfo, null)
                            if (success) {
                                Toast.makeText(context, "请在桌面上确认添加快捷方式", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "创建快捷方式失败", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("添加截图识别快捷方式到桌面")
                    }
                }
            }
        }

        // ==================== 通用设置 ====================
        SmallTitle(text = "通用设置")
        Card(modifier = Modifier.padding(horizontal = 12.dp)) {
            SliderPreference(
                value = maxHistoryCount.toFloat(),
                onValueChange = { newValue ->
                    maxHistoryCount = newValue.toInt()
                    scope.launch {
                        dao.setPreference(Constants.PREF_MAX_HISTORY_COUNT, newValue.toInt().toString())
                        dao.trimExtractsToLimit(newValue.toInt())
                    }
                },
                title = "最大历史记录数量",
                valueText = maxHistoryCount.toString(),
                valueRange = Constants.MIN_HISTORY_COUNT.toFloat()..Constants.MAX_HISTORY_COUNT.toFloat(),
                steps = Constants.MAX_HISTORY_COUNT - Constants.MIN_HISTORY_COUNT - 1,
                summary = "超出限制的旧记录会被自动删除",
            )

            SliderPreference(
                value = timeoutSeconds.toFloat(),
                onValueChange = { newValue ->
                    timeoutSeconds = newValue.toInt()
                    scope.launch {
                        dao.setPreference(Constants.PREF_LLM_TIMEOUT_SECONDS, timeoutSeconds.toString())
                    }
                },
                title = "请求超时时间",
                valueText = "${timeoutSeconds}s",
                valueRange =
                    Constants.MIN_LLM_TIMEOUT_SECONDS.toFloat()..Constants.MAX_LLM_TIMEOUT_SECONDS.toFloat(),
                steps = (Constants.MAX_LLM_TIMEOUT_SECONDS - Constants.MIN_LLM_TIMEOUT_SECONDS) / 30 - 1,
                summary = "所有 LLM 请求（测试连接、截图/文本提取）的超时上限",
            )

            SwitchPreference(
                title = "隐藏多任务卡片",
                summary = "从最近任务列表中隐藏本应用",
                checked = excludeFromRecents,
                onCheckedChange = { enabled ->
                    excludeFromRecents = enabled
                    scope.launch {
                        dao.setPreference(Constants.PREF_EXCLUDE_FROM_RECENTS, enabled.toString())
                    }
                },
            )

            SwitchPreference(
                title = "截图 Toast 提示",
                summary = "关闭后触发截图时不再弹出 Toast 提醒",
                checked = captureToastEnabled,
                onCheckedChange = { enabled ->
                    captureToastEnabled = enabled
                    scope.launch {
                        dao.setPreference(Constants.PREF_CAPTURE_TOAST_ENABLED, enabled.toString())
                    }
                },
            )

            ArrowPreference(
                title = "查看使用教程",
                summary = "了解 PinMe 的基本用法",
                onClick = onShowTutorial,
            )

            BasicComponent(
                title = "版本",
                summary = BuildConfig.VERSION_NAME,
            )

            BasicComponent(
                title = "作者",
                summary = "BryceWG",
            )

            ArrowPreference(
                title = "项目地址",
                summary = "GitHub",
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/BryceWG/Pinme"))
                    context.startActivity(intent)
                },
            )

            ArrowPreference(
                title = "作者其他项目",
                summary = "说点啥 AI语音输入工具",
                onClick = {
                    val intent =
                        Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("https://github.com/BryceWG/BiBi-Keyboard"),
                        )
                    context.startActivity(intent)
                },
            )
        }
    }
}

private fun loadAppIconBase64(context: Context): String {
    val drawable = context.applicationInfo.loadIcon(context.packageManager)
    val size = 128
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    drawable.setBounds(0, 0, size, size)
    drawable.draw(canvas)
    val stream = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
    return Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
}

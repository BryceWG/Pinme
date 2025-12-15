package com.brycewg.pinme.ui.layouts

import android.Manifest
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.brycewg.pinme.R
import com.brycewg.pinme.capture.CaptureActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.brycewg.pinme.Constants
import com.brycewg.pinme.Constants.LlmProvider
import com.brycewg.pinme.db.DatabaseProvider
import com.brycewg.pinme.notification.UnifiedNotificationManager
import com.brycewg.pinme.vllm.VllmClient
import com.brycewg.pinme.vllm.getLlmScopedPreference
import com.brycewg.pinme.vllm.migrateLegacyLlmPreferencesToScoped
import com.brycewg.pinme.vllm.setLlmScopedPreference
import com.brycewg.pinme.vllm.toStoredValue
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map

@Composable
fun AppSettings() {
    val context = LocalContext.current
    val dao = DatabaseProvider.dao()
    val scope = rememberCoroutineScope()

    var selectedProvider by remember { mutableStateOf(LlmProvider.ZHIPU) }
    var apiKey by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var temperature by remember { mutableFloatStateOf(0.1f) }
    var customBaseUrl by remember { mutableStateOf("") }
    var isTesting by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var showProviderDialog by remember { mutableStateOf(false) }
    var isHydratingProviderPrefs by remember { mutableStateOf(true) }
    var maxHistoryCount by remember { mutableStateOf(Constants.DEFAULT_MAX_HISTORY_COUNT) }

    data class LlmPrefsDraft(
        val provider: LlmProvider,
        val apiKey: String,
        val model: String,
        val temperature: Float,
        val customBaseUrl: String
    )

    var lastSavedDraft by remember { mutableStateOf<LlmPrefsDraft?>(null) }

    // 立即保存当前配置的函数（用于退出时和测试前）
    val saveCurrentPrefsImmediately: suspend () -> Unit = {
        if (!isHydratingProviderPrefs) {
            val draft = LlmPrefsDraft(
                provider = selectedProvider,
                apiKey = apiKey,
                model = model,
                temperature = temperature,
                customBaseUrl = customBaseUrl
            )
            if (draft != lastSavedDraft) {
                dao.setLlmScopedPreference(Constants.PREF_LLM_API_KEY, draft.provider, draft.apiKey)
                dao.setLlmScopedPreference(
                    Constants.PREF_LLM_MODEL,
                    draft.provider,
                    draft.model.trim().ifBlank { draft.provider.defaultModel }
                )
                dao.setLlmScopedPreference(
                    Constants.PREF_LLM_TEMPERATURE,
                    draft.provider,
                    draft.temperature.toString()
                )
                dao.setLlmScopedPreference(
                    Constants.PREF_LLM_CUSTOM_BASE_URL,
                    draft.provider,
                    draft.customBaseUrl.trim()
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

    val latestContext by rememberUpdatedState(context)
    val latestDao by rememberUpdatedState(dao)
    val sendTestLiveNotification: () -> Unit = {
        val timeText =
            android.text.format.DateFormat.format("HH:mm", System.currentTimeMillis()).toString()
        UnifiedNotificationManager(latestContext).showExtractNotification(
            title = "测试实况通知",
            content = "如果你看到了这条通知，说明通知发送正常。",
            timeText = timeText
        )
        Toast.makeText(latestContext, "已发送测试通知", Toast.LENGTH_SHORT).show()
    }

    val postNotificationPermissionLauncher =
        androidx.activity.compose.rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) {
                sendTestLiveNotification()
            } else {
                Toast.makeText(latestContext, "未授予通知权限，无法发送测试通知", Toast.LENGTH_SHORT).show()
            }
        }

    LaunchedEffect(Unit) {
        val provider = LlmProvider.fromStoredValue(dao.getPreference(Constants.PREF_LLM_PROVIDER))
        dao.migrateLegacyLlmPreferencesToScoped(
            provider = provider,
            baseKeys = listOf(
                Constants.PREF_LLM_API_KEY,
                Constants.PREF_LLM_MODEL,
                Constants.PREF_LLM_TEMPERATURE,
                Constants.PREF_LLM_CUSTOM_BASE_URL
            )
        )
        isHydratingProviderPrefs = true
        selectedProvider = provider

        // 加载历史记录数量限制
        maxHistoryCount = dao.getPreference(Constants.PREF_MAX_HISTORY_COUNT)
            ?.toIntOrNull()
            ?.coerceIn(1, 20)
            ?: Constants.DEFAULT_MAX_HISTORY_COUNT
    }

    LaunchedEffect(selectedProvider) {
        isHydratingProviderPrefs = true
        apiKey = dao.getLlmScopedPreference(Constants.PREF_LLM_API_KEY, selectedProvider) ?: ""
        model = dao.getLlmScopedPreference(Constants.PREF_LLM_MODEL, selectedProvider)
            ?: selectedProvider.defaultModel
        temperature = dao.getLlmScopedPreference(Constants.PREF_LLM_TEMPERATURE, selectedProvider)
            ?.toFloatOrNull()
            ?: 0.1f
        customBaseUrl = dao.getLlmScopedPreference(Constants.PREF_LLM_CUSTOM_BASE_URL, selectedProvider)
            ?: ""
        lastSavedDraft = LlmPrefsDraft(
            provider = selectedProvider,
            apiKey = apiKey,
            model = model,
            temperature = temperature,
            customBaseUrl = customBaseUrl
        )
        isHydratingProviderPrefs = false
    }

    LaunchedEffect(Unit) {
        snapshotFlow {
            isHydratingProviderPrefs to LlmPrefsDraft(
                provider = selectedProvider,
                apiKey = apiKey,
                model = model,
                temperature = temperature,
                customBaseUrl = customBaseUrl
            )
        }
            .filter { (hydrating, _) -> !hydrating }
            .map { (_, draft) -> draft }
            .distinctUntilChanged()
            .debounce(500)
            .collectLatest { draft ->
                if (draft == lastSavedDraft) return@collectLatest

                latestDao.setLlmScopedPreference(Constants.PREF_LLM_API_KEY, draft.provider, draft.apiKey)
                latestDao.setLlmScopedPreference(
                    Constants.PREF_LLM_MODEL,
                    draft.provider,
                    draft.model.trim().ifBlank { draft.provider.defaultModel }
                )
                latestDao.setLlmScopedPreference(
                    Constants.PREF_LLM_TEMPERATURE,
                    draft.provider,
                    draft.temperature.toString()
                )
                latestDao.setLlmScopedPreference(
                    Constants.PREF_LLM_CUSTOM_BASE_URL,
                    draft.provider,
                    draft.customBaseUrl.trim()
                )
                lastSavedDraft = draft
            }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        val textFieldShape = RoundedCornerShape(16.dp)

        Text("LLM 供应商", style = MaterialTheme.typography.titleMedium)

        OutlinedButton(
            onClick = { showProviderDialog = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(selectedProvider.displayName)
        }

        if (showProviderDialog) {
            AlertDialog(
                onDismissRequest = { showProviderDialog = false },
                title = { Text("选择 LLM 供应商") },
                text = {
                    Column(Modifier.selectableGroup()) {
                        LlmProvider.entries.forEach { provider ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .selectable(
                                        selected = (selectedProvider == provider),
                                        onClick = {
                                            isHydratingProviderPrefs = true
                                            selectedProvider = provider
                                            scope.launch {
                                                dao.setPreference(
                                                    Constants.PREF_LLM_PROVIDER,
                                                    provider.toStoredValue()
                                                )
                                            }
                                            showProviderDialog = false
                                        },
                                        role = Role.RadioButton
                                    )
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = (selectedProvider == provider),
                                    onClick = null
                                )
                                Text(
                                    text = provider.displayName,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showProviderDialog = false }) {
                        Text("取消")
                    }
                }
            )
        }

        if (selectedProvider == LlmProvider.CUSTOM) {
            OutlinedTextField(
                value = customBaseUrl,
                onValueChange = { customBaseUrl = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Base URL") },
                supportingText = { Text("输入到 /v1 即可，例如 https://api.example.com/v1") },
                shape = textFieldShape,
                singleLine = true
            )
        }

        OutlinedTextField(
            value = apiKey,
            onValueChange = { apiKey = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("API Key") },
            shape = textFieldShape,
            singleLine = true
        )

        OutlinedTextField(
            value = model,
            onValueChange = { model = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("模型 ID") },
            supportingText = {
                when (selectedProvider) {
                    LlmProvider.ZHIPU -> Text("例如 glm-4v-flash、glm-4v-plus")
                    LlmProvider.SILICONFLOW -> Text("例如 Qwen/Qwen2.5-VL-72B-Instruct")
                    LlmProvider.CUSTOM -> Text("根据你的服务填写模型名称")
                }
            },
            shape = textFieldShape,
            singleLine = true
        )

        Text(
            text = "温度: %.2f".format(temperature),
            style = MaterialTheme.typography.bodyMedium
        )
        Slider(
            value = temperature,
            onValueChange = { temperature = it },
            valueRange = 0f..2f,
            steps = 19,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = "较低温度输出更确定，较高温度输出更多样",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(4.dp))

        Button(
            onClick = {
                scope.launch {
                    isTesting = true
                    testResult = null
                    try {
                        // 测试前先保存当前配置
                        saveCurrentPrefsImmediately()

                        val baseUrl = when (selectedProvider) {
                            LlmProvider.CUSTOM -> customBaseUrl.trim().takeIf { it.isNotBlank() }
                                ?: throw IllegalStateException("请填写 Base URL")
                            else -> selectedProvider.baseUrl
                        }
                        val testModel = model.trim().takeIf { it.isNotBlank() }
                            ?: selectedProvider.defaultModel.takeIf { it.isNotBlank() }
                            ?: throw IllegalStateException("请填写模型 ID")

                        val response = VllmClient().testConnection(
                            baseUrl = baseUrl,
                            apiKey = apiKey.takeIf { it.isNotBlank() },
                            model = testModel
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
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isTesting) "测试中…" else "测试连接")
        }

        if (testResult != null) {
            Text(
                text = testResult!!,
                style = MaterialTheme.typography.bodySmall,
                color = if (testResult!!.startsWith("连接成功"))
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.error
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text("通知测试", style = MaterialTheme.typography.titleMedium)

        Button(
            onClick = {
                if (Build.VERSION.SDK_INT >= 33) {
                    val granted = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                    if (!granted) {
                        postNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        return@Button
                    }
                }
                sendTestLiveNotification()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("发送测试实况通知")
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text("快捷方式", style = MaterialTheme.typography.titleMedium)

        Button(
            onClick = {
                if (!ShortcutManagerCompat.isRequestPinShortcutSupported(context)) {
                    Toast.makeText(context, "当前启动器不支持创建快捷方式", Toast.LENGTH_SHORT).show()
                    return@Button
                }
                val shortcutIntent = Intent(context, CaptureActivity::class.java).apply {
                    action = Intent.ACTION_VIEW
                }
                val shortcutInfo = ShortcutInfoCompat.Builder(context, "quick_capture_shortcut")
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
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("添加截图识别快捷方式到桌面")
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text("历史记录", style = MaterialTheme.typography.titleMedium)

        Text(
            text = "最大历史记录数量: $maxHistoryCount",
            style = MaterialTheme.typography.bodyMedium
        )
        Slider(
            value = maxHistoryCount.toFloat(),
            onValueChange = { newValue ->
                maxHistoryCount = newValue.toInt()
                scope.launch {
                    dao.setPreference(Constants.PREF_MAX_HISTORY_COUNT, newValue.toInt().toString())
                    dao.trimExtractsToLimit(newValue.toInt())
                }
            },
            valueRange = 1f..20f,
            steps = 18,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = "超出限制的旧记录会被自动删除",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text(
            text = "提示：首次点击磁贴需要授予截屏权限。识别结果会写入历史记录；下一步会同步到 Flyme 实况通知与桌面插件。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

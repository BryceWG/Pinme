package com.brycewg.pinme

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.brycewg.pinme.capture.AccessibilityCaptureService
import com.brycewg.pinme.db.DatabaseProvider
import com.brycewg.pinme.db.ExtractEntity
import com.brycewg.pinme.extract.ExtractWorkflow
import com.brycewg.pinme.notification.UnifiedNotificationManager
import com.brycewg.pinme.qrcode.QrCodeDetector
import com.brycewg.pinme.ui.components.AddRecordActions
import com.brycewg.pinme.ui.components.LocalAddRecordActions
import com.brycewg.pinme.ui.layouts.AppSettings
import com.brycewg.pinme.ui.layouts.ExtractHome
import com.brycewg.pinme.ui.layouts.MarketScreen
import com.brycewg.pinme.ui.theme.StarScheduleTheme
import com.brycewg.pinme.widget.PinMeWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : ComponentActivity() {

    // 相机拍照临时文件 URI
    private var pendingCameraUri: Uri? = null

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        // 无论用户是否授权，都继续运行应用
        // 用户可以稍后在设置中手动授权
    }

    // 图库选择器
    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let { processImageUri(it, "gallery") }
    }

    // 相机拍摄器
    private val takePictureLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            pendingCameraUri?.let { processImageUri(it, "camera") }
        }
        pendingCameraUri = null
    }

    // 相机权限请求
    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            launchCamera()
        } else {
            Toast.makeText(this, "需要相机权限才能拍照", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DatabaseProvider.init(this)
        enableEdgeToEdge()

        // App 进程重启后，桌面小组件可能回到 initialLayout（“加载中...”），这里主动触发一次刷新
        lifecycleScope.launch(Dispatchers.IO) {
            PinMeWidget.updateWidgetContent(applicationContext)
        }

        // 请求通知权限 (Android 13+)
        requestNotificationPermissionIfNeeded()

        // 检查无障碍截图模式是否需要引导用户开启无障碍服务
        checkAccessibilityServiceIfNeeded()

        setContent {
            val addRecordActions = remember {
                AddRecordActions(
                    onManualAdd = { title, content, emoji ->
                        saveManualRecord(title, content, emoji)
                    },
                    onPickImage = {
                        launchImagePicker()
                    },
                    onTakePhoto = {
                        requestCameraPermissionAndLaunch()
                    }
                )
            }

            StarScheduleTheme {
                CompositionLocalProvider(LocalAddRecordActions provides addRecordActions) {
                    AppRoot()
                }
            }
        }
    }

    // 启动图库选择器
    private fun launchImagePicker() {
        pickImageLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    // 请求相机权限并启动相机
    private fun requestCameraPermissionAndLaunch() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            launchCamera()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // 启动相机
    private fun launchCamera() {
        val photoFile = File(cacheDir, "photos").apply { mkdirs() }
            .let { File(it, "capture_${System.currentTimeMillis()}.jpg") }

        pendingCameraUri = FileProvider.getUriForFile(
            this,
            "${packageName}.fileprovider",
            photoFile
        )

        takePictureLauncher.launch(pendingCameraUri!!)
    }

    // 处理选中的图片 URI
    private fun processImageUri(uri: Uri, source: String) {
        lifecycleScope.launch {
            Toast.makeText(this@MainActivity, "正在识别图片...", Toast.LENGTH_SHORT).show()

            try {
                val bitmap = withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(uri)?.use { stream ->
                        BitmapFactory.decodeStream(stream)
                    } ?: throw IllegalStateException("无法读取图片")
                }

                // 并行执行二维码检测和 LLM 识别
                val (qrResult, extract) = coroutineScope {
                    val qrDeferred = async { QrCodeDetector.detect(bitmap) }
                    val extractDeferred = async {
                        ExtractWorkflow(this@MainActivity).processScreenshot(bitmap)
                    }
                    qrDeferred.await() to extractDeferred.await()
                }

                // 如果检测到二维码，保存到数据库
                if (qrResult != null) {
                    val qrBase64 = withContext(Dispatchers.IO) {
                        qrResult.croppedBitmap.toJpegBase64()
                    }
                    DatabaseProvider.dao().updateExtractQrCode(extract.id, qrBase64)
                }

                // 获取市场类型配置用于通知
                val marketItem = withContext(Dispatchers.IO) {
                    DatabaseProvider.dao().getEnabledMarketItems()
                        .find { it.title == extract.title }
                }

                // 显示通知
                val notificationManager = UnifiedNotificationManager(this@MainActivity)
                notificationManager.showExtractNotification(
                    title = extract.title,
                    content = extract.content,
                    capsuleColor = marketItem?.capsuleColor,
                    emoji = extract.emoji ?: marketItem?.emoji,
                    qrBitmap = qrResult?.croppedBitmap,
                    extractId = extract.id
                )

                // 更新小组件
                PinMeWidget.updateWidgetContent(this@MainActivity)

                val qrInfo = if (qrResult != null) " [含二维码]" else ""
                Toast.makeText(
                    this@MainActivity,
                    "${extract.title}: ${extract.content}$qrInfo",
                    Toast.LENGTH_SHORT
                ).show()

                bitmap.recycle()
            } catch (e: Exception) {
                Toast.makeText(
                    this@MainActivity,
                    "识别失败：${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    // 保存手动添加的记录
    private fun saveManualRecord(title: String, content: String, emoji: String?) {
        lifecycleScope.launch {
            try {
                val entity = ExtractEntity(
                    title = title,
                    content = content,
                    emoji = emoji,
                    source = "manual",
                    rawModelOutput = "",
                    createdAtMillis = System.currentTimeMillis()
                )
                val id = withContext(Dispatchers.IO) {
                    DatabaseProvider.dao().insertExtract(entity)
                }

                // 更新小组件
                PinMeWidget.updateWidgetContent(this@MainActivity)

                Toast.makeText(this@MainActivity, "已添加记录", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(
                    this@MainActivity,
                    "添加失败：${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permission = Manifest.permission.POST_NOTIFICATIONS
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(permission)
            }
        }
    }

    private fun checkAccessibilityServiceIfNeeded() {
        lifecycleScope.launch {
            val useAccessibilityCapture = withContext(Dispatchers.IO) {
                val dao = DatabaseProvider.dao()
                dao.getPreference(Constants.PREF_USE_ACCESSIBILITY_CAPTURE)?.toBoolean() ?: false
            }

            // 如果开启了无障碍截图模式，但无障碍服务未启用，则跳转到设置页面
            if (useAccessibilityCapture && !AccessibilityCaptureService.isServiceEnabled(this@MainActivity)) {
                AccessibilityCaptureService.openAccessibilitySettings(this@MainActivity)
            }
        }
    }

    /**
     * 将 Bitmap 转换为 JPEG 格式的 Base64 字符串
     */
    private fun Bitmap.toJpegBase64(): String {
        val stream = java.io.ByteArrayOutputStream()
        compress(Bitmap.CompressFormat.JPEG, 85, stream)
        val bytes = stream.toByteArray()
        return android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun AppRoot() {
    var selected by remember { mutableIntStateOf(0) }
    val title = when (selected) {
        0 -> "PinMe"
        1 -> "市场"
        else -> "设置"
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(title) }) },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selected == 0,
                    onClick = { selected = 0 },
                    icon = { Icon(Icons.Rounded.History, contentDescription = null) },
                    label = { Text("记录") }
                )
                NavigationBarItem(
                    selected = selected == 1,
                    onClick = { selected = 1 },
                    icon = { Icon(Icons.Rounded.Storefront, contentDescription = null) },
                    label = { Text("市场") }
                )
                NavigationBarItem(
                    selected = selected == 2,
                    onClick = { selected = 2 },
                    icon = { Icon(Icons.Rounded.Settings, contentDescription = null) },
                    label = { Text("设置") }
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (selected) {
                0 -> ExtractHome()
                1 -> MarketScreen()
                else -> AppSettings()
            }
        }
    }
}

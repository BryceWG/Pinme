package com.brycewg.pinme.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.toColorInt
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.brycewg.pinme.db.DatabaseProvider
import com.brycewg.pinme.db.MarketItemEntity
import com.brycewg.pinme.notification.UnifiedNotificationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.ui.graphics.Color as ComposeColor

private const val TAG = "PinMeWidget"
private const val DEFAULT_CAPSULE_COLOR = "#FF9800"

private val KEY_EXTRACTS_JSON = stringPreferencesKey("extracts_json")
private val KEY_UPDATE_TIME = stringPreferencesKey("update_time")

private val jsonParser = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    encodeDefaults = true
}

@Serializable
data class WidgetExtractData(
    val items: List<WidgetExtractItem>,
    val updateTime: String
)

@Serializable
data class WidgetExtractItem(
    val id: Long,
    val title: String,
    val content: String,
    val emoji: String? = null,
    val qrCodeBase64: String? = null,
    val sourcePackage: String? = null,
    val capsuleColor: String? = null,
    val createdAtMillis: Long
)

// ActionParameters keys for pin action
private val PARAM_EXTRACT_ID = ActionParameters.Key<Long>("extract_id")
private val PARAM_TITLE = ActionParameters.Key<String>("title")
private val PARAM_CONTENT = ActionParameters.Key<String>("content")
private val PARAM_EMOJI = ActionParameters.Key<String>("emoji")
private val PARAM_QR_CODE_BASE64 = ActionParameters.Key<String>("qr_code_base64")
private val PARAM_CAPSULE_COLOR = ActionParameters.Key<String>("capsule_color")
private val PARAM_CREATED_AT = ActionParameters.Key<Long>("created_at")
private val PARAM_SOURCE_PACKAGE = ActionParameters.Key<String>("source_package")

/**
 * Build ActionParameters for pin action
 */
private fun buildPinActionParameters(item: WidgetExtractItem): ActionParameters {
    val params = mutableListOf<ActionParameters.Pair<out Any>>(
        PARAM_EXTRACT_ID to item.id,
        PARAM_TITLE to item.title,
        PARAM_CONTENT to item.content,
        PARAM_CREATED_AT to item.createdAtMillis
    )

    item.emoji?.let { params.add(PARAM_EMOJI to it) }
    item.qrCodeBase64?.let { params.add(PARAM_QR_CODE_BASE64 to it) }
    item.capsuleColor?.let { params.add(PARAM_CAPSULE_COLOR to it) }
    item.sourcePackage?.let { params.add(PARAM_SOURCE_PACKAGE to it) }

    return actionParametersOf(*params.toTypedArray())
}

/**
 * ActionCallback to handle pin-to-notification button click
 */
class PinToNotificationAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val extractId = parameters[PARAM_EXTRACT_ID] ?: return
        val title = parameters[PARAM_TITLE] ?: return
        val content = parameters[PARAM_CONTENT] ?: return
        val emoji = parameters[PARAM_EMOJI]
        val qrCodeBase64 = parameters[PARAM_QR_CODE_BASE64]
        val capsuleColor = parameters[PARAM_CAPSULE_COLOR]
        val createdAt = parameters[PARAM_CREATED_AT] ?: System.currentTimeMillis()
        val sourcePackage = parameters[PARAM_SOURCE_PACKAGE]

        val timeText = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(createdAt))

        val qrBitmap = qrCodeBase64?.let { base64 ->
            try {
                val bytes = Base64.decode(base64, Base64.NO_WRAP)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to decode QR code", e)
                null
            }
        }

        val notificationManager = UnifiedNotificationManager(context)
        val isLive = notificationManager.isLiveCapsuleCustomizationAvailable()

        notificationManager.showExtractNotification(
            title = title,
            content = content,
            timeText = timeText,
            capsuleColor = capsuleColor,
            emoji = emoji,
            qrBitmap = qrBitmap,
            extractId = extractId,
            sourcePackage = sourcePackage
        )

        CoroutineScope(Dispatchers.Main).launch {
            val toastText = if (isLive) "已挂到实况通知" else "已发送通知"
            Toast.makeText(context, toastText, Toast.LENGTH_SHORT).show()
        }
    }
}

class PinMeWidget : GlanceAppWidget() {
    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // 在 provideContent 之前先确保数据已加载
        val data = loadDataDirectly(context)

        provideContent {
            GlanceTheme {
                Content(data)
            }
        }
    }

    /**
     * 直接从数据库加载数据，不依赖 preferences state
     */
    private suspend fun loadDataDirectly(context: Context): WidgetExtractData {
        return try {
            if (!DatabaseProvider.isInitialized()) {
                DatabaseProvider.init(context.applicationContext)
            }

            val dao = DatabaseProvider.dao()
            val extracts = dao.getLatestExtractsOnce(10)
            val marketItems = dao.getEnabledMarketItems()

            val items = extracts.map { extract ->
                val matchedItem = findMatchedMarketItem(extract.title, marketItems)
                WidgetExtractItem(
                    id = extract.id,
                    title = extract.title,
                    content = extract.content,
                    emoji = extract.emoji ?: matchedItem?.emoji,
                    qrCodeBase64 = extract.qrCodeBase64,
                    sourcePackage = extract.sourcePackage,
                    capsuleColor = matchedItem?.capsuleColor,
                    createdAtMillis = extract.createdAtMillis
                )
            }
            val updateTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            WidgetExtractData(items = items, updateTime = updateTime)
        } catch (e: Exception) {
            Log.e(TAG, "loadDataDirectly failed", e)
            WidgetExtractData(items = emptyList(), updateTime = "")
        }
    }

    @Composable
    private fun Content(data: WidgetExtractData) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(ComposeColor(0xFFF5F5F5))
                .cornerRadius(16.dp)
                .padding(12.dp),
            horizontalAlignment = Alignment.Horizontal.Start,
            verticalAlignment = Alignment.Vertical.Top
        ) {
            // Header row
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.Vertical.CenterVertically
            ) {
                Text(
                    text = "📌 PinMe",
                    style = TextStyle(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = ColorProvider(ComposeColor(0xFF333333))
                    )
                )
                Spacer(modifier = GlanceModifier.defaultWeight())
                if (data.updateTime.isNotBlank()) {
                    Text(
                        text = data.updateTime,
                        style = TextStyle(
                            fontSize = 11.sp,
                            color = ColorProvider(ComposeColor(0xFF888888))
                        )
                    )
                }
            }

            Spacer(modifier = GlanceModifier.height(8.dp))

            if (data.items.isEmpty()) {
                Box(
                    modifier = GlanceModifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.Horizontal.CenterHorizontally) {
                        Text(
                            text = "暂无识别内容",
                            style = TextStyle(
                                fontSize = 14.sp,
                                color = ColorProvider(ComposeColor(0xFF666666))
                            )
                        )
                        Spacer(modifier = GlanceModifier.height(4.dp))
                        Text(
                            text = "点击控制中心磁贴开始",
                            style = TextStyle(
                                fontSize = 12.sp,
                                color = ColorProvider(ComposeColor(0xFF999999))
                            )
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = GlanceModifier.fillMaxSize()
                ) {
                    items(data.items, itemId = { it.id }) { item ->
                        Column {
                            ExtractItemRow(item)
                            Spacer(modifier = GlanceModifier.height(6.dp))
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun ExtractItemRow(item: WidgetExtractItem) {
        val buttonColor = try {
            val baseColor = ComposeColor((item.capsuleColor ?: DEFAULT_CAPSULE_COLOR).toColorInt())
            ComposeColor(
                red = baseColor.red * 0.4f + 0.6f,
                green = baseColor.green * 0.4f + 0.6f,
                blue = baseColor.blue * 0.4f + 0.6f,
                alpha = 1f
            )
        } catch (e: Exception) {
            ComposeColor(0xFFFFE0B2)
        }

        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .background(ComposeColor.White)
                .cornerRadius(10.dp)
                .padding(8.dp),
            verticalAlignment = Alignment.Vertical.CenterVertically
        ) {
            if (item.emoji != null) {
                Text(
                    text = item.emoji,
                    style = TextStyle(fontSize = 20.sp)
                )
                Spacer(modifier = GlanceModifier.width(8.dp))
            }

            Column(
                modifier = GlanceModifier.defaultWeight()
            ) {
                Text(
                    text = item.title,
                    style = TextStyle(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = ColorProvider(ComposeColor(0xFF666666))
                    ),
                    maxLines = 1
                )
                Text(
                    text = item.content,
                    style = TextStyle(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = ColorProvider(ComposeColor(0xFF333333))
                    ),
                    maxLines = 1
                )
            }

            Spacer(modifier = GlanceModifier.width(6.dp))

            Box(
                modifier = GlanceModifier
                    .size(32.dp)
                    .background(buttonColor)
                    .cornerRadius(8.dp)
                    .clickable(
                        onClick = actionRunCallback<PinToNotificationAction>(
                            parameters = buildPinActionParameters(item)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "📌",
                    style = TextStyle(
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center
                    )
                )
            }
        }
    }

    companion object {
        suspend fun updateWidgetContent(context: Context) {
            try {
                val appContext = context.applicationContext
                val appWidgetIds = AppWidgetManager.getInstance(appContext).getAppWidgetIds(
                    ComponentName(appContext, PinMeWidgetReceiver::class.java)
                )
                if (appWidgetIds.isEmpty()) return

                // 直接触发所有小组件更新，让 provideGlance 重新加载数据
                PinMeWidget().updateAll(appContext)
            } catch (e: Exception) {
                Log.e(TAG, "updateWidgetContent failed", e)
            }
        }

        private fun findMatchedMarketItem(title: String, marketItems: List<MarketItemEntity>): MarketItemEntity? {
            val exactMatch = marketItems.find { it.title == title }
            if (exactMatch != null) return exactMatch
            return marketItems.find {
                title.contains(it.title) || it.title.contains(title)
            }
        }
    }
}

class PinMeWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PinMeWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
    }
}

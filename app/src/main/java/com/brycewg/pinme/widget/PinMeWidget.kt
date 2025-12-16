package com.brycewg.pinme.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.glance.state.PreferencesGlanceStateDefinition
import com.brycewg.pinme.db.DatabaseProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TAG = "PinMeWidget"

private val KEY_EXTRACTS_JSON = stringPreferencesKey("extracts_json")
private val KEY_UPDATE_TIME = stringPreferencesKey("update_time")

private val jsonParser = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    encodeDefaults = false
}

@Serializable
data class WidgetExtractData(
    val items: List<WidgetExtractItem>,
    val updateTime: String
)

@Serializable
data class WidgetExtractItem(
    val title: String,
    val content: String,
    val emoji: String? = null
)

class PinMeWidget : GlanceAppWidget() {
    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        try {
            if (!DatabaseProvider.isInitialized()) {
                DatabaseProvider.init(context)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Database init failed", e)
        }

        provideContent {
            Content()
        }
    }

    @Composable
    private fun Content() {
        val prefs = currentState<Preferences>()
        val extractsJson = prefs[KEY_EXTRACTS_JSON]
        val lastUpdate = prefs[KEY_UPDATE_TIME] ?: ""

        val data = try {
            if (extractsJson.isNullOrBlank()) {
                WidgetExtractData(items = emptyList(), updateTime = lastUpdate)
            } else {
                jsonParser.decodeFromString(WidgetExtractData.serializer(), extractsJson)
            }
        } catch (e: Exception) {
            Log.e(TAG, "JSON parse failed", e)
            WidgetExtractData(items = emptyList(), updateTime = lastUpdate)
        }

        Column(
            modifier = GlanceModifier.fillMaxSize().padding(12.dp),
            horizontalAlignment = Alignment.Horizontal.Start,
            verticalAlignment = Alignment.Vertical.Top
        ) {
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                Text(
                    text = "PinMe",
                    style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold)
                )
            }
            if (data.updateTime.isNotBlank()) {
                Spacer(modifier = GlanceModifier.height(2.dp))
                Text(
                    text = data.updateTime,
                    style = TextStyle(fontSize = 11.sp, color = ColorProvider(android.R.color.darker_gray))
                )
            }

            Spacer(modifier = GlanceModifier.height(8.dp))

            if (data.items.isEmpty()) {
                Text(
                    text = "暂无识别内容",
                    style = TextStyle(fontSize = 14.sp, color = ColorProvider(android.R.color.darker_gray))
                )
                Spacer(modifier = GlanceModifier.height(4.dp))
                Text(
                    text = "点一下控制中心磁贴开始",
                    style = TextStyle(fontSize = 12.sp, color = ColorProvider(android.R.color.darker_gray))
                )
            } else {
                data.items.take(3).forEachIndexed { index, item ->
                    if (index > 0) Spacer(modifier = GlanceModifier.height(8.dp))
                    // 标题行：emoji + 标题
                    val titleText = if (item.emoji != null) "${item.emoji} ${item.title}" else item.title
                    Text(text = titleText, style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium))
                    Spacer(modifier = GlanceModifier.height(2.dp))
                    Text(text = item.content, style = TextStyle(fontSize = 15.sp))
                }
            }
        }
    }

    companion object {
        suspend fun updateWidgetContent(context: Context) {
            if (!DatabaseProvider.isInitialized()) {
                DatabaseProvider.init(context)
            }

            val dao = DatabaseProvider.dao()
            val extracts = dao.getLatestExtractsOnce(3)

            val items = extracts.map { WidgetExtractItem(title = it.title, content = it.content, emoji = it.emoji) }
            val updateTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            val widgetData = WidgetExtractData(items = items, updateTime = updateTime)
            val json = jsonParser.encodeToString(WidgetExtractData.serializer(), widgetData)

            val manager = GlanceAppWidgetManager(context)
            val glanceIds = manager.getGlanceIds(PinMeWidget::class.java)
            if (glanceIds.isEmpty()) return

            val validGlanceIds = glanceIds.filter { id ->
                try {
                    val appWidgetId = manager.getAppWidgetId(id)
                    val appWidgetManager = AppWidgetManager.getInstance(context)
                    appWidgetManager.getAppWidgetInfo(appWidgetId) != null
                } catch (_: Exception) {
                    false
                }
            }
            if (validGlanceIds.isEmpty()) return

            validGlanceIds.forEach { id ->
                try {
                    updateAppWidgetState(context, PreferencesGlanceStateDefinition, id) { _: Preferences ->
                        mutablePreferencesOf().apply {
                            this[KEY_EXTRACTS_JSON] = json
                            this[KEY_UPDATE_TIME] = updateTime
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "updateAppWidgetState failed", e)
                }
            }

            try {
                PinMeWidget().updateAll(context)
            } catch (e: Exception) {
                Log.e(TAG, "updateAll failed", e)
            }
        }
    }
}

class PinMeWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PinMeWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                PinMeWidget.updateWidgetContent(context)
            } catch (e: Exception) {
                Log.e(TAG, "initial update failed", e)
            }
        }
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                PinMeWidget.updateWidgetContent(context)
            } catch (e: Exception) {
                Log.e(TAG, "onUpdate failed", e)
            }
        }
    }
}

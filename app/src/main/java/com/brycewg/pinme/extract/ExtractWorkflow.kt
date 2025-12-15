package com.brycewg.pinme.extract

import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import com.brycewg.pinme.Constants
import com.brycewg.pinme.Constants.LlmProvider
import com.brycewg.pinme.db.DatabaseProvider
import com.brycewg.pinme.db.ExtractEntity
import com.brycewg.pinme.db.MarketItemEntity
import com.brycewg.pinme.vllm.VllmClient
import com.brycewg.pinme.vllm.getLlmScopedPreferenceWithLegacyFallback
import java.io.ByteArrayOutputStream

class ExtractWorkflow(
    private val context: Context,
    private val vllmClient: VllmClient = VllmClient()
) {
    suspend fun processScreenshot(bitmap: Bitmap): ExtractEntity {
        if (!DatabaseProvider.isInitialized()) {
            DatabaseProvider.init(context)
        }
        val dao = DatabaseProvider.dao()

        // 读取供应商配置
        val provider = LlmProvider.fromStoredValue(dao.getPreference(Constants.PREF_LLM_PROVIDER))

        // 根据供应商确定 baseUrl
        val baseUrl = when (provider) {
            LlmProvider.CUSTOM -> dao.getLlmScopedPreferenceWithLegacyFallback(
                Constants.PREF_LLM_CUSTOM_BASE_URL,
                provider
            )
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("自定义模式下必须设置 Base URL")
            else -> provider.baseUrl
        }

        val apiKey = dao.getLlmScopedPreferenceWithLegacyFallback(Constants.PREF_LLM_API_KEY, provider)
            ?.takeIf { it.isNotBlank() }
        val model = dao.getLlmScopedPreferenceWithLegacyFallback(Constants.PREF_LLM_MODEL, provider)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: provider.defaultModel.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("必须设置模型 ID")
        val temperature = dao.getLlmScopedPreferenceWithLegacyFallback(Constants.PREF_LLM_TEMPERATURE, provider)
            ?.toDoubleOrNull()
            ?: 0.1
        // 读取启用的市场类型
        val marketItems = dao.getEnabledMarketItems()

        val systemPrompt = dao.getLlmScopedPreferenceWithLegacyFallback(
            Constants.PREF_LLM_SYSTEM_PROMPT,
            provider
        )
            ?.takeIf { it.isNotBlank() }
            ?: buildSystemPrompt(marketItems)

        val imageBase64 = bitmap.toPngBase64()
        val userPrompt = buildUserPrompt(marketItems)

        val modelOutput = vllmClient.chatCompletionWithImage(
            baseUrl = baseUrl,
            apiKey = apiKey,
            model = model,
            systemPrompt = systemPrompt,
            userPrompt = userPrompt,
            imagePngBase64 = imageBase64,
            temperature = temperature
        )

        val parsed = ExtractParsing.parseModelOutput(modelOutput)
        val entity = ExtractEntity(
            title = parsed.title,
            content = parsed.content,
            source = "screen",
            rawModelOutput = modelOutput,
            createdAtMillis = System.currentTimeMillis()
        )

        val id = dao.insertExtract(entity)
        return entity.copy(id = id)
    }

    private fun Bitmap.toPngBase64(): String {
        val stream = ByteArrayOutputStream()
        compress(Bitmap.CompressFormat.PNG, 100, stream)
        val bytes = stream.toByteArray()
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    private fun buildUserPrompt(marketItems: List<MarketItemEntity>): String {
        return if (marketItems.isNotEmpty()) {
            "从截图中提取最重要的、适合固定展示的关键信息。优先匹配用户定义的类型。"
        } else {
            "从截图中提取最重要的、适合固定展示的关键信息。"
        }
    }

    private fun buildSystemPrompt(marketItems: List<MarketItemEntity>): String {
        val customTypesSection = if (marketItems.isNotEmpty()) {
            val typesList = marketItems.joinToString("\n") { item ->
                "- ${item.title}：提取${item.contentDesc}"
            }
            """

## 用户自定义类型（优先匹配）
$typesList
"""
        } else {
            ""
        }

        val customExamplesSection = if (marketItems.isNotEmpty()) {
            val examples = marketItems.take(3).joinToString("\n") { item ->
                """{"title":"${item.title}","content":"示例${item.contentDesc}"}"""
            }
            """
$examples"""
        } else {
            ""
        }

        return """
你是手机截图信息提取助手。从截图中识别用户最可能需要反复查看或复制的关键信息。
$customTypesSection
## 常见场景
- 取餐码/排队号：提取号码
- 验证码/动态密码：提取数字或字母组合
- 火车票/机票：提取车次+座位 或 航班+登机口
- 快递/外卖：提取取件码
- 付款码/收款码：提取金额
- 会议/预约：提取时间+地点
- Wi-Fi：提取密码

## 输出格式
仅输出 JSON，不要其他内容：
{"title":"类型简称","content":"关键信息"}

示例：$customExamplesSection
{"title":"取餐码","content":"A128"}
{"title":"座位","content":"G1234 07车 12F"}
{"title":"验证码","content":"847291"}

## 规则
1. title 控制在 2-5 字，优先使用用户自定义类型的标题
2. content 只保留最核心的可复制内容
3. 若截图无明确关键信息，返回 {"title":"识别结果","content":"截图主要内容概述"}
        """.trimIndent()
    }
}

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
        val systemPrompt = buildSystemPrompt(marketItems)

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

        // 清理超出限制的旧记录
        val maxCount = dao.getPreference(Constants.PREF_MAX_HISTORY_COUNT)
            ?.toIntOrNull()
            ?.coerceIn(1, 20)
            ?: Constants.DEFAULT_MAX_HISTORY_COUNT
        dao.trimExtractsToLimit(maxCount)

        return entity.copy(id = id)
    }

    private fun Bitmap.toPngBase64(): String {
        val stream = ByteArrayOutputStream()
        compress(Bitmap.CompressFormat.PNG, 100, stream)
        val bytes = stream.toByteArray()
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    private fun buildUserPrompt(marketItems: List<MarketItemEntity>): String {
        return "从截图中提取最重要的、适合固定展示的关键信息。严格按照已定义的类型进行匹配。"
    }

    private fun buildSystemPrompt(marketItems: List<MarketItemEntity>): String {
        // 分离无匹配类型和其他类型
        val normalTypes = marketItems.filter { it.presetKey != "no_match" }
        val noMatchType = marketItems.find { it.presetKey == "no_match" }

        val typesSection = if (normalTypes.isNotEmpty()) {
            val typesList = normalTypes.joinToString("\n") { item ->
                "- **${item.title}**：${item.contentDesc}"
            }
            """

## 可识别的类型
$typesList
"""
        } else {
            ""
        }

        val examplesSection = if (normalTypes.isNotEmpty()) {
            val examples = normalTypes.take(4).joinToString("\n") { item ->
                val sampleContent = when (item.presetKey) {
                    "pickup_code" -> "5-8-2-1"
                    "meal_code" -> "A128"
                    "train_ticket" -> "G1234 07车 12F 检票口B2"
                    "verification_code" -> "847291"
                    else -> "示例${item.contentDesc}"
                }
                """{"title":"${item.title}","content":"$sampleContent"}"""
            }
            """
$examples"""
        } else {
            ""
        }

        // 无匹配类型的处理说明
        val noMatchSection = if (noMatchType != null) {
            """

## 无匹配情况
当截图内容不属于上述任何类型时，使用「${noMatchType.title}」类型：
- 提取截图中最关键、最有价值的信息摘要
- content 应简明扼要，突出重点（如页面标题、核心数字、关键状态）
- 若截图为纯装饰性内容或无实质信息，content 填写"无有效信息"

示例：
{"title":"${noMatchType.title}","content":"微信支付成功 ¥128.00"}
{"title":"${noMatchType.title}","content":"航班CA1234 准点"}
{"title":"${noMatchType.title}","content":"无有效信息"}"""
        } else {
            """

## 无匹配情况
若截图无明确关键信息，返回：
{"title":"识别结果","content":"截图主要内容概述"}"""
        }

        return """
你是手机截图信息提取助手。从截图中识别用户最可能需要反复查看或复制的关键信息。
$typesSection
## 输出格式
仅输出 JSON，不要其他内容：
{"title":"类型简称","content":"关键信息"}

示例：$examplesSection

## 规则
1. **title 必须严格使用已定义类型的标题**，不要自创标题
2. content 只保留最核心的可复制内容，去除无关修饰
3. 优先匹配最具体的类型（如"取件码"优于"无匹配"）
4. 验证码类识别需精确，数字/字母不可遗漏或错误
$noMatchSection
        """.trimIndent()
    }
}

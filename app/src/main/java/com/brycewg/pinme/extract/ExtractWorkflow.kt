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
    private data class ParsedModelOutput(
        val parsed: ExtractParsed,
        val rawOutput: String
    )

    suspend fun processScreenshot(bitmap: Bitmap, sourcePackage: String? = null): ExtractEntity {
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
        // 读取自定义系统指令
        val customInstruction = dao.getPreference(Constants.PREF_CUSTOM_SYSTEM_INSTRUCTION)
            ?.takeIf { it.isNotBlank() }
            ?: Constants.DEFAULT_SYSTEM_INSTRUCTION
        val systemPrompt = buildSystemPrompt(marketItems, customInstruction)

        val imageBase64 = bitmap.toCompressedBase64()
        val userPrompt = buildUserPrompt(marketItems)

        val parseResult = parseModelOutputWithRetry {
            vllmClient.chatCompletionWithImage(
                baseUrl = baseUrl,
                apiKey = apiKey,
                model = model,
                systemPrompt = systemPrompt,
                userPrompt = userPrompt,
                imageBase64 = imageBase64,
                temperature = temperature
            )
        }
        val parsed = parseResult.parsed
        val entity = ExtractEntity(
            title = parsed.title,
            content = parsed.content,
            emoji = parsed.emoji,
            source = "screen",
            sourcePackage = sourcePackage,
            rawModelOutput = parseResult.rawOutput,
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

    /**
     * 将 Bitmap 压缩为 JPEG 格式的 Base64 字符串
     * - 按比例缩放到最大宽度以减少传输数据量
     * - 使用 JPEG 有损压缩大幅减小文件体积（相比 PNG 可减少 80-90%）
     */
    private fun Bitmap.toCompressedBase64(): String {
        val scaledBitmap = scaleToMaxWidth(Constants.SCREENSHOT_MAX_WIDTH)
        val stream = ByteArrayOutputStream()
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
        if (scaledBitmap !== this) {
            scaledBitmap.recycle()
        }
        return Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
    }

    /**
     * 按比例缩放 Bitmap 到指定最大宽度
     * 如果原图宽度小于等于目标宽度，返回原图
     */
    private fun Bitmap.scaleToMaxWidth(maxWidth: Int): Bitmap {
        if (width <= maxWidth) return this
        val scale = maxWidth.toFloat() / width
        val newHeight = (height * scale).toInt()
        return Bitmap.createScaledBitmap(this, maxWidth, newHeight, true)
    }

    /**
     * 从纯文本中提取关键信息（不保存到数据库）
     * 用于手动添加记录时的智能提取功能
     */
    suspend fun extractFromText(text: String): ExtractParsed {
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
        // 读取自定义系统指令
        val customInstruction = dao.getPreference(Constants.PREF_CUSTOM_SYSTEM_INSTRUCTION)
            ?.takeIf { it.isNotBlank() }
            ?: Constants.DEFAULT_SYSTEM_INSTRUCTION
        val systemPrompt = buildTextSystemPrompt(marketItems, customInstruction)

        val parseResult = parseModelOutputWithRetry {
            vllmClient.chatCompletion(
                baseUrl = baseUrl,
                apiKey = apiKey,
                model = model,
                systemPrompt = systemPrompt,
                userPrompt = text,
                temperature = temperature
            )
        }

        return parseResult.parsed
    }

    private suspend fun parseModelOutputWithRetry(fetchOutput: suspend () -> String): ParsedModelOutput {
        val firstAttempt = runCatching { fetchOutput() }
        val firstOutput = firstAttempt.getOrNull()
        if (firstOutput != null) {
            val firstParse = ExtractParsing.parseModelOutputWithStatus(firstOutput)
            if (firstParse.parsedFromJson) {
                return ParsedModelOutput(parsed = firstParse.parsed, rawOutput = firstOutput)
            }
        }

        val secondAttempt = runCatching { fetchOutput() }
        val secondOutput = secondAttempt.getOrNull()
        if (secondOutput != null) {
            val secondParse = ExtractParsing.parseModelOutputWithStatus(secondOutput)
            if (secondParse.parsedFromJson) {
                return ParsedModelOutput(parsed = secondParse.parsed, rawOutput = secondOutput)
            }
        }

        val outputs = listOf(firstOutput, secondOutput).filterNotNull()
        if (outputs.isNotEmpty()) {
            val combinedOutput = outputs
                .filter { it.isNotBlank() }
                .joinToString("\n\n----- retry -----\n\n")
            return ParsedModelOutput(
                parsed = buildParseErrorParsed(),
                rawOutput = combinedOutput
            )
        }

        val combinedErrors = listOf(firstAttempt.exceptionOrNull(), secondAttempt.exceptionOrNull())
            .filterNotNull()
            .joinToString("\n\n----- retry -----\n\n") { formatThrowable(it) }
        return ParsedModelOutput(
            parsed = buildModelErrorParsed(),
            rawOutput = combinedErrors.ifBlank { "unknown error" }
        )
    }

    private fun buildParseErrorParsed(): ExtractParsed {
        return ExtractParsed(
            title = Constants.PARSE_ERROR_TITLE,
            content = Constants.PARSE_ERROR_CONTENT,
            emoji = Constants.PARSE_ERROR_EMOJI
        )
    }

    private fun buildModelErrorParsed(): ExtractParsed {
        return ExtractParsed(
            title = Constants.MODEL_ERROR_TITLE,
            content = Constants.MODEL_ERROR_CONTENT,
            emoji = Constants.MODEL_ERROR_EMOJI
        )
    }

    private fun formatThrowable(error: Throwable): String {
        val message = error.message?.trim().orEmpty()
        return if (message.isNotBlank()) message else error::class.java.simpleName
    }

    private fun splitExampleBlocks(raw: String): List<String> {
        val normalized = raw.replace("\r\n", "\n").replace("\r", "\n").trim()
        if (normalized.isBlank()) return emptyList()
        return normalized
            .split("\n")
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }

    private fun escapeJsonString(value: String): String {
        if (value.isEmpty()) return ""
        return buildString(value.length) {
            value.forEach { ch ->
                when (ch) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> Unit
                    '\t' -> append("\\t")
                    else -> append(ch)
                }
            }
        }
    }

    private fun buildExampleLines(items: List<MarketItemEntity>): List<String> {
        return items.flatMap { item ->
            val examples = splitExampleBlocks(item.outputExample)
            if (examples.isEmpty()) {
                emptyList()
            } else {
                val title = escapeJsonString(item.title.trim().ifBlank { "识别结果" })
                val emoji = escapeJsonString(item.emoji.trim().ifBlank { "??" })
                examples.map { example ->
                    val content = escapeJsonString(example)
                    """{"title":"$title","content":"$content","emoji":"$emoji"}"""
                }
            }
        }
    }

    private fun buildUserPrompt(marketItems: List<MarketItemEntity>): String {
        return "从截图中提取最重要的、适合固定展示的关键信息。严格按照已定义的类型进行匹配。"
    }

    private fun buildTextSystemPrompt(marketItems: List<MarketItemEntity>, customInstruction: String): String {
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

        val exampleLines = buildExampleLines(normalTypes)
        val examplesSection = if (exampleLines.isNotEmpty()) {
            exampleLines.joinToString("\n")
        } else {
            ""
        }
        val examplesBlock = if (examplesSection.isNotBlank()) {
            "\n示例：\n$examplesSection"
        } else {
            ""
        }

        // 无匹配类型的处理说明
        val noMatchExampleLines = noMatchType?.let { buildExampleLines(listOf(it)) }.orEmpty()
        val noMatchExamplesSection = if (noMatchType != null && noMatchExampleLines.isNotEmpty()) {
            "示例：\n" + noMatchExampleLines.joinToString("\n")
        } else if (noMatchType != null) {
            "示例：\n{\"title\":\"${noMatchType.title}\",\"content\":\"微信支付成功 ￥128.00\",\"emoji\":\"?\"}"
        } else {
            ""
        }
        val noMatchSection = if (noMatchType != null) {
            """

## 无匹配情况
当文本内容不属于上述任何类型时，使用「${noMatchType.title}」类型：
- 提取文本中最关键、最有价值的信息摘要
- content 应简明扼要，突出重点

$noMatchExamplesSection"""
        } else {
            """

## 无匹配情况
若文本无明确关键信息，返回例如：
{"title":"识别结果","content":"文本主要内容概述","emoji":"??"}"""
        }

        return """
$customInstruction
$typesSection
## 输出格式
仅输出 JSON，不要其他内容：
{"title":"类型简称","content":"关键信息","emoji":"单个emoji"}$examplesBlock

## 规则
1. **title 必须严格使用已定义类型的标题**，不要自创标题
2. content 只保留最核心的可复制内容，去除无关修饰
3. 优先匹配最具体的类型
4. 验证码类识别需精确，数字/字母不可遗漏或错误
5. emoji 根据内容场景选择合适的图标
6. **content 字数不超过 30 字**，超长时精简核心内容
$noMatchSection
        """.trimIndent()
    }

    private fun buildSystemPrompt(marketItems: List<MarketItemEntity>, customInstruction: String): String {
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

        val exampleLines = buildExampleLines(normalTypes)
        val examplesSection = if (exampleLines.isNotEmpty()) {
            exampleLines.joinToString("\n")
        } else {
            ""
        }
        val examplesBlock = if (examplesSection.isNotBlank()) {
            "\n示例：\n$examplesSection"
        } else {
            ""
        }

        // 无匹配类型的处理说明
        val noMatchExampleLines = noMatchType?.let { buildExampleLines(listOf(it)) }.orEmpty()
        val noMatchExamplesSection = if (noMatchType != null && noMatchExampleLines.isNotEmpty()) {
            "示例：\n" + noMatchExampleLines.joinToString("\n")
        } else if (noMatchType != null) {
            "示例：\n{\"title\":\"${noMatchType.title}\",\"content\":\"微信支付成功 ￥128.00\",\"emoji\":\"?\"}\n" +
                "{\"title\":\"${noMatchType.title}\",\"content\":\"航班CA1234 准点\",\"emoji\":\"??\"}\n" +
                "{\"title\":\"${noMatchType.title}\",\"content\":\"无有效信息\",\"emoji\":\"?\"}"
        } else {
            ""
        }
        val noMatchSection = if (noMatchType != null) {
            """

## 无匹配情况
当截图内容不属于上述任何类型时，使用「${noMatchType.title}」类型：
- 提取截图中最关键、最有价值的信息摘要
- content 应简明扼要，突出重点（如页面标题、核心数字、关键状态）
- 若截图为纯装饰性内容或无实质信息，content 填写"无有效信息"

$noMatchExamplesSection"""
        } else {
            """

## 无匹配情况
若截图无明确关键信息，返回例如：
{"title":"识别结果","content":"截图主要内容概述","emoji":"??"}"""
        }

        return """
$customInstruction
$typesSection
## 输出格式
仅输出 JSON，不要其他内容：
{"title":"类型简称","content":"关键信息","emoji":"单个emoji"}$examplesBlock

## 规则
1. **title 必须严格使用已定义类型的标题**，不要自创标题
2. content 只保留最核心的可复制内容，去除无关修饰
3. 优先匹配最具体的类型（如"取件码"优于"无匹配"）
4. 验证码类识别需精确，数字/字母不可遗漏或错误
5. **emoji 必须根据截图中的品牌/商品/场景选择**，而非类型：
   - 咖啡店（瑞幸、星巴克）→ ? | 奶茶店 → ?? | 汉堡店 → ?? | 面馆 → ?? | 炸鸡店 → ??
   - 高铁 → ?? | 飞机 → ?? | 电影票 → ?? | 演出票 → ??
   - 书籍快递 → ?? | 服装快递 → ?? | 通用快递 → ??
6. **content 字数不超过 30 字**，超长时精简核心内容
7. 无需识别二维码本身（系统自动检测），专注于文本信息
$noMatchSection
        """.trimIndent()
    }
}


